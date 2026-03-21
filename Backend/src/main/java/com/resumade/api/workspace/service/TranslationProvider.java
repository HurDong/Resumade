package com.resumade.api.workspace.service;

public interface TranslationProvider {
    String getProviderKey();

    String translateToEnglish(String text);

    String translateToKorean(String text);
}
