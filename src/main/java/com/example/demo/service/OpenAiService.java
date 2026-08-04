package com.example.demo.service;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@Service
public class OpenAiService {

    @Value("${openai.api.key:}")
    private String apiKey;

    @Value("${openai.model:gpt-4o}")
    private String model;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper;

    public OpenAiService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public Map<String, Object> getDashboardAnalysis(String studentSummaryJson) {
        Map<String, Object> result = new HashMap<>();

        // Check if API key is configured
        if (apiKey == null || apiKey.trim().isEmpty() || apiKey.contains("OPENAI_API_KEY")) {
            System.out.println("OpenAI API Key is missing. Using Mock Mode.");
            return getMockData();
        }

        try {
            String url = "https://api.openai.com/v1/chat/completions";

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey);

            // Construct Chat Completion Request
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", model);

            List<Map<String, String>> messages = new ArrayList<>();
            messages.add(Map.of(
                "role", "system",
                "content", "너는 DAON EDU 학원의 관리 대시보드를 돕는 AI 비서이다. 전달되는 학생 분석 요약 데이터(JSON)를 바탕으로 다음 2가지 정보를 반드시 한글로 작성해라.\n" +
                           "1. 오늘의 AI 인사이트 (가장 긴급하거나 눈에 띄는 학원 운영 상황 요약, 1문장)\n" +
                           "2. 오늘 꼭 처리해야 할 긴급 추천 업무 리스트 (정확히 4개 아이템)\n\n" +
                           "중요: 추천 업무 4개는 반드시 학원 운영의 다양한 영역을 종합적으로 포함해야 한다.\n" +
                           "아래 카테고리 중 최소 3개 이상의 서로 다른 영역에서 업무를 추천해라:\n" +
                           "- 출석/출결 관리: 결석 사유 확인, 보강 일정 조율 등\n" +
                           "- 리드 관리: 신규 리드 후속 연락, 상담 전환 대상 등\n" +
                           "- 학생 상담: 면담 진행, 학부모 안내, 케어 요청 등\n" +
                           "- 취업 관리: 취업 현황 확인, 이력서 검토, 채용 연계 등\n" +
                           "- CS/문의: 미응답 문의 처리, 수강료 안내 등\n\n" +
                           "각 업무 텍스트에 해당 카테고리를 유추할 수 있는 키워드(출석, 결석, 리드, 취업, 상담 등)를 반드시 포함해라.\n\n" +
                           "반드시 아래 JSON 형식으로만 답변을 반환하고 마크다운 코드 블록(```json) 없이 원시 문자열로만 응답해라. 응답 JSON 형식:\n" +
                           "{\n  \"insight\": \"오늘의 요약 인사이트 문장\",\n  \"tasks\": [\"업무 1\", \"업무 2\", \"업무 3\", \"업무 4\"]\n}"
            ));
            messages.add(Map.of(
                "role", "user",
                "content", studentSummaryJson
            ));

            requestBody.put("messages", messages);
            requestBody.put("response_format", Map.of("type", "json_object"));

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
            ResponseEntity<String> response = restTemplate.postForEntity(url, entity, String.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                JsonNode root = objectMapper.readTree(response.getBody());
                String content = root.path("choices").path(0).path("message").path("content").asString();

                JsonNode parsedContent = objectMapper.readTree(content);
                result.put("insight", parsedContent.path("insight").asString());

                List<String> tasks = new ArrayList<>();
                JsonNode tasksNode = parsedContent.path("tasks");
                if (tasksNode.isArray()) {
                    for (JsonNode t : tasksNode) {
                        tasks.add(t.asString());
                    }
                }
                result.put("tasks", tasks);
                result.put("mocked", false);
                return result;
            } else {
                System.err.println("OpenAI API call failed with status: " + response.getStatusCode());
            }

        } catch (Exception e) {
            System.err.println("Error calling OpenAI API: " + e.getMessage());
        }

