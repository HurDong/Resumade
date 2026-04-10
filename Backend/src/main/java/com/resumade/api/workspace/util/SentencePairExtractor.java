package com.resumade.api.workspace.util;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 원본 초안과 세탁본을 문장 단위로 분리해 1:1 페어로 묶는 유틸리티.
 * <p>
 * 분리 기준: {@code .} 뒤에 닫는 따옴표류가 오지 않는 경우만 문장 경계로 처리.
 * <ul>
 *   <li>정상: "개발하였습니다." → 분리</li>
 *   <li>예외: "'안주하자.'라는" → 분리 안 함</li>
 * </ul>
 * 문장 수 불일치 시 더 짧은 쪽 기준으로 맞추되, 남은 문장은 마지막 페어에 병합.
 */
public class SentencePairExtractor {

    /**
     * {@code .} 뒤에 닫는 따옴표류가 오면 문장 경계로 보지 않음.
     * 처리 대상: ' (ASCII), ' (U+2018), ' (U+2019), " (U+201C), " (U+201D), 」(U+300D), 』(U+300F)
     */
    private static final Pattern SENTENCE_BOUNDARY = Pattern.compile(
            "\\.(?!['\u2018\u2019\u201C\u201D\u300D\u300F])"
    );

    public record SentencePair(String original, String washed) {}

    public static List<SentencePair> extract(String originalDraft, String washedKr) {
        List<String> origSentences  = splitSentences(originalDraft);
        List<String> washedSentences = splitSentences(washedKr);

        List<SentencePair> pairs = new ArrayList<>();
        int minSize = Math.min(origSentences.size(), washedSentences.size());
        if (minSize == 0) return pairs;

        for (int i = 0; i < minSize; i++) {
            String orig;
            String washed;

            if (i == minSize - 1) {
                // 마지막 페어: 남은 문장 전부 병합
                orig  = String.join(" ", origSentences.subList(i, origSentences.size())).trim();
                washed = String.join(" ", washedSentences.subList(i, washedSentences.size())).trim();
            } else {
                orig  = origSentences.get(i).trim();
                washed = washedSentences.get(i).trim();
            }

            if (!orig.isBlank() && !washed.isBlank()) {
                pairs.add(new SentencePair(orig, washed));
            }
        }

        return pairs;
    }

    private static List<String> splitSentences(String text) {
        if (text == null || text.isBlank()) return List.of();

        String[] parts = SENTENCE_BOUNDARY.split(text);
        List<String> sentences = new ArrayList<>();
        for (String part : parts) {
            String trimmed = part.trim();
            if (!trimmed.isBlank()) {
                sentences.add(trimmed);
            }
        }

        if (sentences.isEmpty()) {
            sentences.add(text.trim());
        }
        return sentences;
    }
}
