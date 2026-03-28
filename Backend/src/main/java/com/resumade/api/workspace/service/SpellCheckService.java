package com.resumade.api.workspace.service;

import com.resumade.api.workspace.dto.SpellCheckResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 한국어 맞춤법 검사 서비스.
 *
 * <p>{@link SpellCheckAiService}의 래퍼 역할을 담당하며,
 * LLM 호출 실패 시 빈 제안 목록으로 안전하게 폴백(Graceful Degradation)한다.</p>
 *
 * <p><b>입력 제약</b>
 * <ul>
 *   <li>텍스트가 null 이거나 공백뿐이면 LLM 을 호출하지 않고 즉시 빈 응답을 반환한다.</li>
 *   <li>텍스트 길이가 {@code MAX_TEXT_LENGTH}를 초과하면 LLM 을 호출하지 않는다.
 *       자기소개서 한 문항 최대 글자 수(통상 1,500자)를 고려한 상한이다.</li>
 * </ul>
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SpellCheckService {

    /** 단일 호출 텍스트 상한 (자기소개서 1문항 최대치 고려) */
    private static final int MAX_TEXT_LENGTH = 3_000;

    private final SpellCheckAiService spellCheckAiService;

    /**
     * 주어진 텍스트의 맞춤법·띄어쓰기 오류를 감지하여 제안 목록을 반환한다.
     *
     * @param text 검사 대상 원문 텍스트
     * @return 오류 제안 목록 (오류가 없거나 호출 실패 시 빈 리스트)
     */
    public SpellCheckResponse check(String text) {
        if (text == null || text.isBlank()) {
            return SpellCheckResponse.empty();
        }

        if (text.length() > MAX_TEXT_LENGTH) {
            log.warn("SpellCheck skipped: text exceeds max length ({} chars)", text.length());
            return SpellCheckResponse.empty();
        }

        try {
            SpellCheckResponse response = spellCheckAiService.check(text);

            // LLM 이 null 또는 비어있는 응답을 반환한 경우 방어 처리
            if (response == null || response.corrections() == null) {
                return SpellCheckResponse.empty();
            }

            log.info("SpellCheck completed: corrections={}", response.corrections().size());
            return response;

        } catch (Exception e) {
            // LLM 호출 실패는 기능 오류가 아닌 서비스 저하(Degradation)로 처리
            log.error("SpellCheck AI call failed, returning empty result", e);
            return SpellCheckResponse.empty();
        }
    }
}
