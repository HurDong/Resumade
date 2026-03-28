package com.resumade.api.workspace.prompt;

import com.resumade.api.workspace.prompt.strategy.DefaultPromptStrategy;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 문항 카테고리에 따른 프롬프트 전략을 선택하고,
 * LLM에 전달할 최종 {@code List<ChatMessage>}를 조립하는 Factory.
 *
 * <h2>메시지 조립 순서</h2>
 * <pre>
 * 1. [system]    전략별 카테고리 특화 시스템 프롬프트 (XML 구조 포함)
 * 2. [user]      Few-shot 예시의 사용자 메시지 (있을 경우)
 * 3. [assistant] Few-shot 예시의 어시스턴트 응답 (있을 경우)
 *    ... (쌍 반복)
 * 4. [user]      실제 생성 요청 메시지 (<Context>, <Strict_Rules>, <Output_Format> 태그 포함)
 * </pre>
 *
 * <h2>Spring 연동</h2>
 * {@link PromptStrategy} 구현체들을 {@code List} 주입으로 받아 자동 등록합니다.
 * 새 카테고리 전략을 추가할 때 이 클래스를 수정할 필요가 없습니다.
 */
@Slf4j
@Component
public class PromptFactory {

    private final Map<QuestionCategory, PromptStrategy> strategyMap;
    private final PromptStrategy defaultStrategy;

    /**
     * Spring이 {@code @Component}로 등록된 모든 {@link PromptStrategy} 구현체를 주입합니다.
     */
    public PromptFactory(List<PromptStrategy> strategies) {
        this.strategyMap = new EnumMap<>(QuestionCategory.class);
        PromptStrategy fallback = null;

        for (PromptStrategy strategy : strategies) {
            strategyMap.put(strategy.getCategory(), strategy);
            if (strategy.getCategory() == QuestionCategory.DEFAULT) {
                fallback = strategy;
            }
        }

        if (fallback == null) {
            // DEFAULT 전략이 등록되지 않은 경우 안전망
            fallback = new DefaultPromptStrategy();
            strategyMap.put(QuestionCategory.DEFAULT, fallback);
            log.warn("DefaultPromptStrategy was not found in Spring context — using inline fallback.");
        }
        this.defaultStrategy = fallback;

        log.info("PromptFactory initialized with {} strategies: {}",
                strategyMap.size(), strategyMap.keySet());
    }

    /**
     * 카테고리에 맞는 전략을 반환합니다.
     * 매핑된 전략이 없으면 DEFAULT 전략을 반환합니다.
     */
    public PromptStrategy getStrategy(QuestionCategory category) {
        if (category == null) {
            return defaultStrategy;
        }
        return strategyMap.getOrDefault(category, defaultStrategy);
    }

    /**
     * 주어진 카테고리와 파라미터로 LLM에 전달할 {@code List<ChatMessage>}를 조립합니다.
     *
     * <p>조립 구조:
     * <pre>
     * SystemMessage  (전략별 카테고리 특화 시스템 프롬프트)
     * UserMessage    (few-shot 예시 user — 있을 경우)
     * AiMessage      (few-shot 예시 assistant — 있을 경우)
     * ...반복...
     * UserMessage    (실제 생성 요청)
     * </pre>
     *
     * @param category 분류기가 판별한 문항 카테고리
     * @param params   초안 생성 파라미터
     * @return LLM에 직접 전달할 메시지 리스트
     */
    public List<ChatMessage> buildMessages(QuestionCategory category, DraftParams params) {
        PromptStrategy strategy = getStrategy(category);

        List<ChatMessage> messages = new ArrayList<>();

        // 1. 시스템 프롬프트
        messages.add(SystemMessage.from(strategy.buildSystemPrompt()));

        // 2. Few-shot 예시 쌍 삽입
        List<FewShotExample> examples = strategy.getFewShotExamples();
        if (examples != null && !examples.isEmpty()) {
            for (FewShotExample example : examples) {
                messages.add(UserMessage.from(example.userMessage()));
                messages.add(AiMessage.from(example.assistantMessage()));
            }
            log.debug("PromptFactory: inserted {} few-shot pair(s) for category={}",
                    examples.size(), category);
        }

        // 3. 실제 생성 요청 메시지
        messages.add(UserMessage.from(strategy.buildUserMessage(params)));

        log.debug("PromptFactory: built {} messages for category={} company={} question={}",
                messages.size(),
                category,
                truncate(params.company(), 30),
                truncate(params.questionTitle(), 60));

        return messages;
    }

    private static String truncate(String value, int maxLen) {
        if (value == null) return "";
        return value.length() <= maxLen ? value : value.substring(0, maxLen) + "…";
    }
}
