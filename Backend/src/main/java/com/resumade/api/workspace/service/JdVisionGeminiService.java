package com.resumade.api.workspace.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.resumade.api.workspace.dto.JdAnalysisResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class JdVisionGeminiService {

    private static final String SYSTEM_PROMPT = """
            당신은 채용 공고(JD) 분석 전문 AI 에이전트입니다.
            이미지에서 채용 공고 전체 내용을 정확하게 추출하여 JSON 객체로만 반환하세요.

            규칙:
            - companyName: 기업명 (없으면 null)
            - position: 지원 직무 (없으면 null)
            - rawJd: 이미지에서 추출한 공고 전체 텍스트 (최대한 완전하게)
            - aiInsight: 핵심 직무 역량·요구사항 요약 (2~3문장)
            - extractedQuestions: 자기소개서 문항 목록 (없으면 빈 배열 [])
            - 다른 설명 없이 JSON 객체만 반환하세요.

            반환 형식:
            {"companyName": "...", "position": "...", "rawJd": "...", "aiInsight": "...", "extractedQuestions": ["문항1", "문항2"]}
            """;

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(20))
            .build();

    @Value("${gemini.api.key:}")
    private String apiKey;

    @Value("${gemini.api.url:https://generativelanguage.googleapis.com/v1beta}")
    private String apiUrl;

    @Value("${gemini.models.jd-vision:gemini-2.5-flash-lite}")
    private String model;

    public JdAnalysisResponse analyzeFromImage(byte[] imageBytes, String mimeType) throws IOException, InterruptedException {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("GEMINI_API_KEY가 설정되지 않았습니다.");
        }

        String base64Image = Base64.getEncoder().encodeToString(imageBytes);
        String safeType = mimeType != null && !mimeType.isBlank() ? mimeType : "image/png";

        Map<String, Object> payload = Map.of(
                "system_instruction", Map.of(
                        "parts", List.of(Map.of("text", SYSTEM_PROMPT))
                ),
                "contents", List.of(
                        Map.of("parts", List.of(
                                Map.of("text", "이 채용 공고 이미지를 분석해주세요."),
                                Map.of("inline_data", Map.of(
                                        "mime_type", safeType,
                                        "data", base64Image
                                ))
                        ))
                ),
                "generationConfig", Map.of(
                        "temperature", 0.0,
                        "responseMimeType", "application/json"
                )
        );

        String encodedModel = URLEncoder.encode(model, StandardCharsets.UTF_8);
        String endpoint = apiUrl + "/models/" + encodedModel + ":generateContent?key=" + apiKey;

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .timeout(Duration.ofSeconds(60))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload), StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 400) {
            log.error("Gemini JD Vision API error: status={}, body={}", response.statusCode(), response.body());
            throw new IllegalStateException("Gemini Vision API 오류: " + response.statusCode());
        }

        JsonNode root = objectMapper.readTree(response.body());
        String jsonText = root.path("candidates").path(0)
                .path("content").path("parts").path(0)
                .path("text").asText("");

        if (jsonText.isBlank()) {
            log.error("Gemini JD Vision returned empty text: {}", response.body());
            throw new IllegalStateException("이미지에서 공고 내용을 추출하지 못했습니다. 이미지를 확인해 주세요.");
        }

        String cleaned = jsonText.strip();
        if (cleaned.startsWith("```")) {
            cleaned = cleaned.replaceFirst("^```[a-z]*\\n?", "").replaceFirst("```$", "").strip();
        }

        log.info("Gemini JD Vision parsed: {} characters", cleaned.length());
        return objectMapper.readValue(cleaned, JdAnalysisResponse.class);
    }
}
