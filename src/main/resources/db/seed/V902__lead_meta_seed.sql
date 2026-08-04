-- 리드 메타데이터 시드: 출석률 기반 자동 분류
-- 수료생 제외, 재원생만 리드 메타 생성
INSERT INTO lead_meta (student_id, lead_status, memo) 
SELECT id, 
  CASE 
    WHEN attendance_rate >= 90 THEN '등록완료'
    WHEN attendance_rate >= 80 THEN '상담진행'
    WHEN attendance_rate >= 70 THEN '상담예정'
    WHEN attendance_rate >= 60 THEN '신규'
    ELSE '이탈'
  END,
  ''
FROM student 
WHERE status <> '수료';
