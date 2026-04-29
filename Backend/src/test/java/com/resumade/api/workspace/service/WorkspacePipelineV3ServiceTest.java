package com.resumade.api.workspace.service;

import com.resumade.api.workspace.dto.DraftAuthenticityReport;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class WorkspacePipelineV3ServiceTest {

    @Test
    void completionPayloadContainsQualityReportAndLengthFields() {
        WorkspacePipelineV3Service service = new WorkspacePipelineV3Service(
                null, null, null, null, null, null, null, null, null, null, null, null
        );
        DraftAuthenticityReport report = DraftAuthenticityReport.builder()
                .experienceDensityScore(82)
                .authenticityRiskScore(18)
                .interviewDefensibilityScore(77)
                .summary("검수 완료")
                .build();

        Map<String, Object> payload = service.buildCompletionPayload(
                "washed",
                "source",
                report,
                700,
                560,
                630,
                602,
                true,
                null
        );

        assertThat(payload).containsKeys("qualityReport", "sourceDraft", "washedDraft", "lengthOk");
        assertThat(payload.get("qualityReport")).isEqualTo(report);
        assertThat(payload.get("sourceDraft")).isEqualTo("source");
        assertThat(payload.get("washedDraft")).isEqualTo("washed");
        assertThat(payload.get("lengthOk")).isEqualTo(true);
        assertThat(payload.get("aiReviewReport")).isInstanceOf(Map.class);
    }
}
