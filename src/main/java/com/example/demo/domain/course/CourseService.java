package com.example.demo.domain.course;

import com.example.demo.domain.course.dto.CourseDtos.CohortRow;
import com.example.demo.domain.course.dto.CourseDtos.CourseGroup;
import com.example.demo.domain.course.dto.CourseDtos.NearFull;
import com.example.demo.domain.course.dto.CourseDtos.PerfPoint;
import com.example.demo.domain.course.dto.CourseDtos.ScheduleEvent;
import com.example.demo.domain.student.Student;
import com.example.demo.domain.student.StudentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@Transactional(readOnly = true)
public class CourseService {

    /** 데모 기준일 (더미의 '오늘') */
    public static final LocalDate DEMO_TODAY = LocalDate.of(2026, 7, 14);

    private final CourseRepository courseRepository;
    private final StudentRepository studentRepository;

    public CourseService(CourseRepository courseRepository, StudentRepository studentRepository) {
        this.courseRepository = courseRepository;
        this.studentRepository = studentRepository;
    }

    /** class_name(=cohort_name) 별 재원/수료 인원 집계 */
    private Map<String, int[]> countByCohort() {
        Map<String, int[]> map = new LinkedHashMap<>(); // [재원, 수료]
        for (Student s : studentRepository.findAll()) {
            int[] v = map.computeIfAbsent(s.getClassName(), k -> new int[2]);
            if ("재원".equals(s.getStatus())) v[0]++;
            else if ("수료".equals(s.getStatus())) v[1]++;
        }
        return map;
    }

    private CohortRow toRow(Course c, Map<String, int[]> counts) {
        int[] v = counts.getOrDefault(c.getCohortName(), new int[2]);
        int current = "종료".equals(c.getStatus()) ? v[1] : v[0];
        int fill = c.getCapacity() == 0 ? 0 : Math.round(current * 100f / c.getCapacity());
        boolean nearFull = !"종료".equals(c.getStatus()) && fill >= 80;
        boolean endingSoon = "운영중".equals(c.getStatus()) && c.getEndDate() != null
                && !c.getEndDate().isBefore(DEMO_TODAY)
                && c.getEndDate().isBefore(DEMO_TODAY.plusDays(30));
        return new CohortRow(c.getId(), c.getCategory(), c.getCohortName(), c.getCapacity(),
                current, fill, c.getStartDate(), c.getEndDate(), c.getStatus(),
                c.getInstructor(), c.getCompletionRate(), nearFull, endingSoon);
    }

    /** 과정별 그룹 목록 (트리) */
    public List<CourseGroup> getGroupedCourses() {
        Map<String, int[]> counts = countByCohort();
        Map<String, List<CohortRow>> byCategory = new LinkedHashMap<>();
        for (Course c : courseRepository.findAllByOrderByStartDateAsc()) {
            byCategory.computeIfAbsent(c.getCategory(), k -> new ArrayList<>()).add(toRow(c, counts));
        }
        List<CourseGroup> result = new ArrayList<>();
        for (var e : byCategory.entrySet()) {
            int avg = (int) Math.round(e.getValue().stream().mapToInt(CohortRow::fillRate).average().orElse(0));
            result.add(new CourseGroup(e.getKey(), avg, e.getValue()));
        }
        return result;
    }

    /** 기수별 성과 비교 — 개강일 순 */
    public List<PerfPoint> getPerformance() {
        Map<String, int[]> counts = countByCohort();
        return courseRepository.findAllByOrderByStartDateAsc().stream()
                .map(c -> toRow(c, counts))
                .map(r -> new PerfPoint(r.cohortName(), r.fillRate(), r.completionRate()))
                .toList();
    }

    /** 정원 마감 임박 (종료 제외, 충원율 높은 순) */
    public List<NearFull> getNearFull() {
        Map<String, int[]> counts = countByCohort();
        return courseRepository.findAll().stream()
                .filter(c -> !"종료".equals(c.getStatus()))
                .map(c -> toRow(c, counts))
                .sorted(Comparator.comparingInt(CohortRow::fillRate).reversed())
                .limit(4)
                .map(r -> new NearFull(r.cohortName(), r.current(), r.capacity(), r.fillRate()))
                .toList();
    }

    /** 개강·종강 일정 이벤트 */
    public List<ScheduleEvent> getSchedule() {
        List<ScheduleEvent> events = new ArrayList<>();
        for (Course c : courseRepository.findAll()) {
            if (c.getStartDate() != null) events.add(new ScheduleEvent(c.getStartDate(), "개강", c.getCohortName()));
            if (c.getEndDate() != null) events.add(new ScheduleEvent(c.getEndDate(), "종강", c.getCohortName()));
        }
        events.sort(Comparator.comparing(ScheduleEvent::date));
        return events;
    }
}
