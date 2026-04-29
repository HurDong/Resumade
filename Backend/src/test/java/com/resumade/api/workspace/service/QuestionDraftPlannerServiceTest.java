package com.resumade.api.workspace.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.resumade.api.workspace.prompt.QuestionDraftPlan;
import com.resumade.api.workspace.prompt.QuestionDraftPlanV3;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.output.Response;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class QuestionDraftPlannerServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void normalizesShortQuestionPlanToOneParagraphAndMergesBlueprint() {
        String plannerJson = """
                {
                  "primaryCategory": "PROBLEM_SOLVING",
                  "compound": true,
                  "primaryIntent": "Solve the issue through collaboration.",
                  "secondaryIntents": ["collaboration"],
                  "lengthBand": "short",
                  "paragraphCount": 4,
                  "answerContract": {
                    "mustInclude": ["problem", "action", "result"],
                    "mustNotOverdo": ["separate every intent into a paragraph"],
                    "successCriteria": ["merged but complete answer"]
                  },
                  "contentUnits": ["problem", "judgment", "collaboration action", "result"],
                  "compressionPlan": ["Merge collaboration into the action/result sentence."],
                  "experienceNeeds": [
                    {
                      "unit": "problem situation",
                      "query": "payment problem diagnosis",
                      "preferredUnitTypes": ["SITUATION", "JUDGMENT"],
                      "intentTags": ["problem solving"]
                    }
                  ],
                  "draftBlueprint": {
                    "targetLength": 300,
                    "paragraphCount": 4,
                    "paragraphs": [
                      {"paragraph": 1, "role": "problem", "units": ["problem"], "targetChars": 80},
                      {"paragraph": 2, "role": "judgment", "units": ["judgment"], "targetChars": 70},
                      {"paragraph": 3, "role": "action", "units": ["collaboration action"], "targetChars": 90},
                      {"paragraph": 4, "role": "result", "units": ["result"], "targetChars": 60}
                    ]
                  },
                  "framingNote": "Do not over-split.",
                  "requiredElements": ["problem solving"],
                  "ragKeywords": ["payment"]
                }
                """;

        QuestionDraftPlannerService service = new QuestionDraftPlannerService(modelReturning(plannerJson), objectMapper);

        QuestionDraftPlan plan = service.plan(
                "ACME",
                "Backend",
                "Describe a time you solved a problem with collaboration.",
                300,
                240,
                280,
                ""
        );

        assertThat(plan.paragraphCount()).isEqualTo(1);
        assertThat(plan.draftBlueprint().paragraphCount()).isEqualTo(1);
        assertThat(plan.draftBlueprint().paragraphs()).hasSize(1);
        assertThat(plan.draftBlueprint().paragraphs().getFirst().units())
                .containsExactly("problem", "judgment", "collaboration action", "result");
        assertThat(plan.compressionPlan()).contains("Merge collaboration into the action/result sentence.");
        assertThat(plan.experienceNeeds()).hasSize(1);
        assertThat(plan.experienceNeeds().getFirst().preferredUnitTypes())
                .containsExactly("SITUATION", "JUDGMENT");
    }

    @Test
    void usesSafeFallbackWhenPlannerJsonCannotBeParsed() {
        QuestionDraftPlannerService service = new QuestionDraftPlannerService(modelReturning("not-json"), objectMapper);

        QuestionDraftPlan plan = service.plan(
                "ACME",
                "Backend",
                "Tell us about your project experience.",
                600,
                480,
                540,
                ""
        );

        assertThat(plan.paragraphCount()).isEqualTo(2);
        assertThat(plan.lengthBand()).isEqualTo("medium");
        assertThat(plan.experienceNeeds()).isNotEmpty();
    }

    @Test
    void fallbackTreatsGrowthAndSchoolActivitiesAsLifeArcReflection() {
        QuestionDraftPlannerService service = new QuestionDraftPlannerService(modelReturning("not-json"), objectMapper);

        QuestionDraftPlan plan = service.plan(
                "ACME",
                "IT",
                "성장과정과 학교활동을 작성해 주세요.",
                1300,
                1040,
                1170,
                ""
        );

        assertThat(plan.primaryCategory().name()).isEqualTo("PERSONAL_GROWTH");
        assertThat(plan.questionIntent()).isEqualTo("GROWTH_NARRATIVE");
        assertThat(plan.answerPosture()).isEqualTo("LIFE_ARC_REFLECTION");
        assertThat(plan.companyConnectionPolicy()).isEqualTo("LIGHT_FINAL_SENTENCE");
        assertThat(plan.evidencePolicy()).contains("시간 흐름");
        assertThat(plan.experienceNeeds()).extracting(need -> need.unit())
                .contains("성장 출발점", "태도 형성");
    }

    @Test
    void v3PlanKeepsCategoriesAndAddsDominantSpineForCompoundQuestions() {
        QuestionDraftPlannerService service = new QuestionDraftPlannerService(modelReturning("not-json"), objectMapper);

        List<String> questions = List.of(
                "지원동기와 직무 역량을 함께 작성해 주세요.",
                "문제를 해결하며 팀과 협업한 경험을 작성해 주세요.",
                "본인의 장점과 보완점을 구체적으로 작성해 주세요.",
                "최근 AI 트렌드와 이를 직무에 적용할 방안을 작성해 주세요."
        );

        List<QuestionDraftPlanV3> plans = questions.stream()
                .map(question -> service.planV3("ACME", "Backend", question, 700, 560, 630, ""))
                .toList();

        assertThat(plans).hasSize(4);
        assertThat(plans).allSatisfy(plan -> {
            assertThat(plan.primaryCategory()).isNotNull();
            assertThat(plan.dominantSpine()).isNotBlank();
            assertThat(plan.styleGuardrails()).isNotEmpty();
            assertThat(plan.verificationFocus()).isNotEmpty();
            assertThat(plan.categoryDirectives()).isNotEmpty();
        });
        assertThat(plans.get(0).primaryCategory().name()).isEqualTo("MOTIVATION");
        assertThat(plans.get(1).primaryCategory().name()).isEqualTo("PROBLEM_SOLVING");
        assertThat(plans.get(2).primaryCategory().name()).isEqualTo("CULTURE_FIT");
        assertThat(plans.get(3).primaryCategory().name()).isEqualTo("TREND_INSIGHT");
    }

    private ChatLanguageModel modelReturning(String responseText) {
        return new ChatLanguageModel() {
            @Override
            public Response<AiMessage> generate(List<ChatMessage> messages) {
                return Response.from(AiMessage.from(responseText));
            }
        };
    }
}
