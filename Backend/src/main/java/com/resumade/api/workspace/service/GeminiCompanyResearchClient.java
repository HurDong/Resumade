package com.resumade.api.workspace.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.resumade.api.workspace.dto.CompanyResearchResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class GeminiCompanyResearchClient {

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(20))
            .build();

    @Value("${gemini.api.key:}")
    private String apiKey;

    @Value("${gemini.api.url:https://generativelanguage.googleapis.com/v1beta}")
    private String apiUrl;

    @Value("${gemini.models.company-research:gemini-2.5-flash}")
    private String modelName;

    /**
     * 기업 및 직무 분석을 3단계로 수행합니다.
     * 1단계: JD + 기업명으로 사업부/제품 자동 추론
     * 2단계: 경력 공고 + 테크블로그 기반 기술 스택 딥다이브
     * 3단계: JD 명시 요구사항 vs 실제 기술 갭 분석
     *
     * @param company         기업명
     * @param position        지원 직무
     * @param rawJd           원본 JD 텍스트
     * @param additionalFocus 사용자가 특별히 궁금한 점 (선택, 없으면 빈 문자열)
     */
    public CompanyResearchResponse compose(
            String company,
            String position,
            String rawJd,
            String additionalFocus
    ) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("GEMINI_API_KEY is missing. Add it to the server environment before using company research.");
        }

        String systemPrompt = buildSystemPrompt();
        String userPrompt = buildUserPrompt(company, position, rawJd, additionalFocus);

        try {
            String requestBody = objectMapper.writeValueAsString(Map.of(
                    "systemInstruction", Map.of(
                            "parts", List.of(Map.of("text", systemPrompt))
                    ),
                    "contents", List.of(
                            Map.of(
                                    "role", "user",
                                    "parts", List.of(Map.of("text", userPrompt))
                            )
                    ),
                    "tools", List.of(
                            Map.of("google_search", Map.of())
                    ),
                    "generationConfig", Map.of(
                            "temperature", 0.1
                    )
            ));

            String encodedModel = URLEncoder.encode(modelName, StandardCharsets.UTF_8);
            String endpoint = apiUrl + "/models/" + encodedModel + ":generateContent?key=" + apiKey;

            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint))
                    .timeout(Duration.ofSeconds(120))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                throw new IllegalStateException(mapGeminiError(response.statusCode(), response.body()));
            }

            JsonNode root = objectMapper.readTree(response.body());

            // candidates 비어있는지 먼저 확인 (safety filter 등)
            JsonNode candidates = root.path("candidates");
            if (!candidates.isArray() || candidates.isEmpty()) {
                JsonNode feedback = root.path("promptFeedback");
                String blockReason = feedback.path("blockReason").asText("UNKNOWN");
                log.error("Gemini 기업조사 응답에 candidates 없음: blockReason={}, feedback={}",
                        blockReason, feedback);
                throw new IllegalStateException("Gemini 응답에 candidates가 없습니다. blockReason=" + blockReason);
            }

            // finishReason 확인
            String finishReason = candidates.path(0).path("finishReason").asText("");
            if (!finishReason.isBlank() && !"STOP".equals(finishReason) && !"MAX_TOKENS".equals(finishReason)) {
                log.warn("Gemini 기업조사 비정상 종료: finishReason={}", finishReason);
            }

            String text = extractResponseText(root);
            if (text.isBlank()) {
                JsonNode candidate0 = candidates.path(0);
                log.error("Gemini 기업조사 텍스트 비어있음: finishReason={}, candidate0={}",
                        finishReason, candidate0);
                throw new IllegalStateException("Gemini 기업조사 응답의 텍스트가 비어있습니다. finishReason=" + finishReason);
            }

            CompanyResearchResponse researchResponse = parseResearchResponse(text);
            enrichWithGroundingMetadata(researchResponse, root);
            return researchResponse;
        } catch (IOException e) {
            throw new IllegalStateException("Gemini company research request failed: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Gemini company research request was interrupted.", e);
        }
    }

    private String buildSystemPrompt() {
        return """
                당신은 한국 취업 시장 전문 기업 인텔리전스 분석가입니다.
                지원자가 자기소개서를 작성하기 전에, 해당 기업과 직무에 대한 실제 기술 정보를 깊게 파악할 수 있도록 도와주는 역할입니다.

                [분석 3단계 수행 지침]

                ▶ 1단계 — 자동 맥락 발견 (Auto Discovery)
                  제공된 기업명과 JD를 분석하여:
                  - 어느 사업부/팀 소속인지 추론 (예: 삼성전자 → MX사업부 갤럭시 소프트웨어팀)
                  - 해당 팀이 만드는 제품/서비스가 무엇인지 파악
                  - 조직 구조 및 비즈니스 맥락 파악
                  Google Search로 위 정보를 검색하여 보완하십시오.

                ▶ 2단계 — 기술 딥다이브 (Technical Deep Dive)
                  Google Search를 적극 활용하여 다음을 반드시 검색하십시오:
                  1. 경력 채용 공고: 원티드(wanted.co.kr), 사람인(saramin.co.kr), 잡코리아(jobkorea.co.kr)에서
                     "[기업명] [직무] 경력" 검색. 경력 공고는 기업이 실제 사용하는 기술을 정확히 명시함.
                  2. 공식 기술 블로그: "[기업명] 기술블로그", "[기업명] engineering blog" 검색
                  3. 개발자 컨퍼런스 발표: if.kakao.com, d2.naver.com, engineering.linecorp.com 등
                  4. GitHub 공개 레포: "[기업명] github" 검색

                  각 기술 스택 항목에 대해 반드시 다음을 기록하십시오:
                  - name: 정확한 버전 포함 (예: "Java 17", "Spring Boot 3.2.x") — 불확실하면 "Java (버전 미확인)"
                  - category: Backend / Frontend / Database / Infrastructure / DevOps / Mobile / AI-ML 중 하나
                  - confidence: CONFIRMED(경력공고/테크블로그에서 직접 확인) / INFERRED(간접 증거) / UNCERTAIN(추정)
                  - source: 출처 명시 (예: "원티드 경력공고 2025.03", "공식 기술블로그", "GitHub 레포")

                  수치/팩트가 있는 최근 기술 작업(recentTechWork)도 가능하면 발굴하십시오.
                  확인되지 않은 수치는 절대 만들지 마십시오. 없으면 비워두십시오.

                ▶ 3단계 — Fit 분석 (Gap Analysis)
                  JD에 명시된 요구사항과 실제로 파악된 기술 스택을 비교하여:
                  - JD가 모호하게 표현한 것의 실제 의미 해석
                  - JD에는 없지만 실제로 사용하는 기술 (경력 공고에서 발견)
                  - 지원자가 자소서에서 강조해야 할 기술 경험 힌트

                [출력 규칙]
                - 모든 자연어 필드는 한국어로 작성
                - JSON만 반환 (마크다운 코드블록 금지)
                - 확인되지 않은 정보는 UNCERTAIN으로 표시하고 추정임을 명시
                - 사용자 지시사항(프롬프트 최상단)이 있으면 모든 분석에 최우선으로 적용. 제외 지시가 있는 항목은 techStack, recentTechWork 등 어떤 섹션에도 포함하지 말 것.
                - 다음 JSON 스키마를 정확히 준수:

                {
                  "focus": {"company": "...", "position": "...", "inferredBusinessUnit": "...", "inferredProduct": "..."},
                  "executiveSummary": "...",
                  "discoveredContext": {
                    "businessUnit": "...",
                    "product": "...",
                    "evidenceSources": ["..."]
                  },
                  "businessContext": ["..."],
                  "serviceLandscape": ["..."],
                  "roleScope": ["..."],
                  "techStack": [
                    {"name": "...", "category": "...", "confidence": "CONFIRMED|INFERRED|UNCERTAIN", "source": "..."}
                  ],
                  "recentTechWork": [
                    {"summary": "...", "detail": "...", "source": "..."}
                  ],
                  "fitAnalysis": {
                    "jdStatedRequirements": ["..."],
                    "actualTechStack": ["..."],
                    "gapAnalysis": "...",
                    "coverLetterHints": ["..."]
                  },
                  "motivationHooks": ["..."],
                  "serviceHooks": ["..."],
                  "resumeAngles": ["..."],
                  "interviewSignals": ["..."],
                  "recommendedNarrative": "...",
                  "followUpQuestions": ["..."],
                  "confidenceNotes": ["..."]
                }
                """;
    }

    private String buildUserPrompt(String company, String position, String rawJd, String additionalFocus) {
        StringBuilder sb = new StringBuilder();

        // 사용자 지시사항을 JD보다 먼저, 강하게 선언
        if (additionalFocus != null && !additionalFocus.isBlank()) {
            sb.append("========================================\n");
            sb.append("[사용자 지시사항 — 반드시 준수, 절대 무시 금지]\n");
            sb.append(additionalFocus.trim()).append("\n");
            sb.append("위 지시사항은 분석 범위와 방향을 강제로 제한합니다.\n");
            sb.append("'찾지 말아줘', '제외', '빼줘' 등의 표현은 해당 항목을 분석 결과에서 완전히 제외하라는 의미입니다.\n");
            sb.append("========================================\n\n");
        }

        sb.append("기업명: ").append(company).append("\n");
        sb.append("지원 직무: ").append(position).append("\n");
        sb.append("\n[원본 JD]\n").append(rawJd);
        return sb.toString();
    }

    private String extractResponseText(JsonNode root) {
        JsonNode parts = root.path("candidates").path(0).path("content").path("parts");
        if (!parts.isArray()) return "";

        StringBuilder text = new StringBuilder();
        Iterator<JsonNode> iterator = parts.elements();
        while (iterator.hasNext()) {
            JsonNode part = iterator.next();
            // thinking 모델의 내부 사고 파트(thought: true)는 제외
            if (part.path("thought").asBoolean(false)) continue;
            String value = part.path("text").asText("");
            if (!value.isBlank()) {
                if (!text.isEmpty()) text.append('\n');
                text.append(value);
            }
        }
        return text.toString().trim();
    }

    private CompanyResearchResponse parseResearchResponse(String text) throws IOException {
        String normalized = text.trim();
        if (normalized.startsWith("```")) {
            normalized = normalized.replaceFirst("^```(?:json)?\\s*", "");
            normalized = normalized.replaceFirst("\\s*```$", "");
        }

        int objectStart = normalized.indexOf('{');
        int objectEnd = normalized.lastIndexOf('}');
        if (objectStart >= 0 && objectEnd > objectStart) {
            normalized = normalized.substring(objectStart, objectEnd + 1);
        }

        return objectMapper.readValue(normalized, CompanyResearchResponse.class);
    }

    /**
     * Gemini API 응답의 groundingMetadata에서 실제 검색어와 참고 URL을 추출하여
     * CompanyResearchResponse에 주입합니다.
     */
    private void enrichWithGroundingMetadata(CompanyResearchResponse response, JsonNode root) {
        JsonNode metadata = root.path("candidates").path(0).path("groundingMetadata");
        if (metadata.isMissingNode()) return;

        // 실제 검색 쿼리 추출
        JsonNode queriesNode = metadata.path("webSearchQueries");
        if (queriesNode.isArray()) {
            List<String> queries = new ArrayList<>();
            queriesNode.forEach(q -> {
                String query = q.asText("").trim();
                if (!query.isBlank()) queries.add(query);
            });
            if (!queries.isEmpty()) response.setSearchQueries(queries);
        }

        // 실제 참고 URL 추출 (중복 제거)
        JsonNode chunksNode = metadata.path("groundingChunks");
        if (chunksNode.isArray()) {
            List<CompanyResearchResponse.SearchSource> sources = new ArrayList<>();
            chunksNode.forEach(chunk -> {
                JsonNode web = chunk.path("web");
                String uri   = web.path("uri").asText("").trim();
                String title = web.path("title").asText("").trim();
                if (!uri.isBlank()) {
                    sources.add(CompanyResearchResponse.SearchSource.builder()
                            .uri(uri)
                            .title(title.isBlank() ? uri : title)
                            .build());
                }
            });
            if (!sources.isEmpty()) response.setSearchSources(sources);
        }

        log.info("Grounding metadata: {} queries, {} sources",
                response.getSearchQueries() != null ? response.getSearchQueries().size() : 0,
                response.getSearchSources() != null ? response.getSearchSources().size() : 0);
    }

    private String mapGeminiError(int statusCode, String responseBody) {
        String lowerBody = responseBody == null ? "" : responseBody.toLowerCase();

        if (lowerBody.contains("api key not valid") || lowerBody.contains("api_key_invalid")) {
            return "Gemini API 키가 유효하지 않습니다. .env의 GEMINI_API_KEY 값을 다시 확인해주세요.";
        }
        if (lowerBody.contains("quota") || lowerBody.contains("resource_exhausted")) {
            return "Gemini API 할당량을 초과했습니다. Google AI Studio 또는 Google Cloud Billing/쿼터 설정을 확인해주세요.";
        }
        if (lowerBody.contains("billing")) {
            return "Gemini API 빌링 설정이 필요합니다. Google AI Studio 또는 Google Cloud에서 빌링 상태를 확인해주세요.";
        }
        if (lowerBody.contains("not found for api version") || lowerBody.contains("not supported for generatecontent")) {
            return "Gemini 모델 설정값이 유효하지 않습니다. GEMINI_COMPANY_RESEARCH_MODEL을 확인해주세요. 현재 기본값은 gemini-2.5-flash입니다.";
        }
        if (lowerBody.contains("response mime type") && lowerBody.contains("unsupported")) {
            return "Gemini 요청 옵션이 현재 모델/툴 조합과 충돌했습니다. 검색 툴 사용 시에는 JSON MIME 강제 옵션을 제거해야 합니다.";
        }

        return "Gemini request failed with status " + statusCode + ": " + responseBody;
    }
}
