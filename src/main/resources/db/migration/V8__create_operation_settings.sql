-- 운영 기준 설정 (설정 > 운영 기준 탭). 항상 단일 행(id=1)만 사용한다.
-- 지금까지 코드에 흩어져 있던 판정 기준 숫자들을 여기로 모아, 학원마다 다르게 운영할 수 있게 한다.
CREATE TABLE operation_settings (
    id                      BIGINT PRIMARY KEY,
    -- 학생 상태 판정 (출석률 기준). risk < warn < watch 순서를 지켜야 한다.
    risk_threshold          INT NOT NULL,   -- 이 값 미만이면 '위험'
    warn_threshold          INT NOT NULL,   -- 이 값 미만이면 '주의'
    watch_threshold         INT NOT NULL,   -- 이 값 미만이면 '관심', 이상이면 '수강중'
    -- 연속 결석 경고
    absent_streak_days      INT NOT NULL,   -- 며칠 연속 결석부터 경고할지
    absent_streak_limit     INT NOT NULL,   -- 경고 목록에 몇 명까지 보여줄지
    -- 리드 자동 분류 (출석률 기준). done > progress > scheduled > new 순서를 지켜야 한다.
    lead_done_threshold     INT NOT NULL,   -- 이상이면 '등록완료'
    lead_progress_threshold INT NOT NULL,   -- 이상이면 '상담진행'
    lead_scheduled_threshold INT NOT NULL,  -- 이상이면 '상담예정'
    lead_new_threshold      INT NOT NULL    -- 이상이면 '신규', 미만이면 '이탈'
);
