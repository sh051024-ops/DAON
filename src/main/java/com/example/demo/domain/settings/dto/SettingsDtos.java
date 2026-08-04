package com.example.demo.domain.settings.dto;

public class SettingsDtos {

    /** 학원 기본 정보 조회/저장 */
    public record AcademySettingsDto(
            String academyName,
            String phone,
            String address,
            String businessRegNo,
            String ceoName,
            String businessType
    ) {}

    /** AI(OpenAI) 연동 상태 */
    public record AiStatus(
            boolean configured,   // 실제 API 키가 설정되어 있는지
            String model
    ) {}

    /** AI 연동 테스트 결과 */
    public record AiTestResult(
            boolean success,
            String message
    ) {}

    /** 운영 기준 조회/저장 */
    public record OperationSettingsDto(
            Integer riskThreshold,
            Integer warnThreshold,
            Integer watchThreshold,
            Integer absentStreakDays,
            Integer absentStreakLimit,
            Integer leadDoneThreshold,
            Integer leadProgressThreshold,
            Integer leadScheduledThreshold,
            Integer leadNewThreshold
    ) {}

    /** 운영 기준 저장 결과 (검증 실패 시 이유를 알려준다) */
    public record SaveResult(
            boolean success,
            String message
    ) {}
}
