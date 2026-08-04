package com.example.demo.domain.course.dto;

import java.time.LocalDate;
import java.util.List;

/** 과정·기수 관리 페이지 응답 DTO 모음 (record) */
public class CourseDtos {

    /** 기수 한 행 */
    public record CohortRow(
            Long id,
            String category,
            String cohortName,
            int capacity,
            int current,        // 현재 인원
            int fillRate,       // 충원율 %
            LocalDate startDate,
            LocalDate endDate,
            String status,      // 운영중 | 모집중 | 종료
            String instructor,
            Integer completionRate,
            boolean nearFull,   // 정원 임박(충원율>=80, 종료 제외)
            boolean endingSoon  // 종료 예정(종강 30일 내)
    ) {}

    /** 과정별 그룹 (기수 목록 트리) */
    public record CourseGroup(
            String category,
            int avgFillRate,
            List<CohortRow> cohorts
    ) {}

    /** 기수별 성과 비교 (라인 차트) */
    public record PerfPoint(
            String label,           // 기수명
            int fillRate,           // 충원율
            Integer completionRate  // 수료율(종료 기수만)
    ) {}

    /** 정원 마감 임박 */
    public record NearFull(
            String cohortName,
            int current,
            int capacity,
            int fillRate
    ) {}

    /** 개강/종강 일정 */
    public record ScheduleEvent(
            LocalDate date,
            String type,        // 개강 | 종강
            String cohortName
    ) {}
}
