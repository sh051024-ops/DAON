-- 상담 로그 시드: 각 리드에 초기 상담 기록 1건씩 자동 생성
INSERT INTO counseling_log (student_id, log_date, type, summary, memo)
SELECT s.id,
  COALESCE(CAST(s.enrolled_at AS VARCHAR), '2026.07.22'),
  CASE lm.lead_status
    WHEN '등록완료' THEN '대면'
    WHEN '상담진행' THEN '대면'
    WHEN '상담예정' THEN '카톡'
    WHEN '이탈'     THEN '전화'
    ELSE '시스템'
  END,
  CASE lm.lead_status
    WHEN '등록완료' THEN '최종 과정 등록 결정 및 수강 서약서 작성'
    WHEN '상담진행' THEN '원내 방문 및 과정 상세 설계 상담'
    WHEN '상담예정' THEN '카카오톡 상담 채널을 통한 첫 문의 접수'
    WHEN '이탈'     THEN '수강 의사 재확인 및 이탈 분류'
    ELSE '신규 리드 정보 유입'
  END,
  CASE lm.lead_status
    WHEN '등록완료' THEN '수강생이 커리큘럼과 취업 연계 혜택에 크게 만족하여 본 등록을 완료함.'
    WHEN '상담진행' THEN '학원에 방문하여 학습 로드맵과 국비지원 신청 절차를 안내받음.'
    WHEN '상담예정' THEN '과정 개강일과 야간반 개설 여부를 문의함.'
    WHEN '이탈'     THEN '개인 사정으로 이번 기수 수강은 어렵다는 의사를 확인하여 이탈로 분류함.'
    ELSE '홈페이지/광고를 통해 관심 과정 문의가 신규로 접수되었습니다.'
  END
FROM student s
JOIN lead_meta lm ON s.id = lm.student_id;
