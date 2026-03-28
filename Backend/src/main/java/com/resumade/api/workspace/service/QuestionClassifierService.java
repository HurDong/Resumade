package com.resumade.api.workspace.service;

import com.resumade.api.workspace.prompt.QuestionCategory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 자기소개서 문항을 {@link QuestionCategory}로 분류하는 서비스.
 *
 * <h2>설계 원칙</h2>
 * <ul>
 *   <li>빠른 분류를 위해 소형 모델(gpt-4o-mini)을 사용합니다.</li>
 *   <li>분류 실패(예외, 파싱 오류) 시 반드시 {@link QuestionCategory#DEFAULT}로 fallback합니다.
 *       분류 실패가 전체 파이프라인을 중단시켜서는 안 됩니다.</li>
 *   <li>분류 결과를 로그에 남기되, 문항 내용 자체는 WARN 레벨 이상에서 노출하지 않습니다(PII 준수).</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QuestionClassifierService {

    private final ClassifierAiService classifierAiService;

    /**
     * 문항 제목을 분석하여 {@link QuestionCategory}를 반환합니다.
     *
     * @param questionTitle 분류할 문항 텍스트 (자소서 문항 제목)
     * @return 판별된 카테고리. 실패 시 {@link QuestionCategory#DEFAULT}
     */
    public QuestionCategory classify(String questionTitle) {
        if (questionTitle == null || questionTitle.isBlank()) {
            log.debug("QuestionClassifier: empty question title, returning DEFAULT");
            return QuestionCategory.DEFAULT;
        }

        try {
            String rawResult = classifierAiService.classify(questionTitle.trim());
            QuestionCategory category = QuestionCategory.fromString(rawResult);

            log.info("QuestionClassifier: category={} (raw=\"{}\") questionLength={}",
                    category,
                    rawResult != null ? rawResult.trim() : "null",
                    questionTitle.length());

            return category;

        } catch (Exception e) {
            // 분류 실패는 파이프라인 중단이 아닌 degraded mode로 처리
            log.warn("QuestionClassifier: classification failed, falling back to DEFAULT. reason={}",
                    e.getMessage());
            return QuestionCategory.DEFAULT;
        }
    }
}
