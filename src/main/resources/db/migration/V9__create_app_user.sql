-- 사용자 계정 테이블 생성
CREATE TABLE app_user (
    id         VARCHAR(50) PRIMARY KEY,
    pw         VARCHAR(255) NOT NULL,
    name       VARCHAR(50) NOT NULL,
    role       VARCHAR(20) NOT NULL,
    photo      VARCHAR(255),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL
);

-- 기본 사용자 정보 시드 데이터 삽입
INSERT INTO app_user (id, pw, name, role, photo) VALUES
('admin', '1234', '김다온 원장', 'admin', '/images/Daon_Kim.png'),
('staff1', '1234', '이수진 상담사', 'staff', '/images/Minji_Kang.png'),
('staff2', '1234', '박준혁 강사', 'staff', '/images/Taehyun_Kim.png');
