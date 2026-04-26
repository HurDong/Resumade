package com.resumade.api.workspace.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.resumade.api.workspace.prompt.DraftParams;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.output.Response;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class DraftCriticRewriteService {

    private final ChatLanguageModel workspaceDraftChatModel;
    private final ObjectMapper objectMapper;

    public WorkspaceDraftAiService.DraftResponse rewrite(DraftParams params, WorkspaceDraftAiService.DraftResponse draft) {
        if (draft == null) {
            return emptyResponse();
        }
        try {
            List<ChatMessage> messages = List.of(
                    SystemMessage.from("""
                            You are RESUMADE's strict final critic and targeted rewriter.
                            You must return ONLY valid JSON: {"title":"...","text":"..."}.
                            Keep verified facts. Do not invent metrics, tools, company facts, or roles.
                            Fix only what is needed for the AnswerContract, DraftBlueprint, length, paragraph count, and natural Korean flow.
                            Treat questionIntent, answerPosture, evidencePolicy, and companyConnectionPolicy as hard constraints.
                            If the plan is GROWTH_NARRATIVE/LIFE_ARC_REFLECTION, reject and rewrite any answer that reads like a skill list, project catalog, or direct company contribution pitch. It must show how the person was formed over time.
                            If the plan is TRAIT_REFLECTION or WEAKNESS_RECOVERY, reject and rewrite any answer that brags about technical competency instead of showing real-situation reaction, choice, consequence, and improvement.
                            If the text is below the lower length bound, expand with concrete life arc, reflection, surrounding impact, or improvement habit. Do not finish under the lower bound unless the hard limit is extremely short.
                            If companyConnectionPolicy is NONE or LIGHT_FINAL_SENTENCE, remove direct contribution promises and keep only a modest final working-attitude connection.
                            If the draft already satisfies the plan, return it with minimal wording cleanup.
                            The title must come from the draft or a direct improvement, and the text must not repeat the title.
                            """),
                    UserMessage.from("""
                            Company: %s
                            Position: %s
                            Question: %s

                            <DraftPlan>
                            %s
                            </DraftPlan>

                            <Context>
                            ## Relevant Experience Data
                            %s

                            ## Other questions already written
                            %s
                            </Context>

                            <LengthPolicy>
                            Hard character limit for text: %d
                            Target range for text: %d ~ %d characters
                            </LengthPolicy>

                            <DraftToReview>
                            title: %s
                            text:
                            %s
                            </DraftToReview>
                            """.formatted(
                            nullSafe(params.company()),
                            nullSafe(params.position()),
                            nullSafe(params.questionTitle()),
                            nullSafe(params.draftPlanContext()),
                            nullSafe(params.experienceContext()),
                            nullSafe(params.othersContext()),
                            params.maxLength(),
                            params.minTarget(),
                            params.maxTarget(),
                            nullSafe(draft.title),
                            nullSafe(draft.text)
                    ))
            );
            Response<AiMessage> response = workspaceDraftChatModel.generate(messages);
            WorkspaceDraftAiService.DraftResponse rewritten = objectMapper.readValue(
                    sanitizeJson(response.content().text()),
                    WorkspaceDraftAiService.DraftResponse.class);
            return rewritten == null ? draft : rewritten;
        } catch (Exception e) {
            log.warn("DraftCriticRewriteService failed, using generated draft as-is. reason={}", e.getMessage());
            return draft;
        }
    }

    public WorkspaceDraftAiService.DraftResponse rewriteForFinalLength(
            DraftParams params,
            String currentDraft,
            String currentWashedDraft,
            int currentFinalLength,
            int attempt,
            int maxAttempts
    ) {
        try {
            List<ChatMessage> messages = List.of(
                    SystemMessage.from("""
                            You are RESUMADE's length-retry rewriter for Korean cover-letter drafts.
                            Return ONLY valid JSON: {"title":"...","text":"..."}.
                            The previous washed draft failed the final visible-character length check.
                            Preserve verified facts and the DraftPlan, but rewrite enough to satisfy the target range.
                            If the final washed text was too short, expand with concrete life arc, reflection, situation reaction, surrounding impact, or improvement habit.
                            If it was too long, compress repeated facts and keep the main narrative.
                            Do not add unsupported facts, fake metrics, or direct company-contribution promises when the plan restricts them.
                            Do not return a draft below the lower bound unless the hard limit is lower than the lower bound.
                            """),
                    UserMessage.from("""
                            Retry: %d / %d

                            Company: %s
                            Position: %s
                            Question: %s

                            <DraftPlan>
                            %s
                            </DraftPlan>

                            <LengthFailure>
                            Current final washed length: %d visible characters
                            Required final range: %d ~ %d visible characters
                            Hard limit: %d visible characters
                            Rewrite direction: %s
                            </LengthFailure>

                            <Context>
                            ## Relevant Experience Data
                            %s

                            ## Other questions already written
                            %s
                            </Context>

                            <CurrentSourceDraft>
                            %s
                            </CurrentSourceDraft>

                            <CurrentWashedDraft>
                            %s
                            </CurrentWashedDraft>
                            """.formatted(
                            attempt,
                            maxAttempts,
                            nullSafe(params.company()),
                            nullSafe(params.position()),
                            nullSafe(params.questionTitle()),
                            nullSafe(params.draftPlanContext()),
                            currentFinalLength,
                            params.minTarget(),
                            params.maxLength(),
                            params.maxLength(),
                            buildRewriteDirection(params, currentFinalLength),
                            nullSafe(params.experienceContext()),
                            nullSafe(params.othersContext()),
                            nullSafe(currentDraft),
                            nullSafe(currentWashedDraft)
                    ))
            );
            Response<AiMessage> response = workspaceDraftChatModel.generate(messages);
            WorkspaceDraftAiService.DraftResponse rewritten = objectMapper.readValue(
                    sanitizeJson(response.content().text()),
                    WorkspaceDraftAiService.DraftResponse.class);
            return rewritten == null ? emptyResponse() : rewritten;
        } catch (Exception e) {
            log.warn("[길이재시도-실패] 재작성 LLM 호출 실패 - 기존 후보 유지 reason={}", e.getMessage());
            return emptyResponse();
        }
    }

    private WorkspaceDraftAiService.DraftResponse emptyResponse() {
        WorkspaceDraftAiService.DraftResponse response = new WorkspaceDraftAiService.DraftResponse();
        response.title = "";
        response.text = "";
        return response;
    }

    private String sanitizeJson(String raw) {
        if (raw == null) return "{}";
        String trimmed = raw.strip();
        if (trimmed.startsWith("```")) {
            trimmed = trimmed.replaceFirst("^```(?:json)?\\s*", "");
            trimmed = trimmed.replaceAll("```\\s*$", "").strip();
        }
        return trimmed;
    }

    private String nullSafe(String value) {
        return value == null ? "" : value;
    }

    private String buildRewriteDirection(DraftParams params, int currentFinalLength) {
        if (currentFinalLength < params.minTarget()) {
            int deficit = params.minTarget() - currentFinalLength;
            return "최종 세탁본이 " + deficit + "자 부족합니다. 핵심 경험을 새로 늘어놓지 말고, 현재 서사의 계기/판단/영향/배운 점을 구체화해 목표 범위에 들어오게 확장하세요.";
        }
        if (currentFinalLength > params.maxLength()) {
            int excess = currentFinalLength - params.maxLength();
            return "최종 세탁본이 " + excess + "자 초과했습니다. 반복 표현과 보조 경험을 줄여 hard limit 안으로 압축하세요.";
        }
        return "길이는 허용 범위에 가깝습니다. 목표 중심을 유지하며 자연스러운 문장으로 정리하세요.";
    }
}
