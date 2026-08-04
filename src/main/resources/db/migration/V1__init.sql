CREATE TABLE pipeline_stage (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(50) NOT NULL,
    order_no    INT NOT NULL,
    stage_type  VARCHAR(20) NOT NULL,
    preset_key  VARCHAR(50),
    is_active   BOOLEAN NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMP NOT NULL DEFAULT NOW()
);
