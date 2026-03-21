package com.resumade.api.workspace.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;

@Slf4j
@Service
@Primary
@RequiredArgsConstructor
public class RoutingTranslationService implements TranslationService {

    private final List<TranslationProvider> providers;

    @Value("${translation.provider:deepl}")
    private String providerKey;

    @Value("${translation.fallback-provider:}")
    private String fallbackProviderKey;

    @Override
    public String translateToEnglish(String text) {
        return resolveProvider().translateToEnglish(text);
    }

    @Override
    public String translateToKorean(String text) {
        return resolveProvider().translateToKorean(text);
    }

    private TranslationProvider resolveProvider() {
        TranslationProvider primary = findProvider(providerKey);
        if (primary != null) {
            return primary;
        }

        TranslationProvider fallback = findProvider(fallbackProviderKey);
        if (fallback != null) {
            log.warn("Translation provider '{}' is unavailable. Falling back to '{}'.",
                    providerKey, fallback.getProviderKey());
            return fallback;
        }

        throw new IllegalStateException("No translation provider available for key: " + providerKey);
    }

    private TranslationProvider findProvider(String key) {
        if (key == null || key.isBlank()) {
            return null;
        }

        String normalized = key.trim().toLowerCase(Locale.ROOT);
        return providers.stream()
                .filter(provider -> provider.getProviderKey().equalsIgnoreCase(normalized))
                .findFirst()
                .orElse(null);
    }
}