        System.out.println("Falling back to Mock Mode due to error.");
        return getMockData();
    }

    private Map<String, Object> getMockData() {
        Map<String, Object> mock = new HashMap<>();
        mock.put("insight", "위험 수강생 5명과 미처리 리드 3건이 감지되었습니다. 출석 관리와 리드 전환 업무를 병행해 주세요.");
        mock.put("tasks", List.of(
            "김서준(IT-1반) 학생 출석률 56% - 결석 사유 확인 및 보강 수강 일정 조율",
            "신규 리드 박하은(웹 개발 과정 문의) - 2일 내 후속 상담 전화 필요",
            "오정식(IT-1반) 학생 면담 - 김상담 상담사 유선 면담 진행 및 학부모 안내",
            "데이터 분석 24기 수료생 이수빈 - 취업 현황 확인 및 채용 연계 추천서 작성"
        ));
        mock.put("mocked", true);
        return mock;
    }

    public List<Map<String, Object>> getAiJobMatching(String studentDetailsJson) {
        List<Map<String, Object>> result = new ArrayList<>();

        if (apiKey == null || apiKey.trim().isEmpty() || apiKey.contains("OPENAI_API_KEY")) {
            System.out.println("OpenAI API Key is missing. Using Mock Job Matching Mode.");
            return getMockJobMatching();
        }

        try {
            String url = "https://api.openai.com/v1/chat/completions";

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey);

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", model);

            List<Map<String, String>> messages = new ArrayList<>();
            messages.add(Map.of(
                "role", "system",
                "content", "너는 DAON EDU 학원의 구직자를 위한 전문 취업 추천 AI 비서이다. 전달되는 구직자의 세부 학력, 전공 여부, 출석률, 점수, 상담 일지 기록(JSON)을 면밀히 분석하여 가장 적합도가 높은 국내 가상/실제 IT/웹 기업 3개를 한글로 추천해라.\n" +
                           "각 추천 기업별로 다음 항목을 포함한 JSON을 만들어라.\n" +
                           "1. companyName (추천 기업명)\n" +
                           "2. jobTitle (추천 직무명)\n" +
                           "3. matchScore (매칭 적합도 점수, 0~100 사이의 정수)\n" +
                           "4. reason (이 구직자의 강점 및 전공 여부, 상담 이력과 연결된 취업 매칭 추천 사유, 2~3문장)\n" +
                           "5. tip (이력서 보완 방향이나 면접 대비 합격 팁, 1~2문장)\n\n" +
                           "반드시 아래 JSON 형식으로만 답변을 반환하고 마크다운 코드 블록(```json) 없이 원시 문자열로만 응답해라. 응답 JSON 형식:\n" +
                           "{\n  \"recommendations\": [\n    {\n      \"companyName\": \"기업명\",\n      \"jobTitle\": \"직무명\",\n      \"matchScore\": 95,\n      \"reason\": \"추천 사유\",\n      \"tip\": \"면접 합격 팁\"\n    }\n  ]\n}"
            ));
            messages.add(Map.of(
                "role", "user",
                "content", studentDetailsJson
            ));

            requestBody.put("messages", messages);
            requestBody.put("response_format", Map.of("type", "json_object"));

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
            ResponseEntity<String> response = restTemplate.postForEntity(url, entity, String.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                JsonNode root = objectMapper.readTree(response.getBody());
                String content = root.path("choices").path(0).path("message").path("content").asString();

                JsonNode parsedContent = objectMapper.readTree(content);
                JsonNode recsNode = parsedContent.path("recommendations");
                if (recsNode.isArray()) {
                    for (JsonNode node : recsNode) {
                        Map<String, Object> rec = new HashMap<>();
                        rec.put("companyName", node.path("companyName").asString());
                        rec.put("jobTitle", node.path("jobTitle").asString());
                        rec.put("matchScore", node.path("matchScore").asInt());
                        rec.put("reason", node.path("reason").asString());
                        rec.put("tip", node.path("tip").asString());
                        result.add(rec);
                    }
                }
                return result;
            } else {
                System.err.println("OpenAI API job matching call failed: " + response.getStatusCode());
            }

        } catch (Exception e) {
            System.err.println("Error calling OpenAI Job Matching API: " + e.getMessage());
        }

        System.out.println("Falling back to Mock Job Matching due to error.");
        return getMockJobMatching();
    }

    private List<Map<String, Object>> getMockJobMatching() {
        List<Map<String, Object>> mock = new ArrayList<>();
        
        Map<String, Object> rec1 = new HashMap<>();
        rec1.put("companyName", "네이버 클라우드");
        rec1.put("jobTitle", "백엔드 개발자 (신입)");
        rec1.put("matchScore", 92);
        rec1.put("reason", "수강생의 우수한 학업 점수(90점 이상)와 뛰어난 출석 성실함을 볼 때 대규모 트래픽을 처리하는 클라우드 인프라 백엔드 포지션에 적합합니다. 비전공자임에도 프로젝트 완수 능력이 우수합니다.");
        rec1.put("tip", "네이버 클라우드 플랫폼(NCP)의 리눅스 기초 지식과 Spring Boot 연동 웹 API 포트폴리오를 강조해 이력서를 보완해 보세요.");
        mock.add(rec1);

        Map<String, Object> rec2 = new HashMap<>();
        rec2.put("companyName", "우아한형제들");
        rec2.put("jobTitle", "Java 백엔드 서비스 개발자");
        rec2.put("matchScore", 88);
        rec2.put("reason", "학습 과정 중 JPA 및 REST API 설계 평가에서 뛰어난 성취를 보여 배달의민족 서비스 플랫폼 부서의 신입 Java 백엔드 직무와 매우 강하게 매치됩니다.");
        rec2.put("tip", "실제 협업 도구 사용 경험과 DB 쿼리 튜닝 성능 최적화 경험을 강조하면 서류 합격율이 대폭 올라갑니다.");
        mock.add(rec2);

        Map<String, Object> rec3 = new HashMap<>();
        rec3.put("companyName", "당근마켓");
        rec3.put("jobTitle", "웹 풀스택 엔지니어");
        rec3.put("matchScore", 85);
        rec3.put("reason", "상담 기록지상 커뮤니케이션 능력이 뛰어난 강점이 있어 애자일 스쿼드 조직을 지향하는 당근마켓의 풀스택 주니어 포지션에 협업 강자로 적합도가 높습니다.");
        rec3.put("tip", "로컬 커뮤니티 성격의 미니 프로젝트 결과물을 GitHub 레포지토리에 올리고 README 문서를 성실히 적는 것을 추천합니다.");
        mock.add(rec3);

        return mock;
    }

    public String getAttendanceAnalysis(String attendanceDataJson) {
        if (apiKey == null || apiKey.trim().isEmpty() || apiKey.contains("OPENAI_API_KEY")) {
            return "이번 주 그래픽디자인반 김서준 학생의 결석률이 이례적으로 상승하고 있습니다. 학업 의지 상실이나 개인 사정이 있을 수 있으니 금일 유선 면담을 요청해 조치해 주세요.";
        }
        try {
            String url = "https://api.openai.com/v1/chat/completions";
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey);

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", model);

            List<Map<String, String>> messages = new ArrayList<>();
            messages.add(Map.of(
                "role", "system",
                "content", "너는 DAON EDU 학원의 출결 관리를 담당하는 AI 비서이다. 전달받은 연속 결석자 정보(JSON)를 분석하여, 담당자에게 필요한 출결 관리 제안이나 경고 분석 내용을 한글로 한 문장(공백 포함 70자 내외)으로 간결하게 조언해라."
            ));
            messages.add(Map.of(
                "role", "user",
                "content", attendanceDataJson
            ));

            requestBody.put("messages", messages);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
            ResponseEntity<String> response = restTemplate.postForEntity(url, entity, String.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                JsonNode root = objectMapper.readTree(response.getBody());
                String content = root.path("choices").path(0).path("message").path("content").asString();
                return content.trim();
            }
        } catch (Exception e) {
            System.err.println("Error calling OpenAI Attendance Analysis API: " + e.getMessage());
        }
        return "이번 주 그래픽디자인반 김서준 학생의 결석률이 이례적으로 상승하고 있습니다. 학업 의지 상실이나 개인 사정이 있을 수 있으니 금일 유선 면담을 요청해 조치해 주세요.";
    }

    public Map<String, String> getCounselingStrategy(String leadJson) {
        Map<String, String> result = new HashMap<>();
        if (apiKey == null || apiKey.trim().isEmpty() || apiKey.contains("OPENAI_API_KEY")) {
            result.put("strategy", "이 리드는 인스타 광고를 통해 '웹 개발자 과정'에 지원한 신규 유입자입니다. 트렌디한 IT 개발 분야에 관심이 크므로 취업 성과 및 포트폴리오 수준을 강조하는 상담이 효과적입니다.");
            result.put("template", "안녕하세요! DAON EDU 학원입니다. 웹 개발자 과정 문의 건에 대해 안내해 드립니다. 비전공자도 6개월 만에 실무 프로젝트를 수행하여 우수 취업 연계까지 가능한 커리큘럼입니다. 자세한 일정을 잡고 무료 적성 진단을 도와드리겠습니다.");
            return result;
        }
        try {
            String url = "https://api.openai.com/v1/chat/completions";
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey);

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", model);

            List<Map<String, String>> messages = new ArrayList<>();
            messages.add(Map.of(
                "role", "system",
                "content", "너는 DAON EDU 학원의 CRM 상담을 조율하는 AI 비서이다. 전달받은 신규 리드 정보(JSON)를 분석하여 두 가지 항목을 반드시 한글로 작성해라.\n" +
                           "1. strategy: 이 리드를 상담할 때 유용한 핵심 세일즈 소구점 및 상담 조언 (2~3문장)\n" +
                           "2. template: 고객에게 바로 발송하거나 전할 수 있는 맞춤형 첫 인사/안내 문자/답변 템플릿 (3문장 내외)\n" +
                           "반드시 아래 JSON 형식으로만 답변을 반환하고 마크다운 코드 블록 없이 원시 문자열로만 응답해라. 응답 JSON 형식:\n" +
                           "{\n  \"strategy\": \"전략 내용\",\n  \"template\": \"템플릿 내용\"\n}"
            ));
            messages.add(Map.of(
                "role", "user",
                "content", leadJson
            ));

            requestBody.put("messages", messages);
            requestBody.put("response_format", Map.of("type", "json_object"));

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
            ResponseEntity<String> response = restTemplate.postForEntity(url, entity, String.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                JsonNode root = objectMapper.readTree(response.getBody());
                String content = root.path("choices").path(0).path("message").path("content").asString();
                JsonNode parsedContent = objectMapper.readTree(content);
                result.put("strategy", parsedContent.path("strategy").asString());
                result.put("template", parsedContent.path("template").asString());
                return result;
            }
        } catch (Exception e) {
            System.err.println("Error calling OpenAI Counseling Strategy API: " + e.getMessage());
        }
        result.put("strategy", "이 리드는 인스타 광고를 통해 '웹 개발자 과정'에 지원한 신규 유입자입니다. 트렌디한 IT 개발 분야에 관심이 크므로 취업 성과 및 포트폴리오 수준을 강조하는 상담이 효과적입니다.");
        result.put("template", "안녕하세요! DAON EDU 학원입니다. 웹 개발자 과정 문의 건에 대해 안내해 드립니다. 비전공자도 6개월 만에 실무 프로젝트를 수행하여 우수 취업 연계까지 가능한 커리큘럼입니다. 자세한 일정을 잡고 무료 적성 진단을 도와드리겠습니다.");
        return result;
    }

    /** 설정 화면의 "연결 테스트" 전용 — 토큰을 소모하지 않는 /v1/models 호출로 키 유효성만 확인한다. */
    public boolean isConfigured() {
        return apiKey != null && !apiKey.trim().isEmpty() && !apiKey.contains("OPENAI_API_KEY");
    }

    public String getModel() {
        return model;
    }

    public String testConnection() {
        if (!isConfigured()) {
            throw new IllegalStateException("OPENAI_API_KEY가 설정되어 있지 않습니다 (Mock 모드로 동작 중).");
        }
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(apiKey);
            HttpEntity<Void> entity = new HttpEntity<>(headers);
            ResponseEntity<String> response = restTemplate.exchange(
                    "https://api.openai.com/v1/models", org.springframework.http.HttpMethod.GET, entity, String.class);
            if (!response.getStatusCode().is2xxSuccessful()) {
                throw new IllegalStateException("OpenAI 응답 코드: " + response.getStatusCode());
            }
            return "연결 성공 (" + model + ")";
        } catch (Exception e) {
            throw new IllegalStateException("연결 실패: " + e.getMessage(), e);
        }
    }
}
