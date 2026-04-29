package com.resumade.api.workspace.service;

import com.resumade.api.workspace.dto.DraftAuthenticityReport;
import com.resumade.api.workspace.prompt.DraftParams;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DraftAuthenticityReviewServiceTest {

    private final DraftAuthenticityReviewService service = new DraftAuthenticityReviewService();

    @Test
    void detectsRepeatedTransitionsAndStackListRisk() {
        DraftAuthenticityReport report = service.review(params("Spring Redis Kafka MySQL 수치 없음"), null, """
                [기술 경험]

                저는 Spring Boot, JPA, Redis, Kafka, MySQL을 활용했습니다. 이를 통해 효율적인 시스템을 만들었습니다. 이를 통해 안정성을 높였습니다. 나아가 성장을 이루었습니다. 구체적으로는 여러 기술을 적용했습니다.
                """);

        assertThat(report.authenticityRiskScore()).isGreaterThanOrEqualTo(40);
        assertThat(report.riskFlags()).anySatisfy(flag -> assertThat(flag).contains("전환어"));
        assertThat(report.riskFlags()).anySatisfy(flag -> assertThat(flag).contains("기술"));
        assertThat(report.rewriteDirective()).contains("v3 진정성 검수");
    }

    @Test
    void detectsUnsupportedMetricsAndRoleGap() {
        DraftAuthenticityReport report = service.review(params("Role: 백엔드 담당 | Outcome: 응답 지연 개선"), null, """
                [성과]

                팀 프로젝트에서 시스템을 개선했습니다. 결과적으로 응답 지연을 30% 줄였고 처리량을 2배 높였습니다.
                """);

        assertThat(report.factGaps()).anySatisfy(gap -> assertThat(gap).contains("30%"));
        assertThat(report.factGaps()).anySatisfy(gap -> assertThat(gap).contains("2배"));
        assertThat(report.interviewDefensibilityScore()).isLessThan(70);
    }

    @Test
    void generatesInterviewVerificationQuestions() {
        DraftAuthenticityReport report = service.review(params("Role: 서버 담당 | Judgment: Redis 선택 | Result: 지연 단축"), null, """
                [응답 지연 개선]

                저는 서버 담당자로 Redis 캐시를 선택해 반복 조회 구간을 개선했습니다. 이 선택으로 응답 지연이 줄었고 운영 중 같은 문제가 재현되지 않았습니다.
                """);

        assertThat(report.verificationQuestions()).isNotEmpty();
        assertThat(report.summary()).isNotBlank();
    }

    private DraftParams params(String experienceContext) {
        return DraftParams.builder()
                .company("ACME")
                .position("Backend")
                .questionTitle("직무 경험을 작성해 주세요.")
                .experienceContext(experienceContext)
                .companyContext("")
                .othersContext("")
                .directive("")
                .maxLength(700)
                .minTarget(560)
                .maxTarget(630)
                .build();
    }
}
