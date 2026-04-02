package com.resumade.api.workspace.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
public class QuestionImageParserService {

    private static final String PARSE_MODEL = "gemini-2.5-flash-lite";
    private static final String SYSTEM_PROMPT = """
            당신은 자기소개서 문항 추출 전문가입니다.
            이미지에 보이는 자기소개서 문항을 빠짐없이 정확하게 추출하여 JSON 배열로만 반환하세요.

            규칙:
            - 각 문항은 {"title": "문항 내용 전체", "maxLength": 글자수 제한} 형태입니다.
            - 글자수 제한은 숫자만 반환합니다. '1000자', '(1000자)' → 1000
            - 글자수 제한이 없거나 '제한 없음' → null
            - 문항 번호(1., 2. 등)는 title에 포함하지 않습니다.
            - 다른 설명 없이 JSON 배열만 반환하세요.

            반환 형식 예시:
            [
              {"title": "지원동기와 해당 직무에서 이루고자 하는 목표를 구체적으로 작성해 주세요.", "maxLength": 1000},
              {"title": "본인의 강점과 이를 발휘한 경험을 기술해 주세요.", "maxLength": 700},
              {"title": "팀 프로젝트 경험과 본인의 역할을 작성해 주세요.", "maxLength": null}
            ]
            """;

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(20))
            .build();

    @Value("${gemini.api.key:}")
    private String apiKey;

    @Value("${gemini.api.url:https://generativelanguage.googleapis.com/v1beta}")
    private String apiUrl;

    public record ParsedQuestion(String title, Integer maxLength) {}

    public List<ParsedQuestion> parseFromImage(byte[] imageBytes, String mimeType) throws IOException, InterruptedException {
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
                                Map.of("text", "이 이미지에서 자기소개서 문항을 추출해주세요."),
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

        String encodedModel = URLEncoder.encode(PARSE_MODEL, StandardCharsets.UTF_8);
        String endpoint = apiUrl + "/models/" + encodedModel + ":generateContent?key=" + apiKey;

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .timeout(Duration.ofSeconds(60))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload), StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 400) {
            log.error("Gemini Vision API error: status={}, body={}", response.statusCode(), response.body());
            throw new IllegalStateException("Gemini Vision API 오류: " + response.statusCode());
        }

        JsonNode root = objectMapper.readTree(response.body());
        String jsonText = root.path("candidates").path(0)
                .path("content").path("parts").path(0)
                .path("text").asText("");

        if (jsonText.isBlank()) {
            log.error("Gemini Vision returned empty text: {}", response.body());
            throw new IllegalStateException("이미지에서 문항을 추출하지 못했습니다. 스크린샷을 확인해 주세요.");
        }

        // Strip markdown code block if present
        String cleaned = jsonText.strip();
        if (cleaned.startsWith("```")) {
            cleaned = cleaned.replaceFirst("^```[a-z]*\\n?", "").replaceFirst("```$", "").strip();
        }

        log.info("Gemini Vision parsed {} characters of JSON", cleaned.length());
        return objectMapper.readValue(cleaned, new TypeReference<>() {});
    }
}
