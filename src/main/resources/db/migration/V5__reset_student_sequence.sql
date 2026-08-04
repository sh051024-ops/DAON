-- 더미데이터 INSERT 후 IDENTITY 시퀀스 값이 동기화되지 않는 현상을 해결하기 위해
-- 학생(student) 테이블의 auto_increment ID 시작 값을 300으로 상향 조정합니다.
ALTER TABLE student ALTER COLUMN id RESTART WITH 300;
