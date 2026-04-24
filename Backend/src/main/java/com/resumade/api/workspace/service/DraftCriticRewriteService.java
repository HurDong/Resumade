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
}
