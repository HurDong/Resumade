package com.resumade.api.workspace.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.resumade.api.workspace.prompt.QuestionDraftPlan;
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

    private ChatLanguageModel modelReturning(String responseText) {
        return new ChatLanguageModel() {
            @Override
            public Response<AiMessage> generate(List<ChatMessage> messages) {
                return Response.from(AiMessage.from(responseText));
            }
        };
    }
}
