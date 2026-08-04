package com.example.demo.domain.attendance;

import com.example.demo.domain.attendance.dto.AttendanceDtos.ClassRate;
import com.example.demo.domain.attendance.dto.AttendanceDtos.Grid;
import com.example.demo.domain.attendance.dto.AttendanceDtos.GridRow;
import com.example.demo.domain.attendance.dto.AttendanceDtos.StatusDistribution;
import com.example.demo.domain.attendance.dto.AttendanceDtos.StreakWarning;
import com.example.demo.domain.attendance.dto.AttendanceDtos.TodaySummary;
import com.example.demo.domain.settings.OperationSettings;
import com.example.demo.domain.settings.OperationSettingsService;
import com.example.demo.domain.student.Student;
import com.example.demo.domain.student.StudentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@Transactional(readOnly = true)
public class AttendanceService {

    private final AttendanceRepository attendanceRepository;
    private final StudentRepository studentRepository;
    private final OperationSettingsService operationSettingsService;

    public AttendanceService(AttendanceRepository attendanceRepository,
                             StudentRepository studentRepository,
                             OperationSettingsService operationSettingsService) {
        this.attendanceRepository = attendanceRepository;
        this.studentRepository = studentRepository;
        this.operationSettingsService = operationSettingsService;
    }

    // 최신 날짜 조회
    public LocalDate latestDate() {
        LocalDate d = attendanceRepository.findLatestDate();
        return d != null ? d : LocalDate.now();
    }

    // 가장 오래된 날짜 조회
    public LocalDate earliestDate() {
        LocalDate d = attendanceRepository.findEarliestDate();
        return d != null ? d : LocalDate.now().minusDays(30);
    }

    // 오늘 출결 요약
    public TodaySummary getTodaySummary() {
        LocalDate today = latestDate();
        List<Attendance> rows = attendanceRepository.findByAttendanceDate(today);
        long present = rows.stream().filter(a -> "출석".equals(a.getStatus())).count();
        long late = rows.stream().filter(a -> "지각".equals(a.getStatus())).count();
        long early = rows.stream().filter(a -> "조퇴".equals(a.getStatus())).count();
        long absent = rows.stream().filter(a -> "결석".equals(a.getStatus())).count();
        double rate = rows.isEmpty() ? 0 : Math.round(present * 1000.0 / rows.size()) / 10.0;
        return new TodaySummary(today, rate, present, late, early, absent);
    }

    // 출결 그리드 조회
    public Grid getGrid(LocalDate start, LocalDate end, String className, String category, String instructor) {
        LocalDate today = latestDate();
        if (end == null) end = today;
        if (start == null) start = earliestDate();

        List<LocalDate> dates = new ArrayList<>();
        for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) dates.add(d);

        final String cn = className;
        final String cat = category;
        final String inst = instructor;
        List<Student> students = studentRepository.findAll().stream()
                .filter(s -> "재원".equals(s.getStatus()))
                .filter(s -> cn == null || cn.isBlank() || s.getClassName().equals(cn))
                .filter(s -> cat == null || cat.isBlank() || s.getCourseCategory().equals(cat))
                .filter(s -> inst == null || inst.isBlank() || s.getInstructor() != null && s.getInstructor().equals(inst))
                .sorted(Comparator.comparing(s -> s.getId()))
                .toList();

        Map<Long, Map<LocalDate, String>> byStudent = new HashMap<>();
        for (Attendance a : attendanceRepository.findByAttendanceDateBetween(start, end)) {
            byStudent.computeIfAbsent(a.getStudentId(), k -> new HashMap<>())
                    .put(a.getAttendanceDate(), a.getStatus());
        }

        Map<Long, Map<LocalDate, String>> recent = new HashMap<>();
        for (Attendance a : attendanceRepository.findByAttendanceDateBetween(today.minusDays(27), today)) {
            recent.computeIfAbsent(a.getStudentId(), k -> new HashMap<>())
                    .put(a.getAttendanceDate(), a.getStatus());
        }

