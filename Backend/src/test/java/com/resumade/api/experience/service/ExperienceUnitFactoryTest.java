package com.resumade.api.experience.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.resumade.api.experience.domain.Experience;
import com.resumade.api.experience.domain.ExperienceFacet;
import com.resumade.api.experience.domain.ExperienceUnit;
import com.resumade.api.experience.domain.ExperienceUnitType;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ExperienceUnitFactoryTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ExperienceUnitFactory factory = new ExperienceUnitFactory(objectMapper);

    @Test
    void buildsUnitsFromFacetFieldsWithoutRawContent() throws Exception {
        Experience experience = Experience.builder()
                .title("Payment Server")
                .description("payment reliability")
                .overallTechStack(objectMapper.writeValueAsString(List.of("Spring Boot", "MySQL")))
                .jobKeywords(objectMapper.writeValueAsString(List.of("backend", "problem solving")))
                .questionTypes(objectMapper.writeValueAsString(List.of("problem solving", "collaboration")))
                .rawContent("full raw content must not be repeated as a unit text")
                .originalFileName("payment.md")
                .build();

        ExperienceFacet facet = ExperienceFacet.builder()
                .title("Payment status API improvement")
                .displayOrder(0)
                .situation(objectMapper.writeValueAsString(List.of("Duplicate approval requests caused inconsistent status.")))
                .role(objectMapper.writeValueAsString(List.of("Owned the payment status API design.")))
                .judgment(objectMapper.writeValueAsString(List.of("Reduced the transaction scope before changing the schema.")))
                .actions(objectMapper.writeValueAsString(List.of("Separated status updates into an idempotent flow.")))
                .results(objectMapper.writeValueAsString(List.of("Failure tracking became easier for operators.")))
                .techStack(objectMapper.writeValueAsString(List.of("Transaction")))
                .jobKeywords(objectMapper.writeValueAsString(List.of("data consistency")))
                .questionTypes(objectMapper.writeValueAsString(List.of("problem solving")))
                .build();
        experience.addFacet(facet);

        List<ExperienceUnit> units = factory.buildUnits(experience);

        assertThat(units).extracting(ExperienceUnit::getUnitType)
                .contains(
                        ExperienceUnitType.SITUATION,
                        ExperienceUnitType.ROLE,
                        ExperienceUnitType.JUDGMENT,
                        ExperienceUnitType.ACTION,
                        ExperienceUnitType.RESULT,
                        ExperienceUnitType.TECH_STACK,
                        ExperienceUnitType.QUESTION_TYPE
                );
        assertThat(units).extracting(ExperienceUnit::getText)
                .doesNotContain("full raw content must not be repeated as a unit text");
        assertThat(units).allSatisfy(unit -> {
            assertThat(unit.getTechStack()).contains("Transaction");
            assertThat(unit.getJobKeywords()).contains("data consistency");
            assertThat(unit.getQuestionTypes()).contains("problem solving");
        });
    }

    @Test
    void buildsUnitIndexSourcesWithoutRepeatingRawContent() throws Exception {
        Experience experience = paymentExperience();
        ExperienceFacet facet = paymentFacet();
        experience.addFacet(facet);
        experience.replaceUnits(factory.buildUnits(experience));
        ReflectionTestUtils.setField(experience, "id", 1L);
        ReflectionTestUtils.setField(facet, "id", 10L);
        for (int i = 0; i < experience.getUnits().size(); i++) {
            ReflectionTestUtils.setField(experience.getUnits().get(i), "id", (long) i + 100L);
        }

        ExperienceService service = new ExperienceService(null, null, null, factory, null, objectMapper);

        @SuppressWarnings("unchecked")
        List<Object> indexSources = ReflectionTestUtils.invokeMethod(service, "buildIndexSources", experience);

        assertThat(indexSources).isNotEmpty();
        for (Object source : indexSources) {
            String content = ReflectionTestUtils.invokeMethod(source, "content");
            assertThat(content)
                    .contains("Unit detail")
                    .doesNotContain("full raw content must not be repeated as a unit text");
        }
    }

    private Experience paymentExperience() throws Exception {
        return Experience.builder()
                .title("Payment Server")
                .description("payment reliability")
                .overallTechStack(objectMapper.writeValueAsString(List.of("Spring Boot", "MySQL")))
                .jobKeywords(objectMapper.writeValueAsString(List.of("backend", "problem solving")))
                .questionTypes(objectMapper.writeValueAsString(List.of("problem solving", "collaboration")))
                .rawContent("full raw content must not be repeated as a unit text")
                .originalFileName("payment.md")
                .build();
    }

    private ExperienceFacet paymentFacet() throws Exception {
        return ExperienceFacet.builder()
                .title("Payment status API improvement")
                .displayOrder(0)
                .situation(objectMapper.writeValueAsString(List.of("Duplicate approval requests caused inconsistent status.")))
                .role(objectMapper.writeValueAsString(List.of("Owned the payment status API design.")))
                .judgment(objectMapper.writeValueAsString(List.of("Reduced the transaction scope before changing the schema.")))
                .actions(objectMapper.writeValueAsString(List.of("Separated status updates into an idempotent flow.")))
                .results(objectMapper.writeValueAsString(List.of("Failure tracking became easier for operators.")))
                .techStack(objectMapper.writeValueAsString(List.of("Transaction")))
                .jobKeywords(objectMapper.writeValueAsString(List.of("data consistency")))
                .questionTypes(objectMapper.writeValueAsString(List.of("problem solving")))
                .build();
    }
}
