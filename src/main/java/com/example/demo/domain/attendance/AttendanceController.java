package com.example.demo.domain.attendance;

import com.example.demo.domain.attendance.dto.AttendanceDtos.ClassRate;
import com.example.demo.domain.attendance.dto.AttendanceDtos.Grid;
import com.example.demo.domain.attendance.dto.AttendanceDtos.StatusDistribution;
import com.example.demo.domain.attendance.dto.AttendanceDtos.StreakWarning;
import com.example.demo.domain.attendance.dto.AttendanceDtos.TodaySummary;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/attendance")
public class AttendanceController {

    private final AttendanceService attendanceService;
    private final com.example.demo.service.OpenAiService openAiService;

    public AttendanceController(AttendanceService attendanceService, com.example.demo.service.OpenAiService openAiService) {
        this.attendanceService = attendanceService;
        this.openAiService = openAiService;
    }

    /** 오늘(최신) 요약 카드 */
    @GetMapping("/summary")
    public TodaySummary summary() {
        return attendanceService.getTodaySummary();
    }

    /** 학생별 x 일자별 출결 그리드 */
    @GetMapping("/grid")
    public Grid grid(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end,
            @RequestParam(required = false) String className,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String instructor) {
        return attendanceService.getGrid(start, end, className, category, instructor);
    }

    /** 연속 결석 경고 */
    @GetMapping("/streak-warnings")
    public List<StreakWarning> streakWarnings() {
        return attendanceService.getStreakWarnings();
    }

    /** 반별 출석률 TOP5 */
    @GetMapping("/class-top5")
    public List<ClassRate> classTop5() {
        return attendanceService.getClassTop5();
    }

    /** 이번 주 상태 분포 (도넛) */
    @GetMapping("/weekly")
    public StatusDistribution weekly() {
        return attendanceService.getWeeklyDistribution();
    }

    /** AI 출결 분석 및 조언 */
    @GetMapping("/ai-analysis")
    public java.util.Map<String, String> getAiAnalysis() {
        List<StreakWarning> warnings = attendanceService.getStreakWarnings();
        StringBuilder sb = new StringBuilder();
        sb.append("{\"warnings\":[");
        for (int i = 0; i < warnings.size(); i++) {
            StreakWarning w = warnings.get(i);
            sb.append(String.format("{\"name\":\"%s\",\"className\":\"%s\",\"streak\":%d}", w.name(), w.className(), w.streak()));
            if (i < warnings.size() - 1) {
                sb.append(",");
            }
        }
        sb.append("]}");
        String analysis = openAiService.getAttendanceAnalysis(sb.toString());
        return java.util.Map.of("analysis", analysis);
    }
}
