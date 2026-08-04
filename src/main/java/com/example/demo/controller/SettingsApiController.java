package com.example.demo.controller;

import com.example.demo.domain.settings.AcademySettings;
import com.example.demo.domain.settings.AcademySettingsRepository;
import com.example.demo.domain.settings.OperationSettings;
import com.example.demo.domain.settings.OperationSettingsService;
import com.example.demo.domain.settings.dto.SettingsDtos.AcademySettingsDto;
import com.example.demo.domain.settings.dto.SettingsDtos.AiStatus;
import com.example.demo.domain.settings.dto.SettingsDtos.AiTestResult;
import com.example.demo.domain.settings.dto.SettingsDtos.OperationSettingsDto;
import com.example.demo.domain.settings.dto.SettingsDtos.SaveResult;
import com.example.demo.service.OpenAiService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/settings")
public class SettingsApiController {

    private final AcademySettingsRepository academySettingsRepository;
    private final OpenAiService openAiService;
    private final OperationSettingsService operationSettingsService;

    public SettingsApiController(AcademySettingsRepository academySettingsRepository,
                                 OpenAiService openAiService,
                                 OperationSettingsService operationSettingsService) {
        this.academySettingsRepository = academySettingsRepository;
        this.openAiService = openAiService;
        this.operationSettingsService = operationSettingsService;
    }

    @GetMapping("/academy")
    public AcademySettingsDto getAcademySettings() {
        AcademySettings s = academySettingsRepository.findById(1L)
                .orElseGet(() -> {
                    AcademySettings fresh = new AcademySettings();
                    fresh.setId(1L);
                    fresh.setAcademyName("DAON EDU");
                    return fresh;
                });
        return toDto(s);
    }

    @PutMapping("/academy")
    public AcademySettingsDto updateAcademySettings(@RequestBody AcademySettingsDto req) {
        AcademySettings s = academySettingsRepository.findById(1L).orElseGet(() -> {
            AcademySettings fresh = new AcademySettings();
            fresh.setId(1L);
            return fresh;
        });
        s.setAcademyName(req.academyName());
        s.setPhone(req.phone());
        s.setAddress(req.address());
        s.setBusinessRegNo(req.businessRegNo());
        s.setCeoName(req.ceoName());
        s.setBusinessType(req.businessType());
        s = academySettingsRepository.save(s);
        return toDto(s);
    }

    @GetMapping("/ai-status")
    public AiStatus getAiStatus() {
        return new AiStatus(openAiService.isConfigured(), openAiService.getModel());
    }

    @PostMapping("/ai-test")
    public AiTestResult testAi() {
        try {
            String message = openAiService.testConnection();
            return new AiTestResult(true, message);
        } catch (Exception e) {
            return new AiTestResult(false, e.getMessage());
        }
    }

    // ──────── 운영 기준 ────────

    @GetMapping("/operation")
    public OperationSettingsDto getOperationSettings() {
        return toDto(operationSettingsService.current());
    }

    @PutMapping("/operation")
    public SaveResult updateOperationSettings(@RequestBody OperationSettingsDto req) {
        String error = validate(req);
        if (error != null) {
            return new SaveResult(false, error);
        }
        OperationSettings incoming = new OperationSettings();
        incoming.setRiskThreshold(req.riskThreshold());
        incoming.setWarnThreshold(req.warnThreshold());
        incoming.setWatchThreshold(req.watchThreshold());
        incoming.setAbsentStreakDays(req.absentStreakDays());
        incoming.setAbsentStreakLimit(req.absentStreakLimit());
        incoming.setLeadDoneThreshold(req.leadDoneThreshold());
        incoming.setLeadProgressThreshold(req.leadProgressThreshold());
        incoming.setLeadScheduledThreshold(req.leadScheduledThreshold());
        incoming.setLeadNewThreshold(req.leadNewThreshold());
        operationSettingsService.save(incoming);
        return new SaveResult(true, "운영 기준이 저장되었습니다.");
    }

    /** 저장 전 값 검증. 순서가 뒤집히면 판정 로직이 이상해지므로 미리 막는다. */
    private String validate(OperationSettingsDto r) {
        Integer[] all = {
                r.riskThreshold(), r.warnThreshold(), r.watchThreshold(),
                r.absentStreakDays(), r.absentStreakLimit(),
                r.leadDoneThreshold(), r.leadProgressThreshold(),
                r.leadScheduledThreshold(), r.leadNewThreshold()
        };
        for (Integer v : all) {
            if (v == null) return "빈 값이 있습니다.";
        }
        Integer[] rates = {
                r.riskThreshold(), r.warnThreshold(), r.watchThreshold(),
                r.leadDoneThreshold(), r.leadProgressThreshold(),
                r.leadScheduledThreshold(), r.leadNewThreshold()
        };
        for (Integer v : rates) {
            if (v < 0 || v > 100) return "출석률 기준은 0~100 사이여야 합니다.";
        }
        if (!(r.riskThreshold() < r.warnThreshold() && r.warnThreshold() < r.watchThreshold())) {
            return "학생 상태 기준은 위험 < 주의 < 관심 순서여야 합니다.";
        }
        if (!(r.leadNewThreshold() < r.leadScheduledThreshold()
                && r.leadScheduledThreshold() < r.leadProgressThreshold()
                && r.leadProgressThreshold() < r.leadDoneThreshold())) {
            return "리드 분류 기준은 신규 < 상담예정 < 상담진행 < 등록완료 순서여야 합니다.";
        }
        if (r.absentStreakDays() < 1) return "연속 결석 경고 일수는 1일 이상이어야 합니다.";
        if (r.absentStreakLimit() < 1) return "경고 표시 인원은 1명 이상이어야 합니다.";
        return null;
    }

    private AcademySettingsDto toDto(AcademySettings s) {
        return new AcademySettingsDto(
                s.getAcademyName(), s.getPhone(), s.getAddress(),
                s.getBusinessRegNo(), s.getCeoName(), s.getBusinessType());
    }

    private OperationSettingsDto toDto(OperationSettings s) {
        return new OperationSettingsDto(
                s.getRiskThreshold(), s.getWarnThreshold(), s.getWatchThreshold(),
                s.getAbsentStreakDays(), s.getAbsentStreakLimit(),
                s.getLeadDoneThreshold(), s.getLeadProgressThreshold(),
                s.getLeadScheduledThreshold(), s.getLeadNewThreshold());
    }
}
