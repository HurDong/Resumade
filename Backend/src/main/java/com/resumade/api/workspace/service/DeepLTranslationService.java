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
public class DeepLTranslationService implements TranslationProvider {

    @Value("${deepl.api.key:demo}")
    private String authKey;

    private final RestTemplate restTemplate = new RestTemplate();
    private static final String DEEPL_API_URL = "https://api-free.deepl.com/v2/translate";

    @Override
    public String getProviderKey() {
        return "deepl";
    }

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
            log.warn("[!! 번역/스킵] 제공자=DeepL | 사유=API키없음 | 처리=원문반환");
            return text;
        }

        if (text == null || text.isBlank()) {
            return text;
        }

        try {
            return requestDeepL(text, targetLang);
        } catch (Exception e) {
            log.error("[!! 번역/실패] 제공자=DeepL | 단계=래퍼처리 | 처리=원문반환", e);
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
            map.add("preserve_formatting", "1");

            HttpEntity<org.springframework.util.MultiValueMap<String, String>> entity = new HttpEntity<>(map, headers);

            log.info("[▶ 번역/요청] 제공자=DeepL | 방향={} | 입력={}자", targetLang, text.length());
            Map<String, Object> response = restTemplate.postForObject(DEEPL_API_URL, entity, Map.class);
            
            if (response != null && response.containsKey("translations")) {
                List<Map<String, String>> translations = (List<Map<String, String>>) response.get("translations");
                String translated = translations.get(0).get("text");
                log.info("[■ 번역/완료] 제공자=DeepL | 방향={} | 출력={}자", targetLang, translated == null ? 0 : translated.length());
                return translated;
            }
        } catch (org.springframework.web.client.HttpClientErrorException.Forbidden e) {
            log.error("[!! 번역/권한오류] 제공자=DeepL | 상태=403 | 조치=API키확인 | keySuffix={}",
                authKey.length() > 3 ? authKey.substring(authKey.length() - 3) : "too-short");
        } catch (Exception e) {
            log.error("[!! 번역/실패] 제공자=DeepL | 단계=단일요청 | 처리=원문반환", e);
        }
        return text;
    }
}
