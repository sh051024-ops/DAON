-- 과정·기수(코호트) 테이블
-- cohort_name 은 student.class_name 과 매칭되어 현재인원/충원율을 계산한다.
CREATE TABLE course (
    id               BIGSERIAL PRIMARY KEY,
    category         VARCHAR(30) NOT NULL,   -- 과정: IT | 회계 | 기계 | 그래픽·멀티미디어
    cohort_name      VARCHAR(40) NOT NULL,   -- 기수: IT-1반, 회계-1반, ...
    capacity         INT NOT NULL,           -- 정원
    status           VARCHAR(10) NOT NULL,   -- 운영중 | 모집중 | 종료
    instructor       VARCHAR(20),            -- 담당 강사
    start_date       DATE,                   -- 개강일
    end_date         DATE,                   -- 종강일
    completion_rate  INT                     -- 수료율(%) — 종료 기수만, 진행중은 NULL
);
CREATE INDEX idx_course_status   ON course (status);
CREATE INDEX idx_course_category ON course (category);
