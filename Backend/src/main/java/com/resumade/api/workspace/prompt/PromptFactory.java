package com.resumade.api.workspace.prompt;

import com.resumade.api.workspace.prompt.strategy.DefaultPromptStrategy;
import com.resumade.api.workspace.prompt.QuestionProfile;
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

    /**
     * [v2] QuestionProfile 기반 메시지 조립.
     * buildSystemPromptWithProfile()로 Layer1+Layer2 동적 시스템 프롬프트를 사용하고,
     * requiredElements를 <Required_Elements> 블록으로 주입합니다.
     */
    public List<ChatMessage> buildMessagesV2(QuestionProfile profile, DraftParams params) {
        QuestionCategory category = profile != null ? profile.primaryCategory() : QuestionCategory.DEFAULT;
        PromptStrategy strategy = getStrategy(category);

        List<ChatMessage> messages = new ArrayList<>();
        messages.add(SystemMessage.from(strategy.buildSystemPromptWithProfile(profile)));
        appendFewShotExamples(messages, strategy, category, "generate-v2");

        String userMsg = strategy.buildUserMessage(params);
        if (profile != null && !profile.requiredElements().isEmpty()) {
            userMsg = injectRequiredElements(userMsg, profile.requiredElements());
        }
        messages.add(UserMessage.from(userMsg));

        log.debug("PromptFactory: built {} v2 messages for category={} compound={} company={} question={}",
                messages.size(), category,
                profile != null && profile.isCompound(),
                truncate(params.company(), 30),
                truncate(params.questionTitle(), 60));

        return messages;
    }

    public List<ChatMessage> buildDraftPlanMessages(QuestionDraftPlan plan, DraftParams params) {
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(SystemMessage.from("""
                You are RESUMADE's Korean cover-letter draft writer.
                Follow the provided AnswerContract and DraftBlueprint as the authority.
                Do not use a fixed category template when the blueprint merges multiple intents.
                Use only facts from Relevant Experience Data, Company/JD context, and user directives.
                If the evidence is weak, write conservatively rather than inventing metrics.
                If Relevant Experience Data contains NO_VERIFIED_EXPERIENCE_CONTEXT, do not draft a cover letter.
                In that case return ONLY {"title":"근거 경험 선택 필요","text":"이 문항에 연결할 검증된 경험이 아직 선택되거나 검색되지 않았습니다. 경험 보관소에서 관련 경험을 선택하거나 새 경험을 추가한 뒤 다시 생성해 주세요."}.
                Respect paragraphCount. For short answers, merge situation, action, result, and lesson into one paragraph.
                Treat questionIntent, answerPosture, evidencePolicy, and companyConnectionPolicy as hard constraints.
                For GROWTH_NARRATIVE/LIFE_ARC_REFLECTION, write a compact life-arc: early trigger, school/activity continuation, attitude formed, current working style. Do not write a technology resume or achievement catalog.
                For TRAIT_REFLECTION, show how the person's trait changes their choice and reaction in a real situation. Do not brag about broad job competency.
                For WEAKNESS_RECOVERY, include a mild consequence, immediate correction, and current improvement habit.
                If companyConnectionPolicy is NONE or LIGHT_FINAL_SENTENCE, do not promise direct company contribution. At most close with a modest working attitude.
                Stay inside the target range. If the draft is under the lower bound, expand with concrete reflection, transition, or impact on people/workflow, not with filler.
                For questions asking "required competencies and efforts/experience", do not answer like a job analysis report. Start from the applicant's verified experience and let the required competency emerge through action and reflection.
                Never open with a competency list such as "핵심 역량은 다음과 같습니다" or numbered items like "1)". Do not use report labels such as "주요 경력", "관련 경험", "역할, 조치, 결과", "RCA", or "MTTR" unless those exact terms appear in the applicant's verified experience.
                The body must read as a Korean self-introduction essay in first-person applicant voice, not a briefing, rubric, resume summary, or consulting memo.
                Return ONLY valid JSON: {"title":"specific title","text":"body only"}
                """));
        messages.add(UserMessage.from("""
                Company: %s
                Position: %s
                Question: %s

                <DraftPlan>
                %s
                </DraftPlan>

                <Context>
                ## Company & JD Analysis
                %s

                ## Relevant Experience Data
                %s

                ## Other questions already written
                %s
                </Context>

                <LengthPolicy>
                Hard character limit for text: %d
                Target range for text: %d ~ %d characters
                </LengthPolicy>

                <UserDirective>
                %s
                </UserDirective>
                """.formatted(
                nullSafe(params.company()),
                nullSafe(params.position()),
                nullSafe(params.questionTitle()),
                nullSafe(params.draftPlanContext()),
                nullSafe(params.companyContext()),
                nullSafe(params.experienceContext()),
                nullSafe(params.othersContext()),
                params.maxLength(),
                params.minTarget(),
                params.maxTarget(),
                nullSafe(params.directive())
        )));
        return messages;
    }

    public List<ChatMessage> buildDraftPlanMessagesV3(QuestionDraftPlanV3 plan, DraftParams params) {
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(SystemMessage.from("""
                You are RESUMADE's v3 Korean cover-letter draft writer.
                Your goal is not to hide AI usage. Your goal is to produce a truthful, interview-defensible draft grounded in the applicant's verified experience.
                Return ONLY valid JSON: {"title":"specific title","text":"body only"}.

                <V3_Core_Principles>
                - Use the provided dominant spine as the answer backbone.
                - Integrate secondary requirements at the injection points. Do not split the answer into category-by-category paragraphs.
                - Every important claim must be traceable to one of these evidence types: role, judgment, action, result, measured or observable outcome.
                - If a metric is not present in the context, do not invent it. Use a bounded observable result instead.
                - Keep the voice as a Korean applicant's first-person prose, not a report, rubric, resume summary, or consultant memo.
                - Avoid detector-like patterns: repeated transition phrases, perfectly uniform sentence rhythm, abstract adjective chains, passive voice, third-person descriptions, and over-polished generic endings.
                - Do not insert deliberate typos, awkward slang, or artificial imperfection.
                - Prefer sentences that can survive a follow-up interview question.
                </V3_Core_Principles>

                <Category_Direction>
                MOTIVATION: connect one company-specific anchor, one applicant readiness proof, why now, and a realistic early contribution. No company worship, stability, salary, welfare, or brand-only motive.
                EXPERIENCE: write problem, owned role, technology choice reason, execution, result, and measurement basis. Treat stack-name lists as failure.
                PROBLEM_SOLVING: include root-cause confirmation, risk of inaction, option comparison, constraint, and changed judgment.
                COLLABORATION: include shared goal, role split, coordination method, friction or bottleneck, team result, and personal contribution.
                PERSONAL_GROWTH: center life/growth arc, turning point, formed attitude, and current working behavior. Do not turn it into a project achievement retrospective.
                CULTURE_FIT: prove traits or values through choices, reactions, improvement habits, and team/customer impact.
                TREND_INSIGHT: start from an external trend, then company application scene, limitation/condition, and only brief applicant support.
                DEFAULT: infer the strongest micro-ask and absorb secondary asks into one narrative.
                </Category_Direction>

                <Hard_Rules>
                - Respect the hard character limit and target range.
                - Title must be concrete and grounded in action, value, result, or behavior. No generic labels.
                - Text must not repeat the title.
                - No section labels, no numbered list, no parenthetical meta labels, no "핵심 역량은 다음과 같습니다".
                - No empty endings such as "최선을 다하겠습니다" or "열심히 하겠습니다".
                - If Relevant Experience Data contains NO_VERIFIED_EXPERIENCE_CONTEXT, return only:
                  {"title":"근거 경험 선택 필요","text":"이 문항에 연결할 검증된 경험이 아직 선택되거나 검색되지 않았습니다. 경험 보관소에서 관련 경험을 선택하거나 새 경험을 추가한 뒤 다시 생성해 주세요."}
                </Hard_Rules>
                """));
        messages.add(UserMessage.from("""
                Company: %s
                Position: %s
                Question: %s

                <DraftPlanV3>
                %s
                </DraftPlanV3>

                <Context>
                ## Company & JD Analysis
                %s

                ## Relevant Experience Data
                %s

                ## Other questions already written (HARD anti-overlap constraint)
                %s
                </Context>

                <LengthPolicy>
                Hard character limit for text: %d
                Target range for text: %d ~ %d characters
                </LengthPolicy>

                <UserDirective>
                %s
                </UserDirective>

                <Output_Format>
                Return ONLY valid JSON:
                {"title":"specific title","text":"body only"}
                </Output_Format>
                """.formatted(
                nullSafe(params.company()),
                nullSafe(params.position()),
                nullSafe(params.questionTitle()),
                plan == null ? "" : plan.toPromptBlock(),
                nullSafe(params.companyContext()),
                nullSafe(params.experienceContext()),
                nullSafe(params.othersContext()),
                params.maxLength(),
                params.minTarget(),
                params.maxTarget(),
                nullSafe(params.directive())
        )));
        return messages;
    }

    /**
     * [v2 refine] QuestionProfile + retryDirective 기반 리파인 메시지.
     */
    public List<ChatMessage> buildRefineMessagesV2(QuestionProfile profile, DraftParams params,
                                                    String currentDraft, String retryDirective) {
        QuestionCategory category = profile != null ? profile.primaryCategory() : QuestionCategory.DEFAULT;
        PromptStrategy strategy = getStrategy(category);

        List<ChatMessage> messages = new ArrayList<>();
        messages.add(SystemMessage.from(strategy.buildSystemPromptWithProfile(profile)));
        appendFewShotExamples(messages, strategy, category, "refine-v2");

        String refineMsg = buildRefineUserMessageV2(params, currentDraft, retryDirective);
        if (profile != null && !profile.requiredElements().isEmpty()) {
            refineMsg = injectRequiredElements(refineMsg, profile.requiredElements());
        }
        messages.add(UserMessage.from(refineMsg));

        return messages;
    }

    private String buildRefineUserMessageV2(DraftParams params, String currentDraft, String retryDirective) {
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

                <Retry_Directive>
                %s
                </Retry_Directive>

                <Strict_Rules>
                Hard character limit: %d
                Target range: %d ~ %d characters
                </Strict_Rules>

                ## Additional User Directive
                %s

                <Output_Format>
                Return ONLY valid JSON:
                {"title": "제목 텍스트", "text": "본문..."}
                - "title": specific, concrete, no brackets
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
                nullSafe(retryDirective),
                params.maxLength(),
                params.minTarget(),
                params.maxTarget(),
                nullSafe(params.directive())
        );
    }

    /**
     * requiredElements를 <Required_Elements> 블록으로 <Output_Format> 직전에 주입.
     */
    private static String injectRequiredElements(String userMsg, List<String> elements) {
        StringBuilder block = new StringBuilder();
        block.append("\n\n<Required_Elements>\n");
        block.append("이 문항에서 반드시 다뤄야 할 보조/추가 요구사항입니다. primary category의 중심 흐름을 유지한 채, 문항이 요구한 위치에서 자연스럽게 통합하세요:\n");
        for (int i = 0; i < elements.size(); i++) {
            block.append(i + 1).append(". ").append(elements.get(i)).append("\n");
        }
        block.append("→ 항목별 소제목·단락 분리 없이 하나의 흐름으로 작성하세요. 보조 요구사항이 중심 흐름을 대체하면 안 됩니다.\n");
        block.append("</Required_Elements>");

        int idx = userMsg.indexOf("<Output_Format>");
        if (idx >= 0) {
            return userMsg.substring(0, idx) + block + "\n\n" + userMsg.substring(idx);
        }
        return userMsg + block;
    }

    public List<ChatMessage> buildMessages(QuestionCategory category, DraftParams params) {
        PromptStrategy strategy = getStrategy(category);

        List<ChatMessage> messages = new ArrayList<>();
        messages.add(SystemMessage.from(strategy.buildSystemPrompt()));
        appendFewShotExamples(messages, strategy, category, "generate");

        String userMsg = strategy.buildUserMessage(params);
        if (params.additionalIntents() != null && !params.additionalIntents().isEmpty()) {
            userMsg = injectAdditionalIntents(userMsg, params.additionalIntents());
        }
        messages.add(UserMessage.from(userMsg));

        log.debug("PromptFactory: built {} generate messages for category={} compound={} company={} question={}",
                messages.size(),
                category,
                params.additionalIntents() != null && !params.additionalIntents().isEmpty(),
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

        String refineMsg = buildRefineUserMessage(params, currentDraft, lengthRetry);
        if (params.additionalIntents() != null && !params.additionalIntents().isEmpty()) {
            refineMsg = injectAdditionalIntents(refineMsg, params.additionalIntents());
        }
        messages.add(UserMessage.from(refineMsg));

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

    /**
     * 복합 문항의 세부 항목을 {@code <Additional_Requirements>} 블록으로 주입합니다.
     * {@code <Output_Format>} 태그 직전에 삽입하며, 없으면 메시지 끝에 추가합니다.
     */
    private static String injectAdditionalIntents(String userMsg, List<String> intents) {
        StringBuilder block = new StringBuilder();
        block.append("\n\n<Additional_Requirements>\n");
        block.append("이 문항은 복합 요구 문항입니다. 아래 항목을 모두 빠짐없이 하나의 유기적 서사로 답하세요:\n");
        for (int i = 0; i < intents.size(); i++) {
            block.append(i + 1).append(". ").append(intents.get(i)).append("\n");
        }
        block.append("→ 항목별 소제목·단락 구분 없이 자연스러운 흐름 안에서 모두 다루세요.\n");
        block.append("</Additional_Requirements>");

        int idx = userMsg.indexOf("<Output_Format>");
        if (idx >= 0) {
            return userMsg.substring(0, idx) + block + "\n\n" + userMsg.substring(idx);
        }
        return userMsg + block;
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
