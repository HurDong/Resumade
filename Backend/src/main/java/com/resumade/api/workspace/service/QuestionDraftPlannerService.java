package com.resumade.api.workspace.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.resumade.api.workspace.prompt.AnswerContract;
import com.resumade.api.workspace.prompt.DraftBlueprint;
import com.resumade.api.workspace.prompt.ExperienceNeed;
import com.resumade.api.workspace.prompt.QuestionCategory;
import com.resumade.api.workspace.prompt.QuestionDraftPlan;
import com.resumade.api.workspace.prompt.QuestionDraftPlanV3;
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

    public QuestionDraftPlanV3 planV3(
            String company,
            String position,
            String question,
            int hardLimit,
            int minTarget,
            int desiredTarget,
            String directive
    ) {
        QuestionDraftPlan basePlan = plan(company, position, question, hardLimit, minTarget, desiredTarget, directive);
        return QuestionDraftPlanV3.from(basePlan, question);
    }

    private String systemPrompt() {
        return """
                You are RESUMADE's Korean cover-letter strategy planner.
                Return ONLY valid JSON. Do not write markdown.
                Your job is not to draft. Your job is to create an AnswerContract and DraftBlueprint.

                Rules:
                - Categories are signals, not templates.
                - questionIntent is more important than primaryCategory. Never force a question into a competency-proof shape only because no exact category exists.
                - Pick one primaryCategory as the answer spine.
                - Put secondary intents inside the right paragraph or sentence; do not create extra paragraphs only because categories are mixed.
                - paragraphCount must follow length: short(<=350)=1, medium(351-650)=1 or 2, long(651-900)=2 or 3, extended(901+)=3 or 4.
                - experienceNeeds must be concrete search queries for RAG units, not broad keywords.
                - preferredUnitTypes must use SITUATION, ROLE, JUDGMENT, ACTION, RESULT, TECH_STACK, QUESTION_TYPE.
                - For growth process / school activities, set questionIntent=GROWTH_NARRATIVE and answerPosture=LIFE_ARC_REFLECTION. The answer must feel like a short life arc: starting trigger -> repeated school/project/life experience -> formed attitude -> current way of working.
                - For personality strengths, weaknesses, work style, values, or culture-fit questions, set answerPosture=TRAIT_REFLECTION. Explain how the person reacts in real situations, not how impressive their skills are.
                - For GROWTH_NARRATIVE or TRAIT_REFLECTION, use at most one main experience and one supporting experience. Avoid technology-stack lists, achievement catalogs, and direct "I will contribute to the company" promises.
                - For weakness questions, include a mild negative consequence, immediate correction, and ongoing improvement habit. Do not present a fatal flaw.
                - companyConnectionPolicy must be NONE, LIGHT_FINAL_SENTENCE, ROLE_RELEVANT, or DIRECT_CONTRIBUTION_ALLOWED. Growth/personality/work-style questions should usually use NONE or LIGHT_FINAL_SENTENCE.
                - The draft blueprint targetLength must stay inside the given target range. Do not intentionally underfill the answer.

                JSON schema:
                {
                  "primaryCategory": "MOTIVATION|EXPERIENCE|PROBLEM_SOLVING|COLLABORATION|PERSONAL_GROWTH|CULTURE_FIT|TREND_INSIGHT|DEFAULT",
                  "questionIntent": "GROWTH_NARRATIVE|PERSONALITY_STRENGTH|PERSONALITY_WEAKNESS|WORK_STYLE|VALUES_REFLECTION|CULTURE_FIT_STYLE|JOB_COMPETENCY|PROJECT_EXPERIENCE|MOTIVATION|DEFAULT",
                  "answerPosture": "LIFE_ARC_REFLECTION|TRAIT_REFLECTION|WEAKNESS_RECOVERY|COMPETENCY_PROOF|MOTIVATION_FIT",
                  "evidencePolicy": "one Korean sentence about how many experiences and which facts to use",
                  "companyConnectionPolicy": "NONE|LIGHT_FINAL_SENTENCE|ROLE_RELEVANT|DIRECT_CONTRIBUTION_ALLOWED",
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
                Hard character limit: %d characters
                Required target range: %d to %d Korean visible characters
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

        DraftBlueprint blueprint = readBlueprint(node.path("draftBlueprint"), minTarget, desiredTarget, paragraphCount);
        return new QuestionDraftPlan(
                category,
                firstNonBlank(node.path("questionIntent").asText(""), inferQuestionIntent(question)),
                firstNonBlank(node.path("answerPosture").asText(""), inferAnswerPosture(question)),
                firstNonBlank(node.path("evidencePolicy").asText(""), defaultEvidencePolicy(question)),
                firstNonBlank(node.path("companyConnectionPolicy").asText(""), defaultCompanyConnectionPolicy(question)),
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

    private DraftBlueprint readBlueprint(JsonNode node, int minTarget, int desiredTarget, int paragraphCount) {
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
        int requestedTarget = node.path("targetLength").asInt(desiredTarget);
        int targetLength = requestedTarget < minTarget || requestedTarget > desiredTarget
                ? desiredTarget
                : requestedTarget;
        return new DraftBlueprint(targetLength, paragraphCount, paragraphs);
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
        DraftBlueprint blueprint = readBlueprint(objectMapper.createObjectNode(), minTarget, desiredTarget, paragraphCount);
        return new QuestionDraftPlan(
                category,
                inferQuestionIntent(question),
                inferAnswerPosture(question),
                defaultEvidencePolicy(question),
                defaultCompanyConnectionPolicy(question),
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
        String intent = inferQuestionIntent(question);
        if ("GROWTH_NARRATIVE".equals(intent)) {
            return List.of(
                    new ExperienceNeed("성장 출발점", base + " 처음 관심 계기 학창시절 학교활동", List.of("SITUATION", "JUDGMENT"), List.of("성장과정", "학교활동")),
                    new ExperienceNeed("태도 형성", base + " 반복 경험 태도 변화 현재 일하는 방식", List.of("ACTION", "RESULT"), List.of("성장과정", "태도"))
            );
        }
        if (intent.startsWith("PERSONALITY_") || "WORK_STYLE".equals(intent) || "VALUES_REFLECTION".equals(intent)) {
            return List.of(
                    new ExperienceNeed("성향이 드러난 상황", base + " 성격 성향 판단 선택 상황", List.of("SITUATION", "JUDGMENT"), List.of("성향", "일하는 방식")),
                    new ExperienceNeed("반응과 영향", base + " 행동 결과 주변 영향 개선", List.of("ACTION", "RESULT"), List.of("성향", "반응"))
            );
        }
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
        if (containsAny(q, "성장", "학교활동", "학창", "교내외", "가치관", "인생")) return QuestionCategory.PERSONAL_GROWTH;
        if (containsAny(q, "성격", "장점", "단점", "강점", "약점", "성향", "일하는 방식", "스타일")) return QuestionCategory.CULTURE_FIT;
        if (containsAny(q, "문제", "해결", "실패", "극복", "어려움")) return QuestionCategory.PROBLEM_SOLVING;
        if (containsAny(q, "협업", "팀", "갈등", "소통")) return QuestionCategory.COLLABORATION;
        if (containsAny(q, "최근", "트렌드", "동향", "산업", "시장", "AI", "LLM", "생성형")) return QuestionCategory.TREND_INSIGHT;
        if (containsAny(q, "지원동기", "입사", "회사")) return QuestionCategory.MOTIVATION;
        if (containsAny(q, "직무", "프로젝트", "기술", "역량")) return QuestionCategory.EXPERIENCE;
        return QuestionCategory.DEFAULT;
    }

    private String inferQuestionIntent(String question) {
        String q = question == null ? "" : question;
        if (containsAny(q, "성장", "학교활동", "학창", "교내외")) {
            return "GROWTH_NARRATIVE";
        }
        if (containsAny(q, "단점", "약점", "보완점", "개선할 점")) {
            return "PERSONALITY_WEAKNESS";
        }
        if (containsAny(q, "성격", "장점", "강점", "성향")) {
            return "PERSONALITY_STRENGTH";
        }
        if (containsAny(q, "일하는 방식", "업무 스타일", "협업 스타일", "스타일")) {
            return "WORK_STYLE";
        }
        if (containsAny(q, "가치관", "중요하게 생각", "신념")) {
            return "VALUES_REFLECTION";
        }
        if (containsAny(q, "직무", "역량", "기술", "프로젝트")) {
            return "JOB_COMPETENCY";
        }
        if (containsAny(q, "지원동기", "입사", "회사")) {
            return "MOTIVATION";
        }
        if (containsAny(q, "최근", "트렌드", "동향", "산업", "시장", "AI", "LLM", "생성형")) {
            return "TREND_INSIGHT";
        }
        return "DEFAULT";
    }

    private String inferAnswerPosture(String question) {
        String intent = inferQuestionIntent(question);
        return switch (intent) {
            case "GROWTH_NARRATIVE" -> "LIFE_ARC_REFLECTION";
            case "PERSONALITY_WEAKNESS" -> "WEAKNESS_RECOVERY";
            case "PERSONALITY_STRENGTH", "WORK_STYLE", "VALUES_REFLECTION" -> "TRAIT_REFLECTION";
            case "MOTIVATION" -> "MOTIVATION_FIT";
            default -> "COMPETENCY_PROOF";
        };
    }

    private String defaultEvidencePolicy(String question) {
        String posture = inferAnswerPosture(question);
        return switch (posture) {
            case "LIFE_ARC_REFLECTION" -> "대표 성장 축 1개와 보조 학교활동 1개만 사용하고, 경험 목록이 아니라 시간 흐름과 태도 형성을 설명한다.";
            case "TRAIT_REFLECTION" -> "성향이 드러난 대표 상황 1개를 중심으로 선택, 반응, 주변 영향만 설명한다.";
            case "WEAKNESS_RECOVERY" -> "치명적이지 않은 단점 사례 1개, 즉시 조치, 현재 보완 습관을 사용한다.";
            default -> "질문 의도와 직접 관련된 검증된 경험 단위를 사용한다.";
        };
    }

    private String defaultCompanyConnectionPolicy(String question) {
        String posture = inferAnswerPosture(question);
        if ("LIFE_ARC_REFLECTION".equals(posture) || "TRAIT_REFLECTION".equals(posture) || "WEAKNESS_RECOVERY".equals(posture)) {
            return "LIGHT_FINAL_SENTENCE";
        }
        return "ROLE_RELEVANT";
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
