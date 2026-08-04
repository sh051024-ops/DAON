-- 학생 테이블 (정식 스키마)
CREATE TABLE student (
    id                 BIGSERIAL PRIMARY KEY,
    name               VARCHAR(50)  NOT NULL,
    age                INT          NOT NULL,
    gender             VARCHAR(4)   NOT NULL,
    phone              VARCHAR(20),
    email              VARCHAR(100),
    region             VARCHAR(20),
    education          VARCHAR(20),
    course_category    VARCHAR(30)  NOT NULL,
    class_name         VARCHAR(40)  NOT NULL,
    class_start        DATE,
    class_end          DATE,
    major_type         VARCHAR(10),
    worker_type        VARCHAR(10),
    channel            VARCHAR(20),
    gov_funded         VARCHAR(10),
    enrolled_at        DATE,
    status             VARCHAR(10)  NOT NULL,
    attendance_rate    INT,
    assignment_rate    INT,
    counselor          VARCHAR(20),
    instructor         VARCHAR(20),
    last_consulted_at  DATE,
    employment_status  VARCHAR(10),
    company            VARCHAR(60),
    employed_at        DATE,
    track_until        DATE
);
CREATE INDEX idx_student_class  ON student (class_name);
CREATE INDEX idx_student_status ON student (status);
