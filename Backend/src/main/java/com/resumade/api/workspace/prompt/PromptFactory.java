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
 * 문항 카테고리에 맞는 프롬프트 전략을 선택하고,
 * LLM에 전달할 최종 {@code List<ChatMessage>}를 조립하는 Factory.
 *
 * <p>초안 생성뿐 아니라 리파인/길이 보강 단계에서도 동일한 카테고리 전략을
 * 유지할 수 있도록 stage-specific message assembly를 제공합니다.
 */
@Slf4j
@Component
public class PromptFactory {

    private final Map<QuestionCategory, PromptStrategy> strategyMap;
    private final PromptStrategy defaultStrategy;

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
            fallback = new DefaultPromptStrategy();
            strategyMap.put(QuestionCategory.DEFAULT, fallback);
            log.warn("DefaultPromptStrategy was not found in Spring context; using inline fallback.");
        }
        this.defaultStrategy = fallback;

        log.info("PromptFactory initialized with {} strategies: {}",
                strategyMap.size(), strategyMap.keySet());
    }

    public PromptStrategy getStrategy(QuestionCategory category) {
        if (category == null) {
            return defaultStrategy;
        }
        return strategyMap.getOrDefault(category, defaultStrategy);
    }

    public List<ChatMessage> buildMessages(QuestionCategory category, DraftParams params) {
        PromptStrategy strategy = getStrategy(category);

        List<ChatMessage> messages = new ArrayList<>();
        messages.add(SystemMessage.from(strategy.buildSystemPrompt()));
        appendFewShotExamples(messages, strategy, category, "generate");
        messages.add(UserMessage.from(strategy.buildUserMessage(params)));

        log.debug("PromptFactory: built {} generate messages for category={} company={} question={}",
                messages.size(),
                category,
                truncate(params.company(), 30),
                truncate(params.questionTitle(), 60));

        return messages;
    }

    public List<ChatMessage> buildRefineMessages(
            QuestionCategory category,
            DraftParams params,
            String currentDraft,
            boolean lengthRetry
    ) {
        PromptStrategy strategy = getStrategy(category);

        List<ChatMessage> messages = new ArrayList<>();
        messages.add(SystemMessage.from(strategy.buildSystemPrompt()));
        appendFewShotExamples(messages, strategy, category, lengthRetry ? "length-retry" : "refine");
        messages.add(UserMessage.from(buildRefineUserMessage(params, currentDraft, lengthRetry)));

        log.debug("PromptFactory: built {} refine messages for category={} company={} question={} retry={}",
                messages.size(),
                category,
                truncate(params.company(), 30),
                truncate(params.questionTitle(), 60),
                lengthRetry);

        return messages;
    }

    private void appendFewShotExamples(
            List<ChatMessage> messages,
            PromptStrategy strategy,
            QuestionCategory category,
            String stage
    ) {
        List<FewShotExample> examples = strategy.getFewShotExamples();
        if (examples == null || examples.isEmpty()) {
            return;
        }

        for (FewShotExample example : examples) {
            messages.add(UserMessage.from(example.userMessage()));
            messages.add(AiMessage.from(example.assistantMessage()));
        }

        log.debug("PromptFactory: inserted {} few-shot pair(s) for category={} stage={}",
                examples.size(), category, stage);
    }

    private String buildRefineUserMessage(DraftParams params, String currentDraft, boolean lengthRetry) {
        String revisionGoal = lengthRetry
                ? "Preserve all strong facts from the current draft and expand only missing depth so the answer reaches the target range."
                : "Preserve the strong facts from the current draft while improving structure, specificity, and job fit.";
        String retryNote = lengthRetry
                ? "The previous output was below the minimum target or outside the preferred range. Fix that in this revision."
                : "Treat the current draft as the base text and revise it without inventing new facts.";

        return """
                Company: %s
                Position: %s
                Question: %s

                <Context>
                ## Company & JD Analysis
                %s

                ## Relevant Experience Data
                %s

                ## Other questions already written (HARD anti-overlap constraint)
                %s

                ## Current Draft
                %s
                </Context>

                <Revision_Goal>
                %s
                %s
                </Revision_Goal>

                <Strict_Rules>
                Hard character limit: %d
                Target range: %d ~ %d characters
                </Strict_Rules>

                ## Additional User Directive
                %s

                <Output_Format>
                Return ONLY valid JSON:
                {"title": "제목 텍스트", "text": "본문..."}
                - "title": specific, concrete, non-generic, no brackets
                - "text": revised body only, do NOT repeat the title inside the text
                </Output_Format>
                """.formatted(
                nullSafe(params.company()),
                nullSafe(params.position()),
                nullSafe(params.questionTitle()),
                nullSafe(params.companyContext()),
                nullSafe(params.experienceContext()),
                nullSafe(params.othersContext()),
                nullSafe(currentDraft),
                revisionGoal,
                retryNote,
                params.maxLength(),
                params.minTarget(),
                params.maxTarget(),
                nullSafe(params.directive())
        );
    }

    private static String truncate(String value, int maxLen) {
        if (value == null) {
            return "";
        }
        return value.length() <= maxLen ? value : value.substring(0, maxLen) + "...";
    }

    private static String nullSafe(String value) {
        return value != null ? value : "";
    }
}
