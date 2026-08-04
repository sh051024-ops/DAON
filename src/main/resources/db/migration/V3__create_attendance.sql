-- 출결 테이블: 학생 x 날짜 x 상태
CREATE TABLE attendance (
    id               BIGSERIAL PRIMARY KEY,
    student_id       BIGINT NOT NULL REFERENCES student(id),
    attendance_date  DATE   NOT NULL,
    status           VARCHAR(10) NOT NULL,   -- 출석|지각|조퇴|결석|휴강
    CONSTRAINT uq_attendance UNIQUE (student_id, attendance_date)
);
CREATE INDEX idx_attendance_date    ON attendance (attendance_date);
CREATE INDEX idx_attendance_student ON attendance (student_id);