        List<GridRow> rows = new ArrayList<>();
        for (Student s : students) {
            Map<LocalDate, String> mine = byStudent.getOrDefault(s.getId(), Map.of());
            Map<String, String> statuses = new LinkedHashMap<>();
            int attended = 0, counted = 0;
            for (LocalDate d : dates) {
                String st;
                if (d.getDayOfWeek() == DayOfWeek.SATURDAY || d.getDayOfWeek() == DayOfWeek.SUNDAY) {
                    st = "휴";
                } else {
                    st = mine.get(d);
                    if (st == null) st = "-";
                    else {
                        counted++;
                        if ("출석".equals(st)) attended++;
                    }
                }
                statuses.put(d.toString(), st);
            }
            double weekRate = counted == 0 ? 0 : Math.round(attended * 1000.0 / counted) / 10.0;
            rows.add(new GridRow(s.getId(), s.getName(), s.getGender(), s.getClassName(),
                    statuses, weekRate, absentStreak(recent.getOrDefault(s.getId(), Map.of()), today)));
        }
        return new Grid(dates, rows);
    }

    /** 연속 결석 경고 목록. 몇 일 연속부터 경고할지·몇 명까지 볼지는 설정 > 운영 기준에서 정한다. */
    public List<StreakWarning> getStreakWarnings() {
        LocalDate today = latestDate();
        OperationSettings cfg = operationSettingsService.current();
        Map<Long, Map<LocalDate, String>> recent = new HashMap<>();
        for (Attendance a : attendanceRepository.findByAttendanceDateBetween(today.minusDays(27), today)) {
            recent.computeIfAbsent(a.getStudentId(), k -> new HashMap<>())
                    .put(a.getAttendanceDate(), a.getStatus());
        }
        return studentRepository.findAll().stream()
                .filter(s -> "재원".equals(s.getStatus()))
                .map(s -> new StreakWarning(s.getId(), s.getName(), s.getGender(), s.getClassName(),
                        absentStreak(recent.getOrDefault(s.getId(), Map.of()), today)))
                .filter(w -> w.streak() >= cfg.getAbsentStreakDays())
                .sorted(Comparator.comparing((StreakWarning w) -> w.streak()).reversed())
                .limit(cfg.getAbsentStreakLimit())
                .toList();
    }

    // 반별 출석률 TOP 5
    public List<ClassRate> getClassTop5() {
        LocalDate today = latestDate();
        Map<Long, String> classOf = new HashMap<>();
        studentRepository.findAll().forEach(s -> classOf.put(s.getId(), s.getClassName()));

        Map<String, long[]> agg = new HashMap<>();
        for (Attendance a : attendanceRepository.findByAttendanceDateBetween(today.minusDays(6), today)) {
            String cls = classOf.get(a.getStudentId());
            if (cls == null) continue;
            long[] v = agg.computeIfAbsent(cls, k -> new long[2]);
            v[1]++;
            if ("출석".equals(a.getStatus())) v[0]++;
        }
        return agg.entrySet().stream()
                .map(e -> new ClassRate(e.getKey(),
                        Math.round(e.getValue()[0] * 1000.0 / e.getValue()[1]) / 10.0))
                .sorted(Comparator.comparing((ClassRate cr) -> cr.rate()).reversed())
                .limit(5)
                .toList();
    }

    // 이번 주 상태 분포
    public StatusDistribution getWeeklyDistribution() {
        LocalDate today = latestDate();
        List<Attendance> rows = attendanceRepository.findByAttendanceDateBetween(today.minusDays(6), today);
        long present = rows.stream().filter(a -> "출석".equals(a.getStatus())).count();
        long late = rows.stream().filter(a -> "지각".equals(a.getStatus())).count();
        long early = rows.stream().filter(a -> "조퇴".equals(a.getStatus())).count();
        long absent = rows.stream().filter(a -> "결석".equals(a.getStatus())).count();
        double rate = rows.isEmpty() ? 0 : Math.round(present * 1000.0 / rows.size()) / 10.0;
        return new StatusDistribution(present, late, early, absent, rate);
    }

    // 연속 결석 일수 계산
    private static int absentStreak(Map<LocalDate, String> records, LocalDate today) {
        int streak = 0;
        LocalDate d = today;
        while (true) {
            if (d.getDayOfWeek() == DayOfWeek.SATURDAY || d.getDayOfWeek() == DayOfWeek.SUNDAY) {
                d = d.minusDays(1);
                continue;
            }
            String st = records.get(d);
            if ("결석".equals(st)) {
                streak++;
                d = d.minusDays(1);
            } else {
                break;
            }
        }
        return streak;
    }
}
