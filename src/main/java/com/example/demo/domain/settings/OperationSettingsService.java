package com.example.demo.domain.settings;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 운영 기준 설정 조회/저장. 판정 로직을 가진 서비스들이 이걸 통해 현재 기준값을 읽는다.
 */
@Service
@Transactional(readOnly = true)
public class OperationSettingsService {

    private final OperationSettingsRepository repository;

    public OperationSettingsService(OperationSettingsRepository repository) {
        this.repository = repository;
    }

    /** 현재 설정. 행이 없으면 기존 코드에 하드코딩되어 있던 기본값으로 동작한다. */
    public OperationSettings current() {
        return repository.findById(1L).orElseGet(OperationSettings::defaults);
    }

    @Transactional
    public OperationSettings save(OperationSettings incoming) {
        OperationSettings s = repository.findById(1L).orElseGet(OperationSettings::defaults);
        s.setRiskThreshold(incoming.getRiskThreshold());
        s.setWarnThreshold(incoming.getWarnThreshold());
        s.setWatchThreshold(incoming.getWatchThreshold());
        s.setAbsentStreakDays(incoming.getAbsentStreakDays());
        s.setAbsentStreakLimit(incoming.getAbsentStreakLimit());
        s.setLeadDoneThreshold(incoming.getLeadDoneThreshold());
        s.setLeadProgressThreshold(incoming.getLeadProgressThreshold());
        s.setLeadScheduledThreshold(incoming.getLeadScheduledThreshold());
        s.setLeadNewThreshold(incoming.getLeadNewThreshold());
        return repository.save(s);
    }
}
