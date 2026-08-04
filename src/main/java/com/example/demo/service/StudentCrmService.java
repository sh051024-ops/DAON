package com.example.demo.service;

import com.example.demo.domain.crm.CounselingLog;
import com.example.demo.domain.crm.CounselingLogRepository;
import com.example.demo.domain.crm.LeadMeta;
import com.example.demo.domain.crm.LeadMetaRepository;
import com.example.demo.domain.crm.CompletedTask;
import com.example.demo.domain.crm.CompletedTaskRepository;
import com.example.demo.domain.student.StudentRepository;
import com.example.demo.dto.StudentDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * CRM 서비스 — 기존 CSV 파일 기반 로직을 DB(JPA) 기반으로 전환.
 * DTO 충돌 해소 및 ObjectMapper 싱글톤 빈 사용, 동시성/트랜잭션 정합성 보완.
 */
@Service
@Transactional(readOnly = true)
public class StudentCrmService {
    private static final Logger log = LoggerFactory.getLogger(StudentCrmService.class);

    private final StudentRepository studentRepository;
    private final LeadMetaRepository leadMetaRepository;
    private final CounselingLogRepository counselingLogRepository;
    private final com.example.demo.domain.settings.OperationSettingsService operationSettingsService;
    private final CompletedTaskRepository completedTaskRepository;
    private final ObjectMapper objectMapper;

    public StudentCrmService(StudentRepository studentRepository,
                             LeadMetaRepository leadMetaRepository,
                             CounselingLogRepository counselingLogRepository,
                             com.example.demo.domain.settings.OperationSettingsService operationSettingsService,
                             CompletedTaskRepository completedTaskRepository,
                             ObjectMapper objectMapper) {
        this.studentRepository = studentRepository;
        this.leadMetaRepository = leadMetaRepository;
        this.counselingLogRepository = counselingLogRepository;
        this.operationSettingsService = operationSettingsService;
        this.completedTaskRepository = completedTaskRepository;
        this.objectMapper = objectMapper;
    }

    // ──────── 완료된 업무 관리 (대시보드 추천 업무 체크) ────────
    public Set<String> getCompletedTasks() {
        return completedTaskRepository.findAll().stream()
                .map(CompletedTask::getTask)
                .collect(Collectors.toSet());
    }

    @Transactional
    public void completeTask(String task) {
        if (task != null && !task.isBlank()) {
            completedTaskRepository.save(new CompletedTask(task));
        }
    }

    // ──────── 학생(리드) 목록 로드: DB 조회 ────────
    public List<StudentDto> loadStudents() {
        List<com.example.demo.domain.student.Student> entities = studentRepository.findAll();
        return entities.stream().map(this::toDto).collect(Collectors.toList());
    }

    /** DB 엔티티 → 기존 DTO 변환 */
    private StudentDto toDto(com.example.demo.domain.student.Student e) {
        StudentDto s = new StudentDto();
        s.setId(String.valueOf(e.getId()));
        s.setName(e.getName());
        s.setAge(e.getAge() != null ? e.getAge() : 0);
        s.setGender(e.getGender());
        s.setPhone(e.getPhone());
        s.setEmail(e.getEmail());
        s.setRegion(e.getRegion());
        s.setEducation(e.getEducation());
        s.setCourseCategory(e.getCourseCategory());
        s.setClassName(e.getClassName());
        s.setClassStart(e.getClassStart() != null ? e.getClassStart().toString() : "");
        s.setClassEnd(e.getClassEnd() != null ? e.getClassEnd().toString() : "");
        s.setMajorType(e.getMajorType());
        s.setWorkerType(e.getWorkerType());
        s.setChannel(e.getChannel());
        s.setGovFunded(e.getGovFunded());
        s.setEnrolledAt(e.getEnrolledAt() != null ? e.getEnrolledAt().toString() : "");
        s.setStatus(e.getStatus());
        s.setAttendanceRate(e.getAttendanceRate() != null ? e.getAttendanceRate() : 0);
        s.setCounselor(e.getCounselor());
        s.setEmploymentStatus(e.getEmploymentStatus());
        s.setCompany(e.getCompany());
        s.setEmployedAt(e.getEmployedAt() != null ? e.getEmployedAt().toString() : "");
        s.setTrackUntil(e.getTrackUntil() != null ? e.getTrackUntil().toString() : "");
        return s;
    }

