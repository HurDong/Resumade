package com.resumade.api.workspace.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.resumade.api.workspace.dto.CompanyResearchResponse;
import lombok.RequiredArgsConstructor;
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
import java.util.List;
import java.util.Map;

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

    @Value("${gemini.models.company-research:gemini-3.1-pro}")
    private String modelName;

    public CompanyResearchResponse compose(
            String company,
            String position,
            String businessUnit,
            String targetService,
            String focusRole,
            String techFocus,
            String questionGoal,
            String rawJd
    ) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("GEMINI_API_KEY is missing. Add it to the server environment before using company research.");
        }

        String systemPrompt = """
                You are a senior Korean company and hiring analyst for self-introduction writing.
                Analyze the supplied company, role, service, and JD context and convert it into a concise JSON object for job applicants.
                Do not write a generic company introduction.
                Focus on the specific business unit, service or product, role scope, technical expectations, and applicant hooks.
                Use Google Search grounding when helpful for recent public company information.
                Separate reasonably grounded signals from inference through confidenceNotes.
                All natural-language fields must be written in Korean.
                Return JSON only with this exact schema:
                {"focus":{"company":"...","position":"...","businessUnit":"...","targetService":"...","focusRole":"...","techFocus":"...","questionGoal":"..."},
                "executiveSummary":"...",
                "businessContext":["..."],
                "serviceLandscape":["..."],
                "roleScope":["..."],
                "techSignals":["..."],
                "motivationHooks":["..."],
                "serviceHooks":["..."],
                "resumeAngles":["..."],
                "interviewSignals":["..."],
                "recommendedNarrative":"...",
                "followUpQuestions":["..."],
                "confidenceNotes":["..."]}
                """;

        String userPrompt = """
                Company: %s
                Position: %s
                Business unit focus: %s
                Service or product focus: %s
                Role focus: %s
                Tech focus: %s
                Analysis goal: %s

                JD:
                %s
                """.formatted(
                company,
                position,
                businessUnit,
                targetService,
                focusRole,
                techFocus,
                questionGoal,
                rawJd
        );

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
                            "temperature", 0.2,
                            "responseMimeType", "application/json"
                    )
            ));

            String encodedModel = URLEncoder.encode(modelName, StandardCharsets.UTF_8);
            String endpoint = apiUrl + "/models/" + encodedModel + ":generateContent?key=" + apiKey;

            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint))
                    .timeout(Duration.ofSeconds(90))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                throw new IllegalStateException(mapGeminiError(response.statusCode(), response.body()));
            }

            JsonNode root = objectMapper.readTree(response.body());
            String text = root.path("candidates").path(0).path("content").path("parts").path(0).path("text").asText("");
            if (text.isBlank()) {
                throw new IllegalStateException("Gemini returned an empty company research response.");
            }

            return objectMapper.readValue(text, CompanyResearchResponse.class);
        } catch (IOException e) {
            throw new IllegalStateException("Gemini company research request failed: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Gemini company research request was interrupted.", e);
        }
    }

    private String mapGeminiError(int statusCode, String responseBody) {
        String lowerBody = responseBody == null ? "" : responseBody.toLowerCase();

        if (lowerBody.contains("api key not valid") || lowerBody.contains("api_key_invalid")) {
            return "\uac00\uc838\uc628 Gemini API \ud0a4\uac00 \uc720\ud6a8\ud558\uc9c0 \uc54a\uc2b5\ub2c8\ub2e4. .env\uc758 GEMINI_API_KEY \uac12\uc744 \ub2e4\uc2dc \ud655\uc778\ud574\uc8fc\uc138\uc694.";
        }

        if (lowerBody.contains("quota") || lowerBody.contains("resource_exhausted")) {
            return "Gemini API \ud560\ub2f9\ub7c9\uc744 \ucd08\uacfc\ud588\uc2b5\ub2c8\ub2e4. Google AI Studio \ub610\ub294 Google Cloud Billing/\ucffc\ud130 \uc124\uc815\uc744 \ud655\uc778\ud574\uc8fc\uc138\uc694.";
        }

        if (lowerBody.contains("billing")) {
            return "Gemini API \ube4c\ub9c1 \uc124\uc815\uc774 \ud544\uc694\ud569\ub2c8\ub2e4. Google AI Studio \ub610\ub294 Google Cloud\uc5d0\uc11c \ube4c\ub9c1 \uc0c1\ud0dc\ub97c \ud655\uc778\ud574\uc8fc\uc138\uc694.";
        }

        return "Gemini request failed with status " + statusCode + ": " + responseBody;
    }
}
