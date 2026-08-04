package com.example.demo.domain.settings;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 운영 기준 설정. 항상 id=1 하나만 존재한다.
 * 여기 값들이 학생 상태 판정 / 연속 결석 경고 / 리드 자동 분류에 실제로 쓰인다.
 */
@Entity
@Table(name = "operation_settings")
@Getter
@Setter
@NoArgsConstructor
public class OperationSettings {

    /** 코드에 하드코딩되어 있던 기본값 — DB에 행이 없을 때의 대비용으로도 쓴다. */
    public static OperationSettings defaults() {
        OperationSettings s = new OperationSettings();
        s.setId(1L);
        s.setRiskThreshold(60);
        s.setWarnThreshold(70);
        s.setWatchThreshold(80);
        s.setAbsentStreakDays(2);
        s.setAbsentStreakLimit(5);
        s.setLeadDoneThreshold(90);
        s.setLeadProgressThreshold(80);
        s.setLeadScheduledThreshold(70);
        s.setLeadNewThreshold(60);
        return s;
    }

    @Id
    private Long id;

    @Column(name = "risk_threshold", nullable = false)
    private Integer riskThreshold;

    @Column(name = "warn_threshold", nullable = false)
    private Integer warnThreshold;

    @Column(name = "watch_threshold", nullable = false)
    private Integer watchThreshold;

    @Column(name = "absent_streak_days", nullable = false)
    private Integer absentStreakDays;

    @Column(name = "absent_streak_limit", nullable = false)
    private Integer absentStreakLimit;

    @Column(name = "lead_done_threshold", nullable = false)
    private Integer leadDoneThreshold;

    @Column(name = "lead_progress_threshold", nullable = false)
    private Integer leadProgressThreshold;

    @Column(name = "lead_scheduled_threshold", nullable = false)
    private Integer leadScheduledThreshold;

    @Column(name = "lead_new_threshold", nullable = false)
    private Integer leadNewThreshold;
}
