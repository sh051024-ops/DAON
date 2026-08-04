-- 학원 기본 정보 (설정 페이지). 항상 단일 행(id=1)만 사용한다.
CREATE TABLE academy_settings (
    id               BIGINT PRIMARY KEY,
    academy_name     VARCHAR(60)  NOT NULL,
    phone            VARCHAR(20),
    address          VARCHAR(200),
    business_reg_no  VARCHAR(20),
    ceo_name         VARCHAR(20),
    business_type    VARCHAR(30)
);
