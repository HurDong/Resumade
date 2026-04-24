package com.resumade.api.workspace.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.resumade.api.workspace.prompt.AnswerContract;
import com.resumade.api.workspace.prompt.DraftBlueprint;
import com.resumade.api.workspace.prompt.ExperienceNeed;
import com.resumade.api.workspace.prompt.QuestionCategory;
import com.resumade.api.workspace.prompt.QuestionDraftPlan;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.data.message.AiMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class QuestionDraftPlannerService {

    private final ChatLanguageModel workspaceDraftChatModel;
    private final ObjectMapper objectMapper;

    public QuestionDraftPlan plan(
            String company,
            String position,
            String question,
            int hardLimit,
            int minTarget,
            int desiredTarget,
            String directive
    ) {
        try {
            List<ChatMessage> messages = List.of(
                    SystemMessage.from(systemPrompt()),
                    UserMessage.from(userPrompt(company, position, question, hardLimit, minTarget, desiredTarget, directive))
            );
            Response<AiMessage> response = workspaceDraftChatModel.generate(messages);
            return parsePlan(response.content().text(), question, hardLimit, minTarget, desiredTarget);
        } catch (Exception e) {
            log.warn("QuestionDraftPlannerService failed, using deterministic fallback. reason={}", e.getMessage());
            return fallbackPlan(question, hardLimit, minTarget, desiredTarget);
        }
    }

    private String systemPrompt() {
        return """
                You are RESUMADE's Korean cover-letter strategy planner.
                Return ONLY valid JSON. Do not write markdown.
                Your job is not to draft. Your job is to create an AnswerContract and DraftBlueprint.

                Rules:
                - Categories are signals, not templates.
                - Pick one primaryCategory as the answer spine.
                - Put secondary intents inside the right paragraph or sentence; do not create extra paragraphs only because categories are mixed.
                - paragraphCount must follow length: short(<=350)=1, medium(351-650)=1 or 2, long(651-900)=2 or 3, extended(901+)=3 or 4.
                - experienceNeeds must be concrete search queries for RAG units, not broad keywords.
                - preferredUnitTypes must use SITUATION, ROLE, JUDGMENT, ACTION, RESULT, TECH_STACK, QUESTION_TYPE.

                JSON schema:
                {
                  "primaryCategory": "MOTIVATION|EXPERIENCE|PROBLEM_SOLVING|COLLABORATION|PERSONAL_GROWTH|CULTURE_FIT|TREND_INSIGHT|DEFAULT",
                  "compound": true,
                  "primaryIntent": "one Korean sentence",
                  "secondaryIntents": ["Korean phrase"],
                  "lengthBand": "short|medium|long|extended",
                  "paragraphCount": 2,
                  "answerContract": {
                    "mustInclude": ["Korean phrase"],
                    "mustNotOverdo": ["Korean phrase"],
                    "successCriteria": ["Korean phrase"]
                  },
                  "contentUnits": ["problem situation", "my role", "action", "result"],
                  "compressionPlan": ["Korean instruction"],
                  "experienceNeeds": [
                    {"unit":"problem situation", "query":"Korean search query", "preferredUnitTypes":["SITUATION"], "intentTags":["problem solving"]}
                  ],
                  "draftBlueprint": {
                    "targetLength": 500,
                    "paragraphCount": 2,
                    "paragraphs": [
                      {"paragraph":1, "role":"Korean role", "units":["problem situation"], "targetChars":220}
                    ]
                  },
                  "framingNote": "Korean strategy note",
                  "requiredElements": ["explicit requirement"],
                  "ragKeywords": ["keyword"]
                }
                """;
    }

    private String userPrompt(
            String company,
            String position,
            String question,
            int hardLimit,
            int minTarget,
            int desiredTarget,
            String directive
    ) {
        return """
                Company: %s
                Position: %s
                Question: %s
                Draft hard limit before wash: %d characters
                Draft target range before wash: %d to %d characters
                User/batch directive: %s
                """.formatted(nullSafe(company), nullSafe(position), nullSafe(question),
                hardLimit, minTarget, desiredTarget, nullSafe(directive));
    }

    private QuestionDraftPlan parsePlan(String raw, String question, int hardLimit, int minTarget, int desiredTarget) throws Exception {
        JsonNode node = objectMapper.readTree(sanitizeJson(raw));
        QuestionCategory category = QuestionCategory.fromString(node.path("primaryCategory").asText(""));
        boolean compound = node.path("compound").asBoolean(node.path("isCompound").asBoolean(false));
        String lengthBand = firstNonBlank(node.path("lengthBand").asText(""), lengthBand(hardLimit));
        int paragraphCount = normalizeParagraphCount(node.path("paragraphCount").asInt(defaultParagraphCount(hardLimit)), hardLimit);

        AnswerContract contract = new AnswerContract(
                readStringList(node.path("answerContract").path("mustInclude")),
                readStringList(node.path("answerContract").path("mustNotOverdo")),
                readStringList(node.path("answerContract").path("successCriteria"))
        );

        List<ExperienceNeed> needs = readExperienceNeeds(node.path("experienceNeeds"));
        if (needs.isEmpty()) {
            needs = defaultExperienceNeeds(category, question);
        }

        DraftBlueprint blueprint = readBlueprint(node.path("draftBlueprint"), desiredTarget, paragraphCount);
        return new QuestionDraftPlan(
                category,
                compound,
                node.path("primaryIntent").asText(""),
                readStringList(node.path("secondaryIntents")),
                lengthBand,
                paragraphCount,
                contract,
                readStringList(node.path("contentUnits")),
                readStringList(node.path("compressionPlan")),
                needs,
                blueprint,
                node.path("framingNote").asText(""),
                readStringList(node.path("requiredElements")),
                readStringList(node.path("ragKeywords"))
        );
    }

    private DraftBlueprint readBlueprint(JsonNode node, int desiredTarget, int paragraphCount) {
        List<DraftBlueprint.ParagraphPlan> paragraphs = new ArrayList<>();
        JsonNode paragraphNodes = node.path("paragraphs");
        if (paragraphNodes.isArray()) {
            for (JsonNode paragraphNode : paragraphNodes) {
                paragraphs.add(new DraftBlueprint.ParagraphPlan(
                        paragraphNode.path("paragraph").asInt(paragraphs.size() + 1),
                        paragraphNode.path("role").asText(""),
                        readStringList(paragraphNode.path("units")),
                        paragraphNode.path("targetChars").asInt(Math.max(1, desiredTarget / Math.max(1, paragraphCount)))
                ));
            }
        }
        if (paragraphs.isEmpty()) {
            for (int i = 1; i <= paragraphCount; i++) {
                paragraphs.add(new DraftBlueprint.ParagraphPlan(i, "Answer the question with verified experience facts.", List.of(), desiredTarget / paragraphCount));
            }
        }
        paragraphs = normalizeBlueprintParagraphs(paragraphs, desiredTarget, paragraphCount);
        return new DraftBlueprint(node.path("targetLength").asInt(desiredTarget), paragraphCount, paragraphs);
    }

    private List<DraftBlueprint.ParagraphPlan> normalizeBlueprintParagraphs(
            List<DraftBlueprint.ParagraphPlan> paragraphs,
            int desiredTarget,
            int paragraphCount
    ) {
        if (paragraphs.size() <= paragraphCount) {
            return paragraphs;
        }

        List<DraftBlueprint.ParagraphPlan> normalized = new ArrayList<>();
        for (int i = 0; i < paragraphCount; i++) {
            if (i < paragraphCount - 1) {
                DraftBlueprint.ParagraphPlan paragraph = paragraphs.get(i);
                normalized.add(new DraftBlueprint.ParagraphPlan(
                        i + 1,
                        paragraph.role(),
                        paragraph.units(),
                        paragraph.targetChars()
                ));
                continue;
            }

            List<DraftBlueprint.ParagraphPlan> tail = paragraphs.subList(i, paragraphs.size());
            LinkedHashSet<String> units = new LinkedHashSet<>();
            List<String> roles = new ArrayList<>();
            int targetChars = 0;
            for (DraftBlueprint.ParagraphPlan paragraph : tail) {
                if (paragraph.role() != null && !paragraph.role().isBlank()) {
                    roles.add(paragraph.role());
                }
                units.addAll(paragraph.units());
                targetChars += Math.max(0, paragraph.targetChars());
            }
            normalized.add(new DraftBlueprint.ParagraphPlan(
                    i + 1,
                    String.join(" / ", roles),
                    List.copyOf(units),
                    Math.max(1, Math.min(desiredTarget, targetChars))
            ));
        }
        return normalized;
    }

    private List<ExperienceNeed> readExperienceNeeds(JsonNode node) {
        if (!node.isArray()) {
            return List.of();
        }
        List<ExperienceNeed> result = new ArrayList<>();
        for (JsonNode item : node) {
            String query = item.path("query").asText("").trim();
            if (query.isBlank()) {
                continue;
            }
            result.add(new ExperienceNeed(
                    item.path("unit").asText(""),
                    query,
                    readStringList(item.path("preferredUnitTypes")),
                    readStringList(item.path("intentTags"))
            ));
        }
        return result;
    }

    private QuestionDraftPlan fallbackPlan(String question, int hardLimit, int minTarget, int desiredTarget) {
        QuestionCategory category = inferCategory(question);
        int paragraphCount = normalizeParagraphCount(defaultParagraphCount(hardLimit), hardLimit);
        List<ExperienceNeed> needs = defaultExperienceNeeds(category, question);
        AnswerContract contract = new AnswerContract(
                List.of("문항의 명시 요구사항", "본인의 역할", "구체적 행동", "결과"),
                List.of("카테고리별 문단을 억지로 분리", "근거 없는 성과 수치"),
                List.of("주요 의도가 글의 중심으로 드러남", "보조 의도는 행동과 결과 안에 자연스럽게 결합됨")
        );
        DraftBlueprint blueprint = readBlueprint(objectMapper.createObjectNode(), desiredTarget, paragraphCount);
        return new QuestionDraftPlan(
                category,
                question != null && (question.contains("및") || question.contains("또는") || question.contains("함께")),
                category.getDisplayName(),
                List.of(),
                lengthBand(hardLimit),
                paragraphCount,
                contract,
                List.of("상황", "본인 역할", "판단", "행동", "결과"),
                List.of(paragraphCount == 1 ? "모든 요구사항을 한 문단에 압축한다." : "보조 요구사항은 행동/결과 문단에 병합한다."),
                needs,
                blueprint,
                "",
                List.of(),
                category.getRelatedTags()
        );
    }

    private List<ExperienceNeed> defaultExperienceNeeds(QuestionCategory category, String question) {
        String base = question == null || question.isBlank() ? category.getDisplayName() : question;
        return switch (category) {
            case PROBLEM_SOLVING -> List.of(
                    new ExperienceNeed("문제상황", base + " 문제 상황 원인 진단", List.of("SITUATION", "JUDGMENT"), List.of("문제해결")),
                    new ExperienceNeed("해결행동", base + " 해결 행동 결과", List.of("ACTION", "RESULT"), List.of("문제해결"))
            );
            case COLLABORATION -> List.of(
                    new ExperienceNeed("협업맥락", base + " 협업 역할 조율 갈등", List.of("ROLE", "ACTION"), List.of("협업")),
                    new ExperienceNeed("성과", base + " 협업 결과", List.of("RESULT"), List.of("협업"))
            );
            case EXPERIENCE -> List.of(
                    new ExperienceNeed("직무경험", base + " 기술 역할 행동 성과", List.of("ACTION", "RESULT", "TECH_STACK"), List.of("직무역량"))
            );
            default -> List.of(
                    new ExperienceNeed("핵심경험", base + " 관련 경험 역할 행동 결과", List.of("SITUATION", "ACTION", "RESULT"), category.getRelatedTags())
            );
        };
    }

    private QuestionCategory inferCategory(String question) {
        String q = question == null ? "" : question;
        if (containsAny(q, "문제", "해결", "실패", "극복", "어려움")) return QuestionCategory.PROBLEM_SOLVING;
        if (containsAny(q, "협업", "팀", "갈등", "소통")) return QuestionCategory.COLLABORATION;
        if (containsAny(q, "지원동기", "입사", "회사")) return QuestionCategory.MOTIVATION;
        if (containsAny(q, "성장", "가치관", "인생")) return QuestionCategory.PERSONAL_GROWTH;
        if (containsAny(q, "직무", "프로젝트", "기술", "역량")) return QuestionCategory.EXPERIENCE;
        return QuestionCategory.DEFAULT;
    }

    private boolean containsAny(String text, String... values) {
        for (String value : values) {
            if (text.contains(value)) {
                return true;
            }
        }
        return false;
    }

    private int normalizeParagraphCount(int candidate, int hardLimit) {
        int max = hardLimit <= 350 ? 1 : hardLimit <= 650 ? 2 : hardLimit <= 900 ? 3 : 4;
        return Math.max(1, Math.min(max, candidate <= 0 ? defaultParagraphCount(hardLimit) : candidate));
    }

    private int defaultParagraphCount(int hardLimit) {
        if (hardLimit <= 350) return 1;
        if (hardLimit <= 650) return 2;
        if (hardLimit <= 900) return 3;
        return 4;
    }

    private String lengthBand(int hardLimit) {
        if (hardLimit <= 350) return "short";
        if (hardLimit <= 650) return "medium";
        if (hardLimit <= 900) return "long";
        return "extended";
    }

    private List<String> readStringList(JsonNode node) {
        if (node == null || !node.isArray()) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        for (JsonNode item : node) {
            String value = item.asText("").trim();
            if (!value.isBlank()) {
                values.add(value);
            }
        }
        return values;
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

    private String firstNonBlank(String first, String second) {
        return first == null || first.isBlank() ? second : first;
    }

    private String nullSafe(String value) {
        return value == null ? "" : value;
    }
}
