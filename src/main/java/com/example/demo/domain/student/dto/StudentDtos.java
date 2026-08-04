package com.example.demo.domain.student.dto;

import java.time.LocalDate;
import java.util.List;

/** 학생 관리 페이지 응답 DTO 모음 (record) */
public class StudentDtos {

    /** 목록 테이블 한 행 */
    public record StudentRow(
            Long id,
            String name,
            String gender,
            String phone,
            String courseCategory,
            String className,
            Integer attendanceRate,
            Integer assignmentRate,
            String displayStatus,     // 수강중 | 관심 | 주의 | 위험 | 수료
            String instructor,
            LocalDate lastConsultedAt
    ) {}

    /** 페이징 응답 */
    public record PageResult<T>(
            List<T> content,
            int page,
            int size,
            long totalElements,
            int totalPages
    ) {}

    /** 상단 요약 카드 */
    public record Summary(
            long totalStudents,       // 전체 학생
            long enrolled,            // 수강 중(재원)
            long expectedToComplete,  // 수료 예정(30일 내 종강)
            long atRisk               // 위험 학생
    ) {}

    /** 위험 학생 TOP 5 항목 */
    public record RiskStudent(
            Long id,
            String name,
            String gender,
            String className,
            Integer attendanceRate
    ) {}

    /** 신규 학생 등록 요청 */
    public record StudentRegisterRequest(
            String name,
            Integer age,
            String gender,
            String phone,
            String email,
            String region,
            String education,
            String courseCategory,
            String className,
            String majorType,
            String workerType,
            String channel,
            String govFunded,
            String counselor,
            String instructor
    ) {}
}
