package com.example.demo.domain.student;

import com.example.demo.domain.student.dto.StudentDtos.PageResult;
import com.example.demo.domain.student.dto.StudentDtos.RiskStudent;
import com.example.demo.domain.student.dto.StudentDtos.StudentRow;
import com.example.demo.domain.student.dto.StudentDtos.Summary;
import com.example.demo.domain.crm.LeadMeta;
import com.example.demo.domain.crm.LeadMetaRepository;
import com.example.demo.domain.crm.CounselingLog;
import com.example.demo.domain.crm.CounselingLogRepository;
import com.example.demo.domain.settings.OperationSettings;
import com.example.demo.domain.settings.OperationSettingsService;
import com.example.demo.domain.student.dto.StudentDtos.StudentRegisterRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class StudentService {

    // 데모 기준일 (더미 데이터의 오늘)
    public static final LocalDate DEMO_TODAY = LocalDate.of(2026, 7, 14);

    private final StudentRepository studentRepository;
    private final LeadMetaRepository leadMetaRepository;
    private final CounselingLogRepository counselingLogRepository;
    private final OperationSettingsService operationSettingsService;

    public StudentService(StudentRepository studentRepository,
                          LeadMetaRepository leadMetaRepository,
                          CounselingLogRepository counselingLogRepository,
                          OperationSettingsService operationSettingsService) {
        this.studentRepository = studentRepository;
        this.leadMetaRepository = leadMetaRepository;
        this.counselingLogRepository = counselingLogRepository;
        this.operationSettingsService = operationSettingsService;
    }

    /**
     * 표시용 상태값. 판정 기준(출석률 몇 % 미만이면 위험/주의/관심)은
     * 설정 > 운영 기준에서 바꿀 수 있다.
     */
    public static String displayStatus(Student s, OperationSettings cfg) {
        if (!"재원".equals(s.getStatus())) {
            return s.getStatus();
        }
        int rate = s.getAttendanceRate() == null ? 100 : s.getAttendanceRate();
        if (rate < cfg.getRiskThreshold()) return "위험";
        if (rate < cfg.getWarnThreshold()) return "주의";
        if (rate < cfg.getWatchThreshold()) return "관심";
        return "수강중";
    }

    // 학생 정보 필터 및 페이징 조회
    public PageResult<StudentRow> getStudents(String category, String className,
                                              String status, String query,
                                              int page, int size) {
        List<Student> all = studentRepository.findAll();
        OperationSettings cfg = operationSettingsService.current();

        List<StudentRow> filtered = all.stream()
                .filter(s -> category == null || category.isBlank() || s.getCourseCategory().equals(category))
                .filter(s -> className == null || className.isBlank() || s.getClassName().equals(className))
                .filter(s -> {
                    if (status == null || status.isBlank()) return true;
                    return displayStatus(s, cfg).equals(status);
                })
                .filter(s -> {
                    if (query == null || query.isBlank()) return true;
                    String q = query.trim();
                    return s.getName().contains(q)
                            || (s.getPhone() != null && s.getPhone().contains(q))
                            || s.getClassName().contains(q);
                })
                .sorted(Comparator.comparing(s -> s.getId()))
                .map(s -> toRow(s, cfg))
                .toList();

        int total = filtered.size();
        int from = Math.min(page * size, total);
        int to = Math.min(from + size, total);

        return new PageResult<>(filtered.subList(from, to), page, size,
                total, (int) Math.ceil((double) total / size));
    }

    // 요약 지표 카드 데이터
    public Summary getSummary() {
        List<Student> all = studentRepository.findAll();
        OperationSettings cfg = operationSettingsService.current();
        long total = all.size();
        long enrolled = all.stream().filter(s -> "재원".equals(s.getStatus())).count();
        long expected = all.stream()
                .filter(s -> "재원".equals(s.getStatus()))
                .filter(s -> s.getClassEnd() != null
                        && !s.getClassEnd().isBefore(DEMO_TODAY)
                        && s.getClassEnd().isBefore(DEMO_TODAY.plusDays(30)))
                .count();
        long risk = all.stream()
                .filter(s -> "재원".equals(s.getStatus()))
                .filter(s -> "위험".equals(displayStatus(s, cfg)))
                .count();
        return new Summary(total, enrolled, expected, risk);
    }

    // 위험 학생 TOP 5
    public List<RiskStudent> getRiskTop5() {
        return studentRepository.findAll().stream()
                .filter(s -> "재원".equals(s.getStatus()))
                .sorted(Comparator.comparing(s -> s.getAttendanceRate() == null ? 100 : s.getAttendanceRate()))
                .limit(5)
                .map(s -> new RiskStudent(s.getId(), s.getName(), s.getGender(), s.getClassName(), s.getAttendanceRate()))
                .toList();
    }

    // 필터용 반 목록
    public List<String> getClassNames() {
        return studentRepository.findAll().stream()
            .map(s -> s.getClassName())
            .distinct()
            .sorted()
            .toList();
    }

    private static StudentRow toRow(Student s, OperationSettings cfg) {
        return new StudentRow(
                s.getId(), s.getName(), s.getGender(), s.getPhone(),
                s.getCourseCategory(), s.getClassName(),
                s.getAttendanceRate(), s.getAssignmentRate(),
                displayStatus(s, cfg), s.getInstructor(), s.getLastConsultedAt());
    }

    // 신규 학생 등록 및 CRM 메타 연동
    @Transactional
    public StudentRow registerStudent(StudentRegisterRequest req) {
        Student s = new Student();
        s.setName(req.name());
        s.setAge(req.age() != null ? req.age() : 0);
        s.setGender(req.gender() != null ? req.gender() : "남");
        s.setPhone(req.phone());
        s.setEmail(req.email());
        s.setRegion(req.region());
        s.setEducation(req.education());
        s.setCourseCategory(req.courseCategory());
        s.setClassName(req.className());
        s.setStatus("재원");
        s.setAttendanceRate(100); // 초기 값 100%
        s.setAssignmentRate(100);
        s.setCounselor(req.counselor() != null && !req.counselor().isBlank() ? req.counselor() : "김상담");
        s.setInstructor(req.instructor());
        s.setEnrolledAt(DEMO_TODAY);
        s.setClassStart(DEMO_TODAY);
        s.setClassEnd(DEMO_TODAY.plusMonths(6)); // 임시 6개월 과정

        s = studentRepository.save(s);

        // CRM 메타 연동
        LeadMeta meta = new LeadMeta(s.getId(), "신규", "");
        leadMetaRepository.save(meta);

        // 상담 이력 연동
        String logDate = DEMO_TODAY.toString().replace("-", ".");
        counselingLogRepository.save(new CounselingLog(
                s.getId(), logDate, "시스템", "신규 학생 등록 완료", "시스템을 통해 학생 정보가 정상 등록되었습니다."
        ));

        return toRow(s, operationSettingsService.current());
    }

    // CSV 파일 생성
    public void writeStudentsCsv(String category, String className, String status, String query, java.io.Writer writer) throws java.io.IOException {
        List<Student> all = studentRepository.findAll();
        OperationSettings cfg = operationSettingsService.current();

        List<Student> filtered = all.stream()
                .filter(s -> category == null || category.isBlank() || s.getCourseCategory().equals(category))
                .filter(s -> className == null || className.isBlank() || s.getClassName().equals(className))
                .filter(s -> {
                    if (status == null || status.isBlank()) return true;
                    return displayStatus(s, cfg).equals(status);
                })
                .filter(s -> {
                    if (query == null || query.isBlank()) return true;
                    String q = query.trim();
                    return s.getName().contains(q)
                            || (s.getPhone() != null && s.getPhone().contains(q))
                            || s.getClassName().contains(q);
                })
                .sorted(Comparator.comparing(s -> s.getId()))
                .toList();

        // Write UTF-8 BOM
        writer.write('\uFEFF');
        writer.write("ID,이름,연락처,과정,반,출석률,과제수행률,상태,담당강사,최근상담일\n");
        for (Student s : filtered) {
            writer.write(String.format("%d,%s,%s,%s,%s,%d%%,%d%%,%s,%s,%s\n",
                    s.getId(),
                    escapeCsv(s.getName()),
                    escapeCsv(s.getPhone() != null ? s.getPhone() : ""),
                    escapeCsv(s.getCourseCategory()),
                    escapeCsv(s.getClassName()),
                    s.getAttendanceRate() != null ? s.getAttendanceRate() : 0,
                    s.getAssignmentRate() != null ? s.getAssignmentRate() : 0,
                    escapeCsv(displayStatus(s, cfg)),
                    escapeCsv(s.getInstructor() != null ? s.getInstructor() : ""),
                    s.getLastConsultedAt() != null ? s.getLastConsultedAt().toString() : ""
            ));
        }
    }

    private String escapeCsv(String val) {
        if (val == null) return "";
        if (val.contains(",") || val.contains("\"") || val.contains("\n") || val.contains("\r")) {
            return "\"" + val.replace("\"", "\"\"") + "\"";
        }
        return val;
    }
}