    // ──────── 리드 메모 Getter ────────
    public Map<String, String> getLeadMemos() {
        Map<String, String> memos = new HashMap<>();
        leadMetaRepository.findAll().forEach(lm -> {
            if (lm.getMemo() != null && !lm.getMemo().isEmpty()) {
                memos.put(String.valueOf(lm.getStudentId()), lm.getMemo());
            }
        });
        return memos;
    }

    // ──────── AI 대시보드용 위험 학생 요약 ────────
    public String getAtRiskStudentsSummary() {
        List<StudentDto> students = loadStudents();
        if (students.isEmpty()) return "{}";

        double avgAttendance = students.stream().mapToInt(StudentDto::getAttendanceRate).average().orElse(0.0);
        List<StudentDto> atRisk = students.stream()
                .filter(s -> s.getAttendanceRate() < 70 && "재원".equals(s.getStatus()))
                .collect(Collectors.toList());

        List<StudentDto> limited = atRisk.stream()
                .sorted(Comparator.comparingInt(StudentDto::getAttendanceRate))
                .limit(15).collect(Collectors.toList());

        List<Map<String, Object>> riskList = limited.stream().map(s -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("name", s.getName());
            item.put("className", s.getClassName());
            item.put("attendanceRate", s.getAttendanceRate());
            item.put("counselor", s.getCounselor() != null ? s.getCounselor() : "");
            return item;
        }).collect(Collectors.toList());

        Map<String, Object> map = new LinkedHashMap<>();
        map.put("totalStudents", students.size());
        map.put("averageAttendanceRate", Double.valueOf(String.format(Locale.US, "%.1f", avgAttendance)));
        map.put("atRiskStudentsCount", atRisk.size());
        map.put("atRiskStudentsList", riskList);

