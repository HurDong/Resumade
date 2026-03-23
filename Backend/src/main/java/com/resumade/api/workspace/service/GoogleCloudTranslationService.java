package com.resumade.api.workspace.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.HtmlUtils;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class GoogleCloudTranslationService implements TranslationProvider {

    private static final String GOOGLE_TRANSLATE_API_URL = "https://translation.googleapis.com/language/translate/v2";

    @Value("${translation.google-cloud.api-key:${GOOGLE_CLOUD_TRANSLATE_API_KEY:${GEMINI_API_KEY:}}}")
    private String apiKey;

    private final RestTemplate restTemplate = new RestTemplate();

    @Override
    public String getProviderKey() {
        return "google-cloud";
    }

    @Override
    public String translateToEnglish(String text) {
        return translate(text, "en");
    }

    @Override
    public String translateToKorean(String text) {
        return translate(text, "ko");
    }

    private String translate(String text, String targetLanguage) {
        if (text == null || text.isBlank()) {
            return text;
        }

        if (apiKey == null || apiKey.isBlank()) {
            log.warn("Google Cloud Translation API key is missing. Returning original text.");
            return text;
        }

        try {
            return requestGoogleTranslation(text, targetLanguage);
        } catch (Exception e) {
            log.error("Google Cloud Translation failed", e);
            return text;
        }
    }

    @SuppressWarnings("unchecked")
    private String requestGoogleTranslation(String text, String targetLanguage) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> body = Map.of(
                "q", text,
                "target", targetLanguage,
                "format", "text");

        String requestUrl = GOOGLE_TRANSLATE_API_URL + "?key=" + apiKey.trim();
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

        log.info("🌐 번역 요청 | 단계={} | 상태=요청 시작 | 초안군=- | 시도=- | 글자수={}자 | 목표=- | 제한=- | 다음=Google Cloud 호출 | 제공자=google-cloud",
                resolveTranslationStage(targetLanguage),
                text.length());
        Map<String, Object> response = restTemplate.postForObject(requestUrl, entity, Map.class);
        if (response == null) {
            return text;
        }

        Object dataObject = response.get("data");
        if (!(dataObject instanceof Map<?, ?> data)) {
            return text;
        }

        Object translationsObject = data.get("translations");
        if (!(translationsObject instanceof List<?> translations) || translations.isEmpty()) {
            return text;
        }

        Object firstObject = translations.get(0);
        if (!(firstObject instanceof Map<?, ?> firstTranslation)) {
            return text;
        }

        Object translatedText = firstTranslation.get("translatedText");
        if (!(translatedText instanceof String translated)) {
            return text;
        }

        return HtmlUtils.htmlUnescape(translated);
    }

    private String resolveTranslationStage(String targetLanguage) {
        if ("en".equalsIgnoreCase(targetLanguage)) {
            return "세탁 번역(한->영)";
        }
        if ("ko".equalsIgnoreCase(targetLanguage)) {
            return "세탁 번역(영->한)";
        }
        return "번역";
    }
}
