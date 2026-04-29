package com.resumade.api.workspace.dto;

import java.util.List;

public record DraftAuthenticityReport(
        int experienceDensityScore,
        int authenticityRiskScore,
        int interviewDefensibilityScore,
        List<String> riskFlags,
        List<String> factGaps,
        List<String> verificationQuestions,
        String rewriteDirective,
        String summary
) {
    public DraftAuthenticityReport {
        experienceDensityScore = clamp(experienceDensityScore);
        authenticityRiskScore = clamp(authenticityRiskScore);
        interviewDefensibilityScore = clamp(interviewDefensibilityScore);
        riskFlags = riskFlags == null ? List.of() : List.copyOf(riskFlags);
        factGaps = factGaps == null ? List.of() : List.copyOf(factGaps);
        verificationQuestions = verificationQuestions == null ? List.of() : List.copyOf(verificationQuestions);
        rewriteDirective = rewriteDirective == null ? "" : rewriteDirective.trim();
        summary = summary == null ? "" : summary.trim();
    }

    public boolean requiresRewrite() {
        return experienceDensityScore < 58
                || authenticityRiskScore >= 48
                || interviewDefensibilityScore < 58
                || !factGaps.isEmpty();
    }

    public static DraftAuthenticityReport empty(String summary) {
        return new DraftAuthenticityReportBuilder()
                .experienceDensityScore(0)
                .authenticityRiskScore(100)
                .interviewDefensibilityScore(0)
                .summary(summary)
                .build();
    }

    public static DraftAuthenticityReportBuilder builder() {
        return new DraftAuthenticityReportBuilder();
    }

    private static int clamp(int value) {
        return Math.max(0, Math.min(100, value));
    }

    public static final class DraftAuthenticityReportBuilder {
        private int experienceDensityScore;
        private int authenticityRiskScore;
        private int interviewDefensibilityScore;
        private List<String> riskFlags = List.of();
        private List<String> factGaps = List.of();
        private List<String> verificationQuestions = List.of();
        private String rewriteDirective = "";
        private String summary = "";

        public DraftAuthenticityReportBuilder experienceDensityScore(int value) {
            this.experienceDensityScore = value;
            return this;
        }

        public DraftAuthenticityReportBuilder authenticityRiskScore(int value) {
            this.authenticityRiskScore = value;
            return this;
        }

        public DraftAuthenticityReportBuilder interviewDefensibilityScore(int value) {
            this.interviewDefensibilityScore = value;
            return this;
        }

        public DraftAuthenticityReportBuilder riskFlags(List<String> value) {
            this.riskFlags = value;
            return this;
        }

        public DraftAuthenticityReportBuilder factGaps(List<String> value) {
            this.factGaps = value;
            return this;
        }

        public DraftAuthenticityReportBuilder verificationQuestions(List<String> value) {
            this.verificationQuestions = value;
            return this;
        }

        public DraftAuthenticityReportBuilder rewriteDirective(String value) {
            this.rewriteDirective = value;
            return this;
        }

        public DraftAuthenticityReportBuilder summary(String value) {
            this.summary = value;
            return this;
        }

        public DraftAuthenticityReport build() {
            return new DraftAuthenticityReport(
                    experienceDensityScore,
                    authenticityRiskScore,
                    interviewDefensibilityScore,
                    riskFlags,
                    factGaps,
                    verificationQuestions,
                    rewriteDirective,
                    summary
            );
        }
    }
}
