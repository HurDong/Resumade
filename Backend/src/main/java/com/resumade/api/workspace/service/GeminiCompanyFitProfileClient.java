package com.resumade.api.workspace.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.resumade.api.workspace.dto.CompanyFitProfileDto;
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
public class GeminiCompanyFitProfileClient {

    private static final int DISABLED_THINKING_BUDGET = 0;
    private static final int MAX_OUTPUT_TOKENS = 12000;
    private static final int MAX_PARSE_ATTEMPTS = 2;
    private static final double DEFAULT_TEMPERATURE = 0.1;

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(20))
            .build();

    @Value("${gemini.api.key:}")
    private String apiKey;

    @Value("${gemini.api.url:https://generativelanguage.googleapis.com/v1beta}")
    private String apiUrl;

    @Value("${gemini.models.company-fit-profile:${gemini.models.company-research:gemini-2.5-flash-lite}}")
    private String modelName;

    public GenerationResult generate(
            String company,
            String position,
            String rawJd,
            String aiInsight,
            String additionalFocus
    ) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("GEMINI_API_KEY is missing. Add it before generating a company fit profile.");
        }

        try {
            JsonNode root = null;
            CompanyFitProfileDto profile = null;
            IOException lastParseError = null;

            for (int attempt = 1; attempt <= MAX_PARSE_ATTEMPTS; attempt++) {
                root = sendGenerateContent(
                        buildSystemPrompt(),
                        buildUserPrompt(company, position, rawJd, aiInsight, additionalFocus)
                );
                String responseText = extractResponseText(root);
                try {
                    profile = parseProfile(responseText);
                    break;
                } catch (IOException e) {
                    lastParseError = e;
                    String finishReason = root.path("candidates").path(0).path("finishReason").asText("UNKNOWN");
                    log.warn("Gemini company fit profile JSON parse failed: attempt={}/{}, finishReason={}, textLength={}, reason={}",
                            attempt, MAX_PARSE_ATTEMPTS, finishReason, responseText.length(), e.getMessage());
                }
            }

            if (profile == null) {
                if (lastParseError != null) {
                    throw lastParseError;
                }
                throw new IllegalStateException("Gemini returned an invalid fit profile.");
            }

            enrichWithGroundingMetadata(profile, root);
            normalizeConfidence(profile);

            String groundingStatus = hasGroundingSources(profile) ? "GROUNDED" : "JD_ONLY";
            if ("JD_ONLY".equals(groundingStatus)) {
                addConfidenceNote(profile, "Search grounding sources were not returned. Treat non-JD conclusions as UNCERTAIN.");
            }

            return new GenerationResult(profile, modelName, groundingStatus);
        } catch (IOException e) {
            throw new IllegalStateException("Gemini company fit profile request failed: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Gemini company fit profile request was interrupted.", e);
        }
    }

    private JsonNode sendGenerateContent(String systemPrompt, String userPrompt)
            throws IOException, InterruptedException {
        String requestBody = objectMapper.writeValueAsString(buildRequestPayload(systemPrompt, userPrompt));
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
            throw new IllegalStateException("Gemini request failed with status " + response.statusCode() + ": " + response.body());
        }

        return objectMapper.readTree(response.body());
    }

    private Map<String, Object> buildRequestPayload(String systemPrompt, String userPrompt) {
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
        payload.put("tools", List.of(Map.of("google_search", Map.of())));
        payload.put("generationConfig", Map.of(
                "temperature", DEFAULT_TEMPERATURE,
                "maxOutputTokens", MAX_OUTPUT_TOKENS,
                "thinkingConfig", Map.of("thinkingBudget", DISABLED_THINKING_BUDGET)
        ));
        return payload;
    }

    private String buildSystemPrompt() {
        return """
                You are a senior Korean cover-letter strategy researcher.
                Build a company fit profile for one job application. Use Google Search grounding when available.

                Return JSON only. No markdown fences, no prose outside JSON.
                All natural-language values must be written in Korean.
                Do not invent facts. If a conclusion is inferred, mark confidence as INFERRED or UNCERTAIN.
                Use these confidence values only: CONFIRMED, INFERRED, UNCERTAIN.
                Keep output compact enough to finish as valid JSON:
                - summary: max 2 Korean sentences.
                - businessAgenda, roleMission, hiringSignals, workStyle, strategyWarnings: max 3 items each.
                - domainLexicon: max 6 items.
                - evidence: max 8 items.
                - Each detail/usage/summary: max 160 Korean characters.

                Evidence rules:
                - Prefer official recruiting pages, official company/service pages, official engineering blogs, recent news, and employee interviews.
                - Every item in businessAgenda, roleMission, hiringSignals, workStyle, domainLexicon, and strategyWarnings must include evidenceIds.
                - If only the JD supports an item, use evidence id "jd".

                Output shape:
                {
                  "focus": {
                    "company": "string",
                    "position": "string",
                    "inferredBusinessUnit": "string",
                    "inferredProduct": "string"
                  },
                  "summary": "string",
                  "businessAgenda": [
                    {"title":"string","detail":"string","confidence":"CONFIRMED|INFERRED|UNCERTAIN","evidenceIds":["string"]}
                  ],
                  "roleMission": [
                    {"title":"string","detail":"string","confidence":"CONFIRMED|INFERRED|UNCERTAIN","evidenceIds":["string"]}
                  ],
                  "hiringSignals": [
                    {"title":"string","detail":"string","confidence":"CONFIRMED|INFERRED|UNCERTAIN","evidenceIds":["string"]}
                  ],
                  "workStyle": [
                    {"title":"string","detail":"string","confidence":"CONFIRMED|INFERRED|UNCERTAIN","evidenceIds":["string"]}
                  ],
                  "domainLexicon": [
                    {"term":"string","usage":"string","confidence":"CONFIRMED|INFERRED|UNCERTAIN","evidenceIds":["string"]}
                  ],
                  "strategyWarnings": [
                    {"title":"string","detail":"string","confidence":"CONFIRMED|INFERRED|UNCERTAIN","evidenceIds":["string"]}
                  ],
                  "evidence": [
                    {"id":"string","type":"JD|OFFICIAL|BLOG|NEWS|INTERVIEW|SEARCH","title":"string","uri":"string","summary":"string","publishedAt":"string"}
                  ],
                  "confidenceNotes": ["string"]
                }
                """;
    }

    private String buildUserPrompt(
            String company,
            String position,
            String rawJd,
            String aiInsight,
            String additionalFocus
    ) {
        StringBuilder sb = new StringBuilder();
        if (additionalFocus != null && !additionalFocus.isBlank()) {
            sb.append("[Additional focus]\n").append(additionalFocus.trim()).append("\n\n");
        }
        sb.append("Company: ").append(nullToEmpty(company)).append("\n");
        sb.append("Position: ").append(nullToEmpty(position)).append("\n\n");
        sb.append("[Existing JD insight]\n").append(blankToNone(aiInsight)).append("\n\n");
        sb.append("[Raw JD]\n").append(blankToNone(rawJd));
        return sb.toString();
    }

    private String extractResponseText(JsonNode root) {
        JsonNode candidates = root.path("candidates");
        if (!candidates.isArray() || candidates.isEmpty()) {
            throw new IllegalStateException("Gemini response has no candidates.");
        }

        JsonNode parts = candidates.path(0).path("content").path("parts");
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

    private CompanyFitProfileDto parseProfile(String text) throws IOException {
        if (text == null || text.isBlank()) {
            throw new IllegalStateException("Gemini returned an empty fit profile.");
        }

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

        return objectMapper.readValue(normalized, CompanyFitProfileDto.class);
    }

    private void enrichWithGroundingMetadata(CompanyFitProfileDto profile, JsonNode root) {
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
                profile.setSearchQueries(queries);
            }
        }

        JsonNode chunksNode = metadata.path("groundingChunks");
        if (chunksNode.isArray()) {
            List<CompanyFitProfileDto.SearchSource> sources = new ArrayList<>();
            chunksNode.forEach(chunk -> {
                JsonNode web = chunk.path("web");
                String uri = web.path("uri").asText("").trim();
                String title = web.path("title").asText("").trim();
                if (!uri.isBlank()) {
                    sources.add(CompanyFitProfileDto.SearchSource.builder()
                            .uri(uri)
                            .title(title.isBlank() ? uri : title)
                            .build());
                }
            });
            if (!sources.isEmpty()) {
                profile.setSearchSources(sources);
            }
        }
    }

    private void normalizeConfidence(CompanyFitProfileDto profile) {
        normalizeItems(profile.getBusinessAgenda());
        normalizeItems(profile.getRoleMission());
        normalizeItems(profile.getHiringSignals());
        normalizeItems(profile.getWorkStyle());
        normalizeItems(profile.getStrategyWarnings());
        if (profile.getDomainLexicon() != null) {
            profile.getDomainLexicon().forEach(item -> {
                if (!isValidConfidence(item.getConfidence())) {
                    item.setConfidence("UNCERTAIN");
                }
            });
        }
    }

    private void normalizeItems(List<CompanyFitProfileDto.ProfileItem> items) {
        if (items == null) {
            return;
        }
        items.forEach(item -> {
            if (!isValidConfidence(item.getConfidence())) {
                item.setConfidence("UNCERTAIN");
            }
        });
    }

    private boolean isValidConfidence(String confidence) {
        return "CONFIRMED".equals(confidence)
                || "INFERRED".equals(confidence)
                || "UNCERTAIN".equals(confidence);
    }

    private boolean hasGroundingSources(CompanyFitProfileDto profile) {
        return profile.getSearchSources() != null && !profile.getSearchSources().isEmpty();
    }

    private void addConfidenceNote(CompanyFitProfileDto profile, String note) {
        List<String> notes = profile.getConfidenceNotes() == null
                ? new ArrayList<>()
                : new ArrayList<>(profile.getConfidenceNotes());
        if (!notes.contains(note)) {
            notes.add(0, note);
        }
        profile.setConfidenceNotes(notes);
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private String blankToNone(String value) {
        return value == null || value.isBlank() ? "None" : value;
    }

    public record GenerationResult(
            CompanyFitProfileDto profile,
            String modelName,
            String groundingStatus
    ) {
    }
}
