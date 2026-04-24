package com.resumade.api.workspace.dto;

import java.util.List;

/**
 * DraftQualityCheckService의 2-Tier 검수 결과.
 *
 * <p>Tier-1(서버사이드 글자수) + Tier-2(LLM requiredElements 충족 검사)를 통합합니다.
 *
 * @param passed          두 Tier 모두 통과하면 true
 * @param lengthOk        현재 글자수 >= minTarget이면 true
 * @param elementsOk      requiredElements가 모두 충분히 다뤄지면 true (elements 없으면 true)
 * @param retryDirective  실패 시 LLM refine에 전달할 구체적 피드백. passed=true면 null
 */
public record DraftQualityResult(
        boolean passed,
        boolean lengthOk,
        boolean elementsOk,
        String retryDirective
) {
    public static DraftQualityResult ok() {
        return new DraftQualityResult(true, true, true, null);
    }

    public static DraftQualityResult lengthFail(int currentChars, int minTarget) {
        String directive = "현재 답변이 %d자입니다. 최소 %d자 이상이 되도록 본론의 근거·맥락을 보강하세요. 새로운 사실을 추가하되, 이미 쓴 내용을 단순 반복하지 마세요."
                .formatted(currentChars, minTarget);
        return new DraftQualityResult(false, false, true, directive);
    }

    public static DraftQualityResult elementsFail(List<String> missingElements, String rawLlmFeedback) {
        String directive = "다음 필수 요소가 충분히 다뤄지지 않았습니다:\n"
                + String.join("\n", missingElements.stream().map(e -> "- " + e).toList())
                + "\n\n각 요소를 기존 서사 흐름 안에 자연스럽게 통합하여 보강하세요. 항목별 단락 분리 금지."
                + (rawLlmFeedback != null && !rawLlmFeedback.isBlank() ? "\n\n추가 피드백: " + rawLlmFeedback : "");
        return new DraftQualityResult(false, true, false, directive);
    }

    public static DraftQualityResult categoryFail(String directive) {
        return new DraftQualityResult(false, true, false, directive);
    }
}
