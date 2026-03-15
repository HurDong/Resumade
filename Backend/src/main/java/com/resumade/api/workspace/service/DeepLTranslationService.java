package com.resumade.api.workspace.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeepLTranslationService implements TranslationService {

    @Value("${deepl.api.key:demo}")
    private String authKey;

    private final RestTemplate restTemplate = new RestTemplate();
    private static final String DEEPL_API_URL = "https://api-free.deepl.com/v2/translate";

    @Override
    public String translateToEnglish(String text) {
        return translate(text, "EN");
    }

    @Override
    public String translateToKorean(String text) {
        // Fallback or explicit mapping for Papago if key is not provided?
        // For now, let's just use DeepL for both if possible.
        return translate(text, "KO");
    }

    private String translate(String text, String targetLang) {
        if (authKey == null || authKey.equals("demo") || authKey.isBlank()) {
            log.warn("DeepL API key is missing or is 'demo'. Returning original text.");
            return text;
        }

        if (text == null || text.isBlank()) {
            return text;
        }

        try {
            // Split by newlines while preserving formatting
            String[] lines = text.split("\n", -1);
            StringBuilder result = new StringBuilder();

            for (int i = 0; i < lines.length; i++) {
                String line = lines[i];
                if (!line.isBlank()) {
                    result.append(requestDeepL(line, targetLang));
                } else {
                    result.append(line);
                }

                if (i < lines.length - 1) {
                    result.append("\n");
                }
            }
            return result.toString();
        } catch (Exception e) {
            log.error("DeepL translation wrapper failed", e);
            return text;
        }
    }

    private String requestDeepL(String text, String targetLang) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
            headers.set("Authorization", "DeepL-Auth-Key " + authKey.trim());

            org.springframework.util.MultiValueMap<String, String> map = new org.springframework.util.LinkedMultiValueMap<>();
            map.add("text", text);
            map.add("target_lang", targetLang.toUpperCase());

            HttpEntity<org.springframework.util.MultiValueMap<String, String>> entity = new HttpEntity<>(map, headers);

            log.info("Requesting DeepL translation (target: {}, text length: {})", targetLang, text.length());
            Map<String, Object> response = restTemplate.postForObject(DEEPL_API_URL, entity, Map.class);
            
            if (response != null && response.containsKey("translations")) {
                List<Map<String, String>> translations = (List<Map<String, String>>) response.get("translations");
                return translations.get(0).get("text");
            }
        } catch (org.springframework.web.client.HttpClientErrorException.Forbidden e) {
            log.error("DeepL 403 Forbidden. Is the key valid? Key suffix: {}", 
                authKey.length() > 3 ? authKey.substring(authKey.length() - 3) : "too-short");
        } catch (Exception e) {
            log.error("DeepL single segment translation failed", e);
        }
        return text;
    }
}
