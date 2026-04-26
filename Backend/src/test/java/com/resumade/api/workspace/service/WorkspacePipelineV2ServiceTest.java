package com.resumade.api.workspace.service;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

class WorkspacePipelineV2ServiceTest {

    @Test
    void lengthPolicyTargetsFinalCharacterRangeWithoutPreWashShrink() {
        WorkspacePipelineV2Service service = new WorkspacePipelineV2Service(
                null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null
        );

        Object policy = ReflectionTestUtils.invokeMethod(service, "resolveLengthPolicy", 1300, null);

        assertThat((Integer) ReflectionTestUtils.invokeMethod(policy, "minTarget")).isEqualTo(1040);
        assertThat((Integer) ReflectionTestUtils.invokeMethod(policy, "desiredTarget")).isEqualTo(1170);
        assertThat((Integer) ReflectionTestUtils.invokeMethod(policy, "hardLimit")).isEqualTo(1300);
    }

    @Test
    void requestedTargetUsesRequestedCharactersDirectly() {
        WorkspacePipelineV2Service service = new WorkspacePipelineV2Service(
                null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null
        );

        Object policy = ReflectionTestUtils.invokeMethod(service, "resolveLengthPolicy", 1300, 1300);

        assertThat((Integer) ReflectionTestUtils.invokeMethod(policy, "minTarget")).isEqualTo(1170);
        assertThat((Integer) ReflectionTestUtils.invokeMethod(policy, "desiredTarget")).isEqualTo(1300);
        assertThat((Integer) ReflectionTestUtils.invokeMethod(policy, "hardLimit")).isEqualTo(1300);
    }

    @Test
    void finalLengthScoringPrefersAcceptedCandidateClosestToPreferredTarget() {
        WorkspacePipelineV2Service service = new WorkspacePipelineV2Service(
                null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null
        );
        Object policy = ReflectionTestUtils.invokeMethod(service, "resolveLengthPolicy", 1300, null);

        assertThat((Boolean) ReflectionTestUtils.invokeMethod(service, "isFinalLengthOk", 910, policy)).isFalse();
        assertThat((Boolean) ReflectionTestUtils.invokeMethod(service, "isFinalLengthOk", 1100, policy)).isTrue();

        int shortScore = (Integer) ReflectionTestUtils.invokeMethod(service, "finalLengthScore", 910, policy);
        int acceptedScore = (Integer) ReflectionTestUtils.invokeMethod(service, "finalLengthScore", 1100, policy);

        assertThat(acceptedScore).isLessThan(shortScore);
    }
}
