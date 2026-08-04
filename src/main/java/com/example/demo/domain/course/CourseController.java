package com.example.demo.domain.course;

import com.example.demo.domain.course.dto.CourseDtos.CourseGroup;
import com.example.demo.domain.course.dto.CourseDtos.NearFull;
import com.example.demo.domain.course.dto.CourseDtos.PerfPoint;
import com.example.demo.domain.course.dto.CourseDtos.ScheduleEvent;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/courses")
public class CourseController {

    private final CourseService courseService;

    public CourseController(CourseService courseService) {
        this.courseService = courseService;
    }

    /** 과정별 기수 목록 (트리) */
    @GetMapping
    public List<CourseGroup> list() {
        return courseService.getGroupedCourses();
    }

    /** 기수별 성과 비교 (라인 차트) */
    @GetMapping("/performance")
    public List<PerfPoint> performance() {
        return courseService.getPerformance();
    }

    /** 정원 마감 임박 */
    @GetMapping("/near-full")
    public List<NearFull> nearFull() {
        return courseService.getNearFull();
    }

    /** 개강·종강 일정 */
    @GetMapping("/schedule")
    public List<ScheduleEvent> schedule() {
        return courseService.getSchedule();
    }
}