        try {
            return objectMapper.writeValueAsString(map);
        } catch (Exception e) {
            log.error("Failed to serialize at risk students summary", e);
            return "{}";
        }
    }

    // ──────── 기간 필터링 ────────
    public List<StudentDto> filterStudentsByPeriod(List<StudentDto> students, String period) {
        if (period == null || period.trim().isEmpty() || "전체 기간".equals(period)) return students;

        LocalDate now = LocalDate.now();
        String startOfThisMonth = now.withDayOfMonth(1).toString();
        String startOfLastMonth = now.minusMonths(1).withDayOfMonth(1).toString();
        String startOf3MonthsAgo = now.minusMonths(2).withDayOfMonth(1).toString();

        if ("이번 달".equals(period)) {
            return students.stream().filter(s -> s.getEnrolledAt() != null && s.getEnrolledAt().compareTo(startOfThisMonth) >= 0).collect(Collectors.toList());
        } else if ("지난 달".equals(period)) {
            return students.stream().filter(s -> s.getEnrolledAt() != null && s.getEnrolledAt().compareTo(startOfLastMonth) >= 0 && s.getEnrolledAt().compareTo(startOfThisMonth) < 0).collect(Collectors.toList());
        } else if ("최근 3개월".equals(period)) {
            return students.stream().filter(s -> s.getEnrolledAt() != null && s.getEnrolledAt().compareTo(startOf3MonthsAgo) >= 0).collect(Collectors.toList());
        }
        return students;
    }

    // ──────── 위험 학생 목록 (프론트) ────────
    public List<Map<String, Object>> getAtRiskStudentsForFrontend(String period) {
        List<StudentDto> students = filterStudentsByPeriod(loadStudents(), period);
        return students.stream()
                .filter(s -> s.getAttendanceRate() < 85 && "재원".equals(s.getStatus()))
                .sorted(Comparator.comparingInt(StudentDto::getAttendanceRate))
                .limit(8)
                .map(s -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("name", s.getName());
                    map.put("className", s.getClassName());
                    map.put("courseCategory", s.getCourseCategory());
                    map.put("attendanceRate", s.getAttendanceRate());
                    String riskLevel, riskBadgeClass;
                    if (s.getAttendanceRate() < 60) { riskLevel = "고위험"; riskBadgeClass = "badge-red"; }
                    else if (s.getAttendanceRate() < 80) { riskLevel = "주의"; riskBadgeClass = "badge-amber"; }
                    else { riskLevel = "관심"; riskBadgeClass = "badge-gray"; }
                    map.put("riskLevel", riskLevel);
                    map.put("riskBadgeClass", riskBadgeClass);
                    map.put("gender", s.getGender());
                    return map;
                }).collect(Collectors.toList());
    }

    public List<Map<String, Object>> getAtRiskStudentsForFrontend() {
        return getAtRiskStudentsForFrontend("전체 기간");
    }

    // ──────── 통계 ────────
    public Map<String, Object> getStats(String period) {
        List<StudentDto> students = filterStudentsByPeriod(loadStudents(), period);
        Map<String, Object> stats = new HashMap<>();
        long totalStudents = students.size();
        stats.put("totalStudents", totalStudents);

        double avgAttendance = students.stream().mapToInt(StudentDto::getAttendanceRate).average().orElse(0.0);
        stats.put("averageAttendance", Double.valueOf(String.format(Locale.US, "%.1f", avgAttendance)));

        double latePercentage = totalStudents > 0 ? 5.0 : 0.0;
        double absentPercentage = totalStudents > 0 ? (100.0 - latePercentage - avgAttendance) : 0.0;
        if (absentPercentage < 0) { absentPercentage = 0; latePercentage = 100.0 - avgAttendance; }

        long attendanceCount = Math.round(totalStudents * (avgAttendance / 100.0));
        long lateCount = Math.round(totalStudents * (latePercentage / 100.0));
        long absentCount = totalStudents - attendanceCount - lateCount;
        if (absentCount < 0) absentCount = 0;

        stats.put("attendanceCount", attendanceCount);
        stats.put("lateCount", lateCount);
        stats.put("absentCount", absentCount);
        stats.put("attendancePercentage", Double.valueOf(String.format(Locale.US, "%.1f", avgAttendance)));
        stats.put("latePercentage", Double.valueOf(String.format(Locale.US, "%.1f", latePercentage)));
        stats.put("absentPercentage", Double.valueOf(String.format(Locale.US, "%.1f", absentPercentage)));
        return stats;
    }

    public Map<String, Object> getStats() { return getStats("전체 기간"); }

    // ──────── 오늘의 상담 일정 ────────
    public List<Map<String, Object>> getTodayCounselings(String period) {
        List<StudentDto> students = filterStudentsByPeriod(loadStudents(), period);
        List<StudentDto> candidates = students.stream()
                .filter(s -> "재원".equals(s.getStatus()) && s.getAttendanceRate() < 80).limit(4).collect(Collectors.toList());
        if (candidates.size() < 4) {
            List<StudentDto> additional = students.stream()
                    .filter(s -> "재원".equals(s.getStatus()) && s.getAttendanceRate() >= 80)
                    .filter(s -> !candidates.contains(s)).limit(4 - candidates.size()).collect(Collectors.toList());
            candidates.addAll(additional);
        }

        List<Map<String, Object>> result = new ArrayList<>();
        String[] times = {"10:00", "11:00", "14:00", "16:00"};
        String[] types = {"방문", "전화", "방문", "전화"};
        String[] badges = {"badge-blue", "badge-green", "badge-blue", "badge-green"};

        for (int i = 0; i < candidates.size() && i < times.length; i++) {
            StudentDto s = candidates.get(i);
            Map<String, Object> map = new HashMap<>();
            map.put("name", s.getName());
            map.put("className", s.getClassName());
            map.put("time", times[i]);
            map.put("counselor", s.getCounselor() != null && !s.getCounselor().isEmpty() ? s.getCounselor() : "담당자");
            map.put("type", types[i]);
            map.put("badgeClass", badges[i]);
            result.add(map);
        }
        return result;
    }

    public List<Map<String, Object>> getTodayCounselings() { return getTodayCounselings("전체 기간"); }

    // ──────── 후속 연락 대상 ────────
    public List<Map<String, Object>> getFollowUpContacts(String period) {
        List<StudentDto> students = filterStudentsByPeriod(loadStudents(), period);
        List<StudentDto> candidates = students.stream()
                .filter(s -> "재원".equals(s.getStatus()) && s.getAttendanceRate() >= 80 && s.getAttendanceRate() < 90).limit(4).collect(Collectors.toList());
        if (candidates.size() < 4) {
            List<StudentDto> additional = students.stream()
                    .filter(s -> "재원".equals(s.getStatus()) && s.getAttendanceRate() >= 90)
                    .filter(s -> !candidates.contains(s)).limit(4 - candidates.size()).collect(Collectors.toList());
            candidates.addAll(additional);
        }

        List<Map<String, Object>> result = new ArrayList<>();
        String[] dues = {"오늘", "내일", "2일 후", "3일 후"};
        for (int i = 0; i < candidates.size() && i < dues.length; i++) {
            StudentDto s = candidates.get(i);
            Map<String, Object> map = new HashMap<>();
            map.put("name", s.getName());
            map.put("className", s.getClassName());
            map.put("gender", s.getGender());
            map.put("due", dues[i]);
            result.add(map);
        }
        return result;
    }

    public List<Map<String, Object>> getFollowUpContacts() { return getFollowUpContacts("전체 기간"); }

    // ──────── 퍼널 스테이지 ────────
    public Map<String, Object> getFunnelStages(String period) {
        List<StudentDto> students = filterStudentsByPeriod(loadStudents(), period);
        long enrolledCount = students.stream().filter(s -> "재원".equals(s.getStatus()) || "수료".equals(s.getStatus())).count();
        long applied = enrolledCount * 5;
        long inProgress = Math.round(enrolledCount * 3.3);
        long regConsult = Math.round(enrolledCount * 1.7);

        Map<String, Object> funnel = new HashMap<>();
        funnel.put("applied", applied);
        funnel.put("inProgress", inProgress);
        funnel.put("regConsult", regConsult);
        funnel.put("enrolled", enrolledCount);
        double conversionRate = applied > 0 ? ((double) enrolledCount / applied) * 100.0 : 0.0;
        funnel.put("conversionRate", Double.valueOf(String.format(Locale.US, "%.1f", conversionRate)));
        return funnel;
    }

    // ──────── 과정 성과 ────────
    public List<Map<String, Object>> getCoursePerformance() {
        List<StudentDto> students = loadStudents();
        List<StudentDto> janGrads = students.stream().filter(s -> s.getClassEnd() != null && s.getClassEnd().startsWith("2026-01")).collect(Collectors.toList());
        long janTotal = janGrads.size();
        long janCompleted = janGrads.stream().filter(s -> "수료".equals(s.getStatus())).count();
        long janEmployed = janGrads.stream().filter(s -> "수료".equals(s.getStatus()) && ("취업완료".equals(s.getEmploymentStatus()) || "재직중".equals(s.getEmploymentStatus()))).count();
        double janCompletionRate = janTotal > 0 ? ((double) janCompleted / janTotal) * 100.0 : 72.1;
        double janEmploymentRate = janCompleted > 0 ? ((double) janEmployed / janCompleted) * 100.0 : 52.3;

        List<StudentDto> febGrads = students.stream().filter(s -> s.getClassEnd() != null && s.getClassEnd().startsWith("2026-02")).collect(Collectors.toList());
        long febTotal = febGrads.size();
        long febCompleted = febGrads.stream().filter(s -> "수료".equals(s.getStatus())).count();
        long febEmployed = febGrads.stream().filter(s -> "수료".equals(s.getStatus()) && ("취업완료".equals(s.getEmploymentStatus()) || "재직중".equals(s.getEmploymentStatus()))).count();
        double febCompletionRate = febTotal > 0 ? ((double) febCompleted / febTotal) * 100.0 : 78.5;
        double febEmploymentRate = febCompleted > 0 ? ((double) febEmployed / febCompleted) * 100.0 : 58.2;

        List<Map<String, Object>> list = new ArrayList<>();
        list.add(Map.of("month", "2월", "completion", Double.valueOf(String.format(Locale.US, "%.1f", janCompletionRate)), "employment", Double.valueOf(String.format(Locale.US, "%.1f", janEmploymentRate))));
        list.add(Map.of("month", "3월", "completion", Double.valueOf(String.format(Locale.US, "%.1f", febCompletionRate)), "employment", Double.valueOf(String.format(Locale.US, "%.1f", febEmploymentRate))));
        list.add(Map.of("month", "4월", "completion", 70.0, "employment", 51.0));
        list.add(Map.of("month", "5월", "completion", 72.0, "employment", 58.0));
        list.add(Map.of("month", "6월", "completion", 76.1, "employment", 68.3));
        return list;
    }

    // ──────── 최근 활동 ────────
    public List<Map<String, Object>> getRecentActivities() {
        List<StudentDto> students = loadStudents();
        List<StudentDto> recent = students.stream()
                .filter(s -> s.getEnrolledAt() != null && !s.getEnrolledAt().isEmpty())
                .sorted((s1, s2) -> s2.getEnrolledAt().compareTo(s1.getEnrolledAt()))
                .limit(5).collect(Collectors.toList());

        List<Map<String, Object>> result = new ArrayList<>();
        String[] icons = {"bg-blue", "bg-green", "bg-purple", "bg-amber", "bg-blue"};
        String[] iconSymbols = {"i-calendar", "i-people-fill", "i-people-fill", "i-bell", "i-people-fill"};
        String[] times = {"10분 전", "30분 전", "1시간 전", "2시간 전", "3시간 전"};

        for (int i = 0; i < recent.size() && i < times.length; i++) {
            StudentDto s = recent.get(i);
            Map<String, Object> map = new HashMap<>();
            map.put("iconClass", icons[i]);
            map.put("iconSymbol", iconSymbols[i]);
            map.put("time", times[i]);
            int actType = i % 4;
            String msg;
            if (actType == 0) msg = s.getName() + " 학생이 " + s.getClassName() + " 과정에 신규 등록 완료되었습니다.";
            else if (actType == 1) msg = s.getName() + " 리드가 " + s.getCounselor() + " 상담사와의 상담을 완료했습니다.";
            else if (actType == 2) msg = s.getName() + " 학생의 출석률 및 특별 관리 기록이 생성되었습니다.";
            else msg = s.getName() + " 학생이 과제 제출 및 성과를 달성했습니다.";
            map.put("title", msg);
            result.add(map);
        }
        return result;
    }

    // ──────── 리드 목록 (상담 관리) — DB 기반 ────────
    public List<Map<String, Object>> getLeads() {
        List<StudentDto> students = loadStudents();
        com.example.demo.domain.settings.OperationSettings cfg = operationSettingsService.current();
        Map<Long, LeadMeta> metaMap = new HashMap<>();
        leadMetaRepository.findAll().forEach(lm -> metaMap.put(lm.getStudentId(), lm));

        List<Map<String, Object>> leads = new ArrayList<>();
        for (StudentDto s : students) {
            if ("수료".equals(s.getStatus())) continue;

            Map<String, Object> lead = new HashMap<>();
            lead.put("id", s.getId());
            lead.put("name", s.getName());
            lead.put("phone", maskPhoneNumber(s.getPhone()));
            lead.put("course", s.getCourseCategory() != null && !s.getCourseCategory().isEmpty() ? s.getCourseCategory() : "IT");
            lead.put("source", s.getChannel() != null && !s.getChannel().isEmpty() ? s.getChannel() : "홈페이지");
            lead.put("manager", s.getCounselor() != null && !s.getCounselor().isEmpty() ? s.getCounselor() : "김상담");

            // 리드 상태: DB 우선, 없으면 출석률 기반 자동 분류
            Long sid = Long.parseLong(s.getId());
            LeadMeta meta = metaMap.get(sid);
            String leadStatus;
            if (meta != null && meta.getLeadStatus() != null) {
                leadStatus = meta.getLeadStatus();
            } else {
                // 분류 기준은 설정 > 운영 기준에서 바꿀 수 있다.
                int att = s.getAttendanceRate();
                if (att >= cfg.getLeadDoneThreshold()) leadStatus = "등록완료";
                else if (att >= cfg.getLeadProgressThreshold()) leadStatus = "상담진행";
                else if (att >= cfg.getLeadScheduledThreshold()) leadStatus = "상담예정";
                else if (att >= cfg.getLeadNewThreshold()) leadStatus = "신규";
                else leadStatus = "이탈";
            }
            lead.put("status", leadStatus);

            String enrolled = s.getEnrolledAt();
            lead.put("lastContact", enrolled != null && !enrolled.isEmpty() ? enrolled.replace("-", ".") : "2026.07.22");
            String avatar = "여".equals(s.getGender()) ? "/images/default_avatar_woman.png" : "/images/default_avatar_man.png";
            lead.put("avatar", avatar);
            lead.put("memo", meta != null && meta.getMemo() != null ? meta.getMemo() : "");

            String followUp = null;
            if ("상담진행".equals(leadStatus)) followUp = "urgent";
            else if ("상담예정".equals(leadStatus)) followUp = "mid";
            lead.put("followUp", followUp);
            lead.put("className", s.getClassName());

            leads.add(lead);
        }
        return leads;
    }

    // ──────── 리드 상태/메모 업데이트 — DB 저장 ────────
    @Transactional
    public boolean updateLead(String id, String status, String memo, String lastContact) {
        Long sid = Long.parseLong(id);
        Optional<com.example.demo.domain.student.Student> optEntity = studentRepository.findById(sid);
        if (optEntity.isEmpty()) return false;

        com.example.demo.domain.student.Student entity = optEntity.get();
        if (lastContact != null) {
            entity.setEnrolledAt(LocalDate.parse(lastContact.replace(".", "-")));
        }
        studentRepository.save(entity);

        LeadMeta meta = leadMetaRepository.findByStudentId(sid).orElse(new LeadMeta(sid, null, null));
        if (status != null) meta.setLeadStatus(status);
        if (memo != null) meta.setMemo(memo);
        leadMetaRepository.save(meta);

        return true;
    }

    // ──────── 상담 히스토리 — DB 조회 ────────
    @Transactional
    public List<Map<String, String>> getCounselingHistory(String id) {
        Long sid;
        try { sid = Long.parseLong(id); } catch (NumberFormatException e) { return new ArrayList<>(); }

        List<CounselingLog> logs = counselingLogRepository.findByStudentIdOrderByIdDesc(sid);
        if (!logs.isEmpty()) {
            return logs.stream().map(cl -> {
                Map<String, String> m = new HashMap<>();
                m.put("date", cl.getLogDate());
                m.put("type", cl.getType());
                m.put("summary", cl.getSummary());
                m.put("memo", cl.getMemo());
                return m;
            }).collect(Collectors.toList());
        }

        // 기존 초기 히스토리 자동 생성 로직 (DB에 아직 데이터가 없는 경우)
        LeadMeta meta = leadMetaRepository.findByStudentId(sid).orElse(null);
        String leadStatus = meta != null && meta.getLeadStatus() != null ? meta.getLeadStatus() : "신규";

        List<Map<String, String>> history = new ArrayList<>();
        String lastDate = "2026.07.22";
        Optional<com.example.demo.domain.student.Student> optEntity = studentRepository.findById(sid);
        if (optEntity.isPresent() && optEntity.get().getEnrolledAt() != null) {
            lastDate = optEntity.get().getEnrolledAt().toString().replace("-", ".");
        }

        if ("등록완료".equals(leadStatus)) {
            history.add(Map.of("date", lastDate, "type", "대면", "summary", "최종 과정 등록 결정 및 수강 서약서 작성", "memo", "수강생이 커리큘럼과 취업 연계 혜택에 크게 만족하여 본 등록을 완료함."));
            history.add(Map.of("date", "2026.05.02", "type", "전화", "summary", "1차 유선 상세 안내", "memo", "비전공자로서 웹 개발자 과정 진입에 대한 고민을 조율하고, 수강 적절성 테스트 참여 일정을 잡음."));
        } else if ("상담진행".equals(leadStatus)) {
            history.add(Map.of("date", lastDate, "type", "대면", "summary", "원내 방문 및 과정 상세 설계 상담", "memo", "학원에 방문하여 학습 로드맵과 국비지원 신청 절차를 안내받음."));
        } else if ("상담예정".equals(leadStatus)) {
            history.add(Map.of("date", lastDate, "type", "카톡", "summary", "카카오톡 상담 채널을 통한 첫 문의 접수", "memo", "과정 개강일과 야간반 개설 여부를 문의함."));
        } else if ("이탈".equals(leadStatus)) {
            history.add(Map.of("date", lastDate, "type", "전화", "summary", "수강 의사 재확인 및 이탈 분류", "memo", "개인 사정으로 이번 기수 수강은 어렵다는 의사를 확인하여 이탈로 분류함."));
        } else {
            history.add(Map.of("date", lastDate, "type", "시스템", "summary", "신규 리드 정보 유입", "memo", "홈페이지/광고를 통해 관심 과정 문의가 신규로 접수되었습니다."));
        }

        // 자동 생성된 히스토리를 DB에 저장
        for (Map<String, String> h : history) {
            counselingLogRepository.save(new CounselingLog(sid, h.get("date"), h.get("type"), h.get("summary"), h.get("memo")));
        }
        return history;
    }

    // ──────── 상담 등록 — DB 저장 ────────
    @Transactional
    public boolean addCounseling(String id, String type, String summary, String memo) {
        Long sid;
        try { sid = Long.parseLong(id); } catch (NumberFormatException e) { return false; }

        String logDate = LocalDate.now().toString().replace("-", ".");
        counselingLogRepository.save(new CounselingLog(sid, logDate, type, summary, memo));

        // 상태가 '신규'이면 '상담진행'으로 전환
        LeadMeta meta = leadMetaRepository.findByStudentId(sid).orElse(new LeadMeta(sid, "신규", null));
        if ("신규".equals(meta.getLeadStatus())) {
            meta.setLeadStatus("상담진행");
        }
        leadMetaRepository.save(meta);

        // lastContact 업데이트
        updateLead(id, meta.getLeadStatus(), null, logDate);
        return true;
    }

    // ──────── 취업 데이터 ────────
    public Map<String, Object> getEmploymentData() {
        List<StudentDto> students = loadStudents();
        List<Map<String, Object>> graduates = new ArrayList<>();
        long totalGrads = 0, employedCount = 0, activeJobSeekers = 0, trackingTargets = 0;
        Map<String, Integer> classTotal = new HashMap<>(), classEmployed = new HashMap<>();

        for (StudentDto s : students) {
            if (!"수료".equals(s.getStatus())) continue;
            totalGrads++;

            String empStatus = s.getEmploymentStatus();
            if (empStatus == null || empStatus.trim().isEmpty()) empStatus = "미정";

            boolean isEmployed = "취업완료".equals(empStatus) || "재직중".equals(empStatus);
            if (isEmployed) employedCount++;
            else if ("구직중".equals(empStatus)) activeJobSeekers++;

            if (s.getTrackUntil() != null && !s.getTrackUntil().trim().isEmpty()) trackingTargets++;

            Map<String, Object> grad = new HashMap<>();
            grad.put("id", s.getId());
            grad.put("name", s.getName());
            grad.put("gender", s.getGender());
            grad.put("course", s.getCourseCategory() != null && !s.getCourseCategory().isEmpty() ? s.getCourseCategory() : "IT");
            grad.put("className", s.getClassName() != null && !s.getClassName().isEmpty() ? s.getClassName() : "IT-1반");
            grad.put("classEnd", s.getClassEnd() != null && !s.getClassEnd().isEmpty() ? s.getClassEnd().replace("-", ".") : "2026.07.22");
            grad.put("employmentStatus", empStatus);
            grad.put("company", s.getCompany() != null ? s.getCompany() : "");
            grad.put("employedAt", s.getEmployedAt() != null && !s.getEmployedAt().isEmpty() ? s.getEmployedAt().replace("-", ".") : "");
            grad.put("trackUntil", s.getTrackUntil() != null && !s.getTrackUntil().isEmpty() ? s.getTrackUntil().replace("-", ".") : "");
            grad.put("counselor", s.getCounselor() != null ? s.getCounselor() : "김상담");
            graduates.add(grad);

            String cls = s.getClassName() != null && !s.getClassName().isEmpty() ? s.getClassName() : "기타";
            classTotal.put(cls, classTotal.getOrDefault(cls, 0) + 1);
            if (isEmployed) classEmployed.put(cls, classEmployed.getOrDefault(cls, 0) + 1);
        }

        double rate = totalGrads > 0 ? ((double) employedCount / totalGrads) * 100.0 : 0.0;
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalGraduates", totalGrads);
        stats.put("employedCount", employedCount);
        stats.put("employmentRate", Double.valueOf(String.format(Locale.US, "%.1f", rate)));
        stats.put("activeJobSeekers", activeJobSeekers);
        stats.put("trackingTargetsCount", trackingTargets);

        List<Map<String, Object>> coursePerformance = new ArrayList<>();
        for (String cls : classTotal.keySet()) {
            int tot = classTotal.get(cls);
            int emp = classEmployed.getOrDefault(cls, 0);
            double clsRate = tot > 0 ? ((double) emp / tot) * 100.0 : 0.0;
            Map<String, Object> perf = new HashMap<>();
            perf.put("className", cls);
            perf.put("total", tot);
            perf.put("employed", emp);
            perf.put("rate", Double.valueOf(String.format(Locale.US, "%.1f", clsRate)));
            coursePerformance.add(perf);
        }

        Map<String, Object> response = new HashMap<>();
        response.put("stats", stats);
        response.put("graduates", graduates);
        response.put("coursePerformance", coursePerformance);
        return response;
    }

    // ──────── 취업 상태 업데이트 — DB 저장 ────────
    @Transactional
    public boolean updateEmployment(String id, String status, String company, String employedAt, String trackUntil) {
        Long sid = Long.parseLong(id);
        Optional<com.example.demo.domain.student.Student> optEntity = studentRepository.findById(sid);
        if (optEntity.isEmpty()) return false;

        com.example.demo.domain.student.Student entity = optEntity.get();
        if (status != null) entity.setEmploymentStatus(status);
        if (company != null) entity.setCompany(company);
        if (employedAt != null) entity.setEmployedAt(LocalDate.parse(employedAt.replace(".", "-")));
        if (trackUntil != null) entity.setTrackUntil(LocalDate.parse(trackUntil.replace(".", "-")));
        studentRepository.save(entity);
        return true;
    }

    // ──────── AI 취업 매칭 추천 ────────
    public List<Map<String, Object>> getAiJobRecommendations(String id, OpenAiService openAiService) {
        Long sid;
        try { sid = Long.parseLong(id); } catch (NumberFormatException e) { return new ArrayList<>(); }

        Optional<com.example.demo.domain.student.Student> optEntity = studentRepository.findById(sid);
        if (optEntity.isEmpty()) return new ArrayList<>();

        com.example.demo.domain.student.Student entity = optEntity.get();
        StudentDto student = toDto(entity);

        Map<String, Object> details = new HashMap<>();
        details.put("name", student.getName());
        details.put("gender", student.getGender());
        details.put("age", student.getAge());
        details.put("major", student.getMajorType());
        details.put("region", student.getRegion());
        details.put("education", student.getEducation());
        details.put("course", student.getClassName());
        details.put("attendanceRate", student.getAttendanceRate());
        details.put("employmentStatus", student.getEmploymentStatus());

        List<Map<String, String>> history = getCounselingHistory(id);
        details.put("counselingHistoryCount", history.size());
        if (!history.isEmpty()) {
            List<String> memos = new ArrayList<>();
            for (Map<String, String> h : history) {
                memos.add("[" + h.get("date") + "] " + h.get("summary") + ": " + h.get("memo"));
            }
            details.put("counselingMemos", memos);
        }

        try {
            String json = objectMapper.writeValueAsString(details);
            return openAiService.getAiJobMatching(json);
        } catch (Exception e) {
            log.error("Failed to serialize student details for AI matching: {}", e.getMessage());
            return openAiService.getAiJobMatching("{}");
        }
    }

    // ──────── 신규 리드 등록 — DB 저장 ────────
    @Transactional
    public boolean addLead(String name, String phone, String course, String source, String manager, String status, String memo) {
        com.example.demo.domain.student.Student entity = new com.example.demo.domain.student.Student();
        entity.setName(name);
        entity.setPhone(phone);
        entity.setCourseCategory(course != null ? course : "");
        entity.setChannel(source);
        entity.setCounselor(manager);
        entity.setStatus("대기");
        entity.setEnrolledAt(LocalDate.now());
        entity.setAge(0);
        entity.setGender("");
        entity.setClassName("");
        entity = studentRepository.save(entity);

        LeadMeta meta = new LeadMeta(entity.getId(), status, memo);
        leadMetaRepository.save(meta);

        String logDate = LocalDate.now().toString().replace("-", ".");
        String logMemo = memo != null && !memo.trim().isEmpty() ? memo : "홈페이지를 통해 신규 리드가 등록되었습니다.";
        counselingLogRepository.save(new CounselingLog(entity.getId(), logDate, "시스템", "신규 리드 정보 유입 및 등록", logMemo));

        return true;
    }

    // ──────── 유틸리티 ────────
    private String maskPhoneNumber(String phone) {
        if (phone == null || phone.isEmpty()) return "";
        String[] parts = phone.split("-");
        if (parts.length == 3) return parts[0] + "-" + parts[1] + "-****";
        String clean = phone.replaceAll("[^0-9]", "");
        if (clean.length() >= 7) return clean.substring(0, 3) + "-" + clean.substring(3, Math.min(7, clean.length())) + "-****";
        return phone;
    }
}
