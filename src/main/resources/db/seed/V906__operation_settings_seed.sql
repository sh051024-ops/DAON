-- 기존 코드에 하드코딩되어 있던 값을 그대로 초기값으로 넣는다(동작이 달라지지 않도록).
INSERT INTO operation_settings (
    id,
    risk_threshold, warn_threshold, watch_threshold,
    absent_streak_days, absent_streak_limit,
    lead_done_threshold, lead_progress_threshold, lead_scheduled_threshold, lead_new_threshold
) VALUES (
    1,
    60, 70, 80,
    2, 5,
    90, 80, 70, 60
);
