package com.resumade.api.workspace.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class GeminiCompanyResearchClient {

    private static final int DISABLED_THINKING_BUDGET = 0;
    private static final double DEFAULT_TEMPERATURE = 0.1;
    private static final String UNGROUNDED_FALLBACK_NOTE =
            "실시간 검색 응답이 비어 있어, 검색 없이 생성한 보조 분석입니다. 최신성과 출처는 별도로 확인하세요.";
    private static final List<RequestMode> REQUEST_MODES = List.of(
            new RequestMode("grounded-search", true, false, 2),
            new RequestMode("structured-fallback", false, true, 1)
    );

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
            String lastRetryableError = null;

            for (RequestMode mode : REQUEST_MODES) {
                for (int attempt = 1; attempt <= mode.maxAttempts(); attempt++) {
                    try {
                        JsonNode root = sendGenerateContent(systemPrompt, userPrompt, mode);
                        CompanyResearchResponse response = extractResearchResponse(root, mode);
                        enrichWithGroundingMetadata(response, root);
                        if (!mode.useGoogleSearch()) {
                            applyFallbackConfidenceNote(response);
                        }
                        return response;
                    } catch (RetryableGeminiResponseException e) {
                        lastRetryableError = e.getMessage();
                        log.warn("Gemini company research retry scheduled: mode={}, attempt={}/{}, reason={}",
                                mode.label(), attempt, mode.maxAttempts(), e.getMessage());
                    }
                }
            }

            throw new IllegalStateException(lastRetryableError != null
                    ? lastRetryableError
                    : "Gemini 기업조사 요청이 반복 실패했습니다.");
        } catch (IOException e) {
            throw new IllegalStateException("Gemini company research request failed: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Gemini company research request was interrupted.", e);
        }
    }

    private JsonNode sendGenerateContent(String systemPrompt, String userPrompt, RequestMode mode)
            throws IOException, InterruptedException {
        String requestBody = objectMapper.writeValueAsString(buildRequestPayload(systemPrompt, userPrompt, mode));

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

        return objectMapper.readTree(response.body());
    }

    private Map<String, Object> buildRequestPayload(String systemPrompt, String userPrompt, RequestMode mode) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("systemInstruction", Map.of(
                "parts", List.of(Map.of("text", systemPrompt))
        ));
        payload.put("contents", List.of(
                Map.of(
                        "role", "user",
                        "parts", List.of(Map.of("text", userPrompt))
                )
        ));
        if (mode.useGoogleSearch()) {
            payload.put("tools", List.of(Map.of("google_search", Map.of())));
        }
        payload.put("generationConfig", buildGenerationConfig(mode));
        return payload;
    }

    private Map<String, Object> buildGenerationConfig(RequestMode mode) {
        Map<String, Object> generationConfig = new LinkedHashMap<>();
        generationConfig.put("temperature", DEFAULT_TEMPERATURE);
        generationConfig.put("thinkingConfig", Map.of("thinkingBudget", DISABLED_THINKING_BUDGET));
        if (mode.useStructuredOutput()) {
            generationConfig.put("responseMimeType", "application/json");
            generationConfig.put("responseJsonSchema", buildCompanyResearchResponseSchema());
        }
        return generationConfig;
    }

    private CompanyResearchResponse extractResearchResponse(JsonNode root, RequestMode mode) throws IOException {
        JsonNode candidates = root.path("candidates");
        if (!candidates.isArray() || candidates.isEmpty()) {
            JsonNode feedback = root.path("promptFeedback");
            String blockReason = feedback.path("blockReason").asText("UNKNOWN");
            log.error("Gemini company research response has no candidates: mode={}, blockReason={}, feedback={}",
                    mode.label(), blockReason, feedback);
            throw new IllegalStateException("Gemini 응답에 candidates가 없습니다. blockReason=" + blockReason);
        }

        String finishReason = candidates.path(0).path("finishReason").asText("");
        if (!finishReason.isBlank() && !"STOP".equals(finishReason) && !"MAX_TOKENS".equals(finishReason)) {
            log.warn("Gemini company research finished abnormally: mode={}, finishReason={}",
                    mode.label(), finishReason);
        }

        String text = extractResponseText(root);
        if (text.isBlank()) {
            JsonNode candidate0 = candidates.path(0);
            JsonNode usage = root.path("usageMetadata");
            log.error("Gemini company research text empty: mode={}, finishReason={}, usage={}, candidate0={}",
                    mode.label(), finishReason, usage, candidate0);
            throw new RetryableGeminiResponseException(
                    "Gemini 기업조사 응답의 텍스트가 비어있습니다. finishReason=" + finishReason
            );
        }

        try {
            return parseResearchResponse(text);
        } catch (IOException e) {
            log.error("Gemini company research JSON parse failed: mode={}, text={}", mode.label(), text);
            throw new RetryableGeminiResponseException("Gemini 기업조사 JSON 파싱에 실패했습니다: " + e.getMessage(), e);
        }
    }

    private void applyFallbackConfidenceNote(CompanyResearchResponse response) {
        List<String> notes = response.getConfidenceNotes() == null
                ? new ArrayList<>()
                : new ArrayList<>(response.getConfidenceNotes());

        if (!notes.contains(UNGROUNDED_FALLBACK_NOTE)) {
            notes.add(0, UNGROUNDED_FALLBACK_NOTE);
        }
        response.setConfidenceNotes(notes);
    }

    private String buildSystemPrompt() {
        return """
                You are a senior company and role research analyst for Korean job seekers.
                Your task is to produce a company-research brief that helps the user write a strong Korean cover letter.

                Requirements:
                - All natural-language fields must be written in Korean.
                - Return JSON only. No markdown fences, no prose outside JSON.
                - If information is uncertain, mark that uncertainty in the relevant text.
                - Respect the user's additional focus instructions as the highest-priority scope constraint.

                Research workflow:
                1. Infer the likely business unit and product/service from the company name and JD.
                2. If search is available, use it to inspect recent hiring posts, engineering blogs, conference talks, and GitHub sources.
                3. Compare the JD's stated requirements with the company's likely real tech stack and hiring signals.

                Output schema:
                {
                  "focus": {
                    "company": "string",
                    "position": "string",
                    "inferredBusinessUnit": "string",
                    "inferredProduct": "string"
                  },
                  "executiveSummary": "string",
                  "discoveredContext": {
                    "businessUnit": "string",
                    "product": "string",
                    "evidenceSources": ["string"]
                  },
                  "businessContext": ["string"],
                  "serviceLandscape": ["string"],
                  "roleScope": ["string"],
                  "techStack": [
                    {
                      "name": "string",
                      "category": "Backend|Frontend|Database|Infrastructure|DevOps|Mobile|AI-ML",
                      "confidence": "CONFIRMED|INFERRED|UNCERTAIN",
                      "source": "string"
                    }
                  ],
                  "recentTechWork": [
                    {
                      "summary": "string",
                      "detail": "string",
                      "source": "string"
                    }
                  ],
                  "fitAnalysis": {
                    "jdStatedRequirements": ["string"],
                    "actualTechStack": ["string"],
                    "gapAnalysis": "string",
                    "coverLetterHints": ["string"]
                  },
                  "motivationHooks": ["string"],
                  "serviceHooks": ["string"],
                  "resumeAngles": ["string"],
                  "interviewSignals": ["string"],
                  "recommendedNarrative": "string",
                  "followUpQuestions": ["string"],
                  "confidenceNotes": ["string"]
                }
                """;
    }

    private String buildUserPrompt(String company, String position, String rawJd, String additionalFocus) {
        StringBuilder sb = new StringBuilder();

        if (additionalFocus != null && !additionalFocus.isBlank()) {
            sb.append("[Additional focus instructions]\n");
            sb.append(additionalFocus.trim()).append("\n\n");
        }

        sb.append("Company: ").append(company).append("\n");
        sb.append("Position: ").append(position).append("\n");
        sb.append("\n[Raw JD]\n").append(rawJd);
        return sb.toString();
    }

    private String extractResponseText(JsonNode root) {
        JsonNode parts = root.path("candidates").path(0).path("content").path("parts");
        if (!parts.isArray()) {
            return "";
        }

        StringBuilder text = new StringBuilder();
        Iterator<JsonNode> iterator = parts.elements();
        while (iterator.hasNext()) {
            JsonNode part = iterator.next();
            if (part.path("thought").asBoolean(false)) {
                continue;
            }
            String value = part.path("text").asText("");
            if (!value.isBlank()) {
                if (!text.isEmpty()) {
                    text.append('\n');
                }
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

        JsonNode root = objectMapper.readTree(normalized);
        if (root instanceof ObjectNode objectNode) {
            normalizeLegacyResponseShape(objectNode);
            return objectMapper.treeToValue(objectNode, CompanyResearchResponse.class);
        }

        return objectMapper.readValue(normalized, CompanyResearchResponse.class);
    }

    private void normalizeLegacyResponseShape(ObjectNode root) {
        ObjectNode focusNode = ensureObjectNode(root, "focus");
        copyTextIfMissing(root, focusNode, "company", "company");
        copyTextIfMissing(root, focusNode, "position", "position");
        copyTextIfMissing(root, focusNode, "inferredBusinessUnit", "inferredBusinessUnit");
        copyTextIfMissing(root, focusNode, "inferredProduct", "inferredProduct");
        copyTextIfMissing(root, focusNode, "businessUnit", "inferredBusinessUnit");
        copyTextIfMissing(root, focusNode, "product", "inferredProduct");

        if (root.has("businessUnit") || root.has("product") || root.has("evidenceSources")) {
            ObjectNode discoveredContextNode = ensureObjectNode(root, "discoveredContext");
            copyTextIfMissing(root, discoveredContextNode, "businessUnit", "businessUnit");
            copyTextIfMissing(root, discoveredContextNode, "product", "product");
            copyArrayIfMissing(root, discoveredContextNode, "evidenceSources", "evidenceSources");
        }
    }

    private ObjectNode ensureObjectNode(ObjectNode parent, String fieldName) {
        JsonNode existing = parent.get(fieldName);
        if (existing instanceof ObjectNode objectNode) {
            return objectNode;
        }
        return parent.putObject(fieldName);
    }

    private void copyTextIfMissing(ObjectNode source, ObjectNode target, String sourceField, String targetField) {
        JsonNode sourceNode = source.get(sourceField);
        JsonNode targetNode = target.get(targetField);
        if (!isBlankTextNode(sourceNode) && isBlankTextNode(targetNode)) {
            target.put(targetField, sourceNode.asText().trim());
        }
    }

    private void copyArrayIfMissing(ObjectNode source, ObjectNode target, String sourceField, String targetField) {
        JsonNode sourceNode = source.get(sourceField);
        JsonNode targetNode = target.get(targetField);
        if (sourceNode != null && sourceNode.isArray() && (targetNode == null || targetNode.isNull())) {
            target.set(targetField, sourceNode.deepCopy());
        }
    }

    private boolean isBlankTextNode(JsonNode node) {
        return node == null || node.isNull() || node.asText("").isBlank();
    }

    private void enrichWithGroundingMetadata(CompanyResearchResponse response, JsonNode root) {
        JsonNode metadata = root.path("candidates").path(0).path("groundingMetadata");
        if (metadata.isMissingNode()) {
            return;
        }

        JsonNode queriesNode = metadata.path("webSearchQueries");
        if (queriesNode.isArray()) {
            List<String> queries = new ArrayList<>();
            queriesNode.forEach(q -> {
                String query = q.asText("").trim();
                if (!query.isBlank()) {
                    queries.add(query);
                }
            });
            if (!queries.isEmpty()) {
                response.setSearchQueries(queries);
            }
        }

        JsonNode chunksNode = metadata.path("groundingChunks");
        if (chunksNode.isArray()) {
            List<CompanyResearchResponse.SearchSource> sources = new ArrayList<>();
            chunksNode.forEach(chunk -> {
                JsonNode web = chunk.path("web");
                String uri = web.path("uri").asText("").trim();
                String title = web.path("title").asText("").trim();
                if (!uri.isBlank()) {
                    sources.add(CompanyResearchResponse.SearchSource.builder()
                            .uri(uri)
                            .title(title.isBlank() ? uri : title)
                            .build());
                }
            });
            if (!sources.isEmpty()) {
                response.setSearchSources(sources);
            }
        }

        log.info("Grounding metadata: {} queries, {} sources",
                response.getSearchQueries() != null ? response.getSearchQueries().size() : 0,
                response.getSearchSources() != null ? response.getSearchSources().size() : 0);
    }

    private Map<String, Object> buildCompanyResearchResponseSchema() {
        Map<String, Object> focusSchema = objectSchema(
                Map.of(
                        "company", stringSchema("Target company name."),
                        "position", stringSchema("Target position name."),
                        "inferredBusinessUnit", stringSchema("Likely business unit."),
                        "inferredProduct", stringSchema("Likely product or service.")
                ),
                List.of("company", "position", "inferredBusinessUnit", "inferredProduct")
        );

        Map<String, Object> discoveredContextSchema = objectSchema(
                Map.of(
                        "businessUnit", stringSchema("Discovered or inferred business unit."),
                        "product", stringSchema("Discovered or inferred product."),
                        "evidenceSources", stringArraySchema("Evidence source summary.")
                ),
                List.of("businessUnit", "product", "evidenceSources")
        );

        Map<String, Object> techStackItemSchema = objectSchema(
                Map.of(
                        "name", stringSchema("Technology name, include version if known."),
                        "category", stringSchema("Backend, Frontend, Database, Infrastructure, DevOps, Mobile, or AI-ML."),
                        "confidence", stringSchema("CONFIRMED, INFERRED, or UNCERTAIN."),
                        "source", stringSchema("Evidence source.")
                ),
                List.of("name", "category", "confidence", "source")
        );

        Map<String, Object> techFactSchema = objectSchema(
                Map.of(
                        "summary", stringSchema("Short summary."),
                        "detail", stringSchema("Specific technical detail."),
                        "source", stringSchema("Evidence source.")
                ),
                List.of("summary", "detail", "source")
        );

        Map<String, Object> fitAnalysisSchema = objectSchema(
                Map.of(
                        "jdStatedRequirements", stringArraySchema("Requirement stated in JD."),
                        "actualTechStack", stringArraySchema("Likely actual tech stack."),
                        "gapAnalysis", stringSchema("Gap analysis in Korean."),
                        "coverLetterHints", stringArraySchema("Hints for the cover letter.")
                ),
                List.of("jdStatedRequirements", "actualTechStack", "gapAnalysis", "coverLetterHints")
        );

        Map<String, Object> searchSourceSchema = objectSchema(
                Map.of(
                        "title", stringSchema("Source title."),
                        "uri", stringSchema("Source URL.")
                ),
                List.of("title", "uri")
        );

        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("focus", focusSchema);
        properties.put("executiveSummary", stringSchema("One concise executive summary in Korean."));
        properties.put("discoveredContext", discoveredContextSchema);
        properties.put("businessContext", stringArraySchema("Business context bullet."));
        properties.put("serviceLandscape", stringArraySchema("Service landscape bullet."));
        properties.put("roleScope", stringArraySchema("Role scope bullet."));
        properties.put("techStack", arraySchema(techStackItemSchema, "Structured tech stack list."));
        properties.put("recentTechWork", arraySchema(techFactSchema, "Recent technical work list."));
        properties.put("fitAnalysis", fitAnalysisSchema);
        properties.put("motivationHooks", stringArraySchema("Motivation hook."));
        properties.put("serviceHooks", stringArraySchema("Service hook."));
        properties.put("resumeAngles", stringArraySchema("Resume angle."));
        properties.put("interviewSignals", stringArraySchema("Interview signal."));
        properties.put("recommendedNarrative", stringSchema("Recommended narrative in Korean."));
        properties.put("followUpQuestions", stringArraySchema("Follow-up question."));
        properties.put("confidenceNotes", stringArraySchema("Confidence note."));
        properties.put("searchQueries", stringArraySchema("Search query used by the model."));
        properties.put("searchSources", arraySchema(searchSourceSchema, "Grounding source list."));

        return objectSchema(
                properties,
                List.of(
                        "focus",
                        "executiveSummary",
                        "discoveredContext",
                        "businessContext",
                        "serviceLandscape",
                        "roleScope",
                        "techStack",
                        "recentTechWork",
                        "fitAnalysis",
                        "motivationHooks",
                        "serviceHooks",
                        "resumeAngles",
                        "interviewSignals",
                        "recommendedNarrative",
                        "followUpQuestions",
                        "confidenceNotes",
                        "searchQueries",
                        "searchSources"
                )
        );
    }

    private Map<String, Object> stringSchema(String description) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "string");
        schema.put("description", description);
        return schema;
    }

    private Map<String, Object> stringArraySchema(String itemDescription) {
        return arraySchema(stringSchema(itemDescription), itemDescription);
    }

    private Map<String, Object> arraySchema(Map<String, Object> itemSchema, String description) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "array");
        schema.put("description", description);
        schema.put("items", itemSchema);
        return schema;
    }

    private Map<String, Object> objectSchema(Map<String, Object> properties, List<String> required) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        schema.put("required", required);
        schema.put("additionalProperties", false);
        return schema;
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
            return "Gemini 2.5 계열에서는 검색 도구와 structured output 조합이 제한될 수 있습니다. 현재 코드는 검색 fallback 없이만 structured output을 사용합니다.";
        }

        return "Gemini request failed with status " + statusCode + ": " + responseBody;
    }

    private record RequestMode(String label, boolean useGoogleSearch, boolean useStructuredOutput, int maxAttempts) {
    }

    private static final class RetryableGeminiResponseException extends RuntimeException {
        private RetryableGeminiResponseException(String message) {
            super(message);
        }

        private RetryableGeminiResponseException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
