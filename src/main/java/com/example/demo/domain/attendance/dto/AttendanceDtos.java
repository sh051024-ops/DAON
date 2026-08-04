package com.example.demo.domain.attendance.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public class AttendanceDtos {

    // 오늘 출결 요약 정보 DTO
    public record TodaySummary(
            LocalDate date,
            double attendanceRate,   // 오늘 출석률 %
            long present,            // 출석 인원
            long late,               // 지각 인원
            long earlyLeave,         // 조퇴 인원
            long absent              // 결석 인원
    ) {}

    // 출결 그리드의 한 행 정보 DTO
    public record GridRow(
            Long studentId,
            String name,
            String gender,
            String className,
            Map<String, String> statuses,  // "yyyy-MM-dd" -> 상태
            double weekRate,               // 주간 출석률 %
            int absentStreak               // 연속 결석 일수
    ) {}

    // 출결 그리드 응답 DTO
    public record Grid(
            List<LocalDate> dates,
            List<GridRow> rows
    ) {}

    // 연속 결석 경고 정보 DTO
    public record StreakWarning(
            Long studentId,
            String name,
            String gender,
            String className,
            int streak
    ) {}

    // 반별 출석률 DTO
    public record ClassRate(
            String className,
            double rate
    ) {}

    // 주간 출석 상태 분포 DTO
    public record StatusDistribution(
            long present,
            long late,
            long earlyLeave,
            long absent,
            double rate
    ) {}
}
