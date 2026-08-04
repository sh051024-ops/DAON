-- CRM 메타데이터 테이블: 리드 상태/메모
CREATE TABLE lead_meta (
    student_id  BIGINT PRIMARY KEY REFERENCES student(id),
    lead_status VARCHAR(20),
    memo        TEXT,
    updated_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 상담 히스토리 로그 테이블
CREATE TABLE counseling_log (
    id          BIGSERIAL PRIMARY KEY,
    student_id  BIGINT NOT NULL REFERENCES student(id),
    log_date    VARCHAR(20) NOT NULL,
    type        VARCHAR(20) NOT NULL,
    summary     VARCHAR(200),
    memo        TEXT,
    created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_counseling_student ON counseling_log(student_id);
