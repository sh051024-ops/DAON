package com.example.demo.controller;

import com.example.demo.service.OpenAiService;
import com.example.demo.service.StudentCrmService;
import com.example.demo.dto.StudentDto;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.ObjectMapper;

import java.util.*;

@RestController
@RequestMapping("/api/ai")
public class DashboardApiController {

    private final StudentCrmService studentCrmService;
    private final OpenAiService openAiService;
    private final ObjectMapper objectMapper;

    public DashboardApiController(StudentCrmService studentCrmService, OpenAiService openAiService, ObjectMapper objectMapper) {
        this.studentCrmService = studentCrmService;
        this.openAiService = openAiService;
        this.objectMapper = objectMapper;
    }

    @GetMapping("/dashboard")
    public Map<String, Object> getDashboardAnalysis(@RequestParam(value = "period", required = false) String period) {
        String studentSummary = studentCrmService.getAtRiskStudentsSummary();
        Map<String, Object> analysis = openAiService.getDashboardAnalysis(studentSummary);

        if (analysis == null) {
            analysis = new HashMap<>();
            analysis.put("tasks", Collections.emptyList());
        } else {
            // 완료 처리된 추천 업무를 목록에서 제외
            Object tasksObj = analysis.get("tasks");
            if (tasksObj instanceof List<?> list) {
                Set<String> completedTasks = studentCrmService.getCompletedTasks();
                List<String> filtered = list.stream()
                        .filter(String.class::isInstance)
                        .map(String.class::cast)
                        .filter(task -> !completedTasks.contains(task))
                        .toList();
                analysis.put("tasks", filtered);
            }
        }

        analysis.put("stats", studentCrmService.getStats(period));
        analysis.put("atRiskStudents", studentCrmService.getAtRiskStudentsForFrontend(period));
        analysis.put("todayCounselings", studentCrmService.getTodayCounselings(period));
        analysis.put("followUpContacts", studentCrmService.getFollowUpContacts(period));
        analysis.put("funnelStages", studentCrmService.getFunnelStages(period));
        analysis.put("performance", studentCrmService.getCoursePerformance());
        analysis.put("activities", studentCrmService.getRecentActivities());

        return analysis;
    }

    @PostMapping("/complete-task")
    public Map<String, Object> completeTask(@RequestBody CompleteTaskRequest request) {
        String task = request != null ? request.task() : null;
        Map<String, Object> result = new HashMap<>();
        if (task != null && !task.isBlank()) {
            studentCrmService.completeTask(task);
            result.put("success", true);
            result.put("message", "업무가 완료 처리되었습니다.");
        } else {
            result.put("success", false);
            result.put("message", "업무 내용이 비어 있습니다.");
        }
        return result;
    }

    @GetMapping("/leads")
    public List<Map<String, Object>> getLeads() {
        return studentCrmService.getLeads();
    }

    @PostMapping("/update-lead")
    public Map<String, Object> updateLead(@RequestBody UpdateLeadRequest request) {
        boolean success = request != null && studentCrmService.updateLead(
                request.id(),
                request.status(),
                request.memo(),
                request.lastContact()
        );
        Map<String, Object> result = new HashMap<>();
        result.put("success", success);
        return result;
    }

    @GetMapping("/counseling-history")
    public List<Map<String, String>> getCounselingHistory(@RequestParam("id") String id) {
        return studentCrmService.getCounselingHistory(id);
    }

    @PostMapping("/add-counseling")
    public Map<String, Object> addCounseling(@RequestBody AddCounselingRequest request) {
        boolean success = request != null && studentCrmService.addCounseling(
                request.id(),
                request.type(),
                request.summary(),
                request.memo()
        );
        Map<String, Object> result = new HashMap<>();
        result.put("success", success);
        return result;
    }

    @GetMapping("/employment")
    public Map<String, Object> getEmployment() {
        return studentCrmService.getEmploymentData();
    }

    @PostMapping("/update-employment")
    public Map<String, Object> updateEmployment(@RequestBody UpdateEmploymentRequest request) {
        boolean success = request != null && studentCrmService.updateEmployment(
                request.id(),
                request.status(),
                request.company(),
                request.employedAt(),
                request.trackUntil()
        );
        Map<String, Object> result = new HashMap<>();
        result.put("success", success);
        return result;
    }

    @PostMapping("/match-jobs")
    public List<Map<String, Object>> matchJobs(@RequestBody MatchJobsRequest request) {
        String id = request != null ? request.id() : null;
        if (id == null || id.trim().isEmpty()) {
            return Collections.emptyList();
        }
        return studentCrmService.getAiJobRecommendations(id, openAiService);
    }

    @PostMapping("/counseling-strategy")
    public Map<String, String> getCounselingStrategy(@RequestBody MatchJobsRequest request) {
        String id = request != null ? request.id() : null;
        if (id == null || id.trim().isEmpty()) {
            return Collections.emptyMap();
        }
        List<StudentDto> students = studentCrmService.loadStudents();
        StudentDto student = null;
        for (StudentDto s : students) {
            if (s.getId().equals(id)) {
                student = s;
                break;
            }
        }
        if (student == null) {
            return Collections.emptyMap();
        }
        Map<String, Object> details = new HashMap<>();
        details.put("name", student.getName());
        details.put("course", student.getCourseCategory());
        details.put("channel", student.getChannel());
        details.put("counselor", student.getCounselor());
        details.put("status", student.getStatus());
        String memo = studentCrmService.getLeadMemos().get(id);
        details.put("memo", memo != null ? memo : "");

        try {
            String json = objectMapper.writeValueAsString(details);
            return openAiService.getCounselingStrategy(json);
        } catch (Exception e) {
            return openAiService.getCounselingStrategy("{}");
        }
    }

    @PostMapping("/add-lead")
    public Map<String, Object> addLead(@RequestBody AddLeadRequest request) {
        boolean success = request != null && studentCrmService.addLead(
                request.name(),
                request.phone(),
                request.course(),
                request.source(),
                request.manager(),
                request.status(),
                request.memo()
        );
        Map<String, Object> result = new HashMap<>();
        result.put("success", success);
        return result;
    }

    // DTO Records for Request payloads
    public record CompleteTaskRequest(String task) {}
    public record UpdateLeadRequest(String id, String status, String memo, String lastContact) {}
    public record AddCounselingRequest(String id, String type, String summary, String memo) {}
    public record UpdateEmploymentRequest(String id, String status, String company, String employedAt, String trackUntil) {}
    public record MatchJobsRequest(String id) {}
    public record AddLeadRequest(String name, String phone, String course, String source, String manager, String status, String memo) {}
}
