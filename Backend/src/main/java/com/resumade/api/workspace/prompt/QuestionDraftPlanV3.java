package com.resumade.api.workspace.prompt;

import java.util.ArrayList;
import java.util.List;

public record QuestionDraftPlanV3(
        QuestionDraftPlan basePlan,
        QuestionCategory primaryCategory,
        boolean compound,
        String dominantSpine,
        List<String> secondaryInjectionPoints,
        List<String> styleGuardrails,
        List<String> verificationFocus,
        List<String> categoryDirectives
) {
    public QuestionDraftPlanV3 {
        primaryCategory = primaryCategory == null ? QuestionCategory.DEFAULT : primaryCategory;
        dominantSpine = dominantSpine == null ? "" : dominantSpine.trim();
        secondaryInjectionPoints = secondaryInjectionPoints == null ? List.of() : List.copyOf(secondaryInjectionPoints);
        styleGuardrails = styleGuardrails == null ? List.of() : List.copyOf(styleGuardrails);
        verificationFocus = verificationFocus == null ? List.of() : List.copyOf(verificationFocus);
        categoryDirectives = categoryDirectives == null ? List.of() : List.copyOf(categoryDirectives);
    }

    public static QuestionDraftPlanV3 from(QuestionDraftPlan basePlan, String question) {
        QuestionDraftPlan safePlan = basePlan == null
                ? QuestionDraftPlanFallback.defaultPlan(question)
                : basePlan;
        QuestionCategory category = safePlan.primaryCategory();
        return new QuestionDraftPlanV3(
                safePlan,
                category,
                safePlan.compound() || !safePlan.secondaryIntents().isEmpty(),
                buildDominantSpine(safePlan),
                buildInjectionPoints(safePlan),
                buildStyleGuardrails(),
                buildVerificationFocus(category),
                buildCategoryDirectives(category)
        );
    }

    public QuestionProfile toProfile() {
        return basePlan.toProfile();
    }

    public String toPromptBlock() {
        StringBuilder sb = new StringBuilder();
        sb.append("primaryCategory: ").append(primaryCategory).append("\n");
        sb.append("questionIntent: ").append(basePlan.questionIntent()).append("\n");
        sb.append("answerPosture: ").append(basePlan.answerPosture()).append("\n");
        sb.append("compound: ").append(compound).append("\n");
        sb.append("dominantSpine: ").append(dominantSpine).append("\n");
        appendList(sb, "secondaryInjectionPoints", secondaryInjectionPoints);
        appendList(sb, "styleGuardrails", styleGuardrails);
        appendList(sb, "verificationFocus", verificationFocus);
        appendList(sb, "categoryDirectives", categoryDirectives);
        appendList(sb, "requiredElements", basePlan.requiredElements());
        appendList(sb, "contentUnits", basePlan.contentUnits());
        appendList(sb, "compressionPlan", basePlan.compressionPlan());
        if (basePlan.answerContract() != null) {
            appendList(sb, "mustInclude", basePlan.answerContract().mustInclude());
            appendList(sb, "mustNotOverdo", basePlan.answerContract().mustNotOverdo());
            appendList(sb, "successCriteria", basePlan.answerContract().successCriteria());
        }
        if (basePlan.draftBlueprint() != null) {
            sb.append("targetLength: ").append(basePlan.draftBlueprint().targetLength()).append("\n");
            sb.append("paragraphCount: ").append(basePlan.draftBlueprint().paragraphCount()).append("\n");
            for (DraftBlueprint.ParagraphPlan paragraph : basePlan.draftBlueprint().paragraphs()) {
                sb.append("- paragraph ").append(paragraph.paragraph())
                        .append(": ").append(paragraph.role())
                        .append(" / units=").append(String.join(", ", paragraph.units()))
                        .append(" / targetChars=").append(paragraph.targetChars())
                        .append("\n");
            }
        }
        return sb.toString().trim();
    }

    private static void appendList(StringBuilder sb, String label, List<String> values) {
        if (values == null || values.isEmpty()) {
            return;
        }
        sb.append(label).append(":\n");
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                sb.append("- ").append(value.trim()).append("\n");
            }
        }
    }

    private static String buildDominantSpine(QuestionDraftPlan plan) {
        String primaryIntent = plan.primaryIntent();
        if (primaryIntent != null && !primaryIntent.isBlank()) {
            return primaryIntent;
        }
        return switch (plan.primaryCategory()) {
            case MOTIVATION -> "회사 고유 근거와 지원자의 준비 경험을 연결해, 왜 지금 이 회사와 직무인지 설득한다.";
            case EXPERIENCE -> "검증 가능한 직무 경험 하나를 중심으로 문제, 역할, 판단, 실행, 결과를 연결한다.";
            case PROBLEM_SOLVING -> "문제의 원인 진단과 선택 기준을 중심축으로 삼고 결과보다 판단 과정을 설득한다.";
            case COLLABORATION -> "공동 목표와 역할 분담, 실제 조율 행동, 팀 성과와 개인 기여를 분리해 보여준다.";
            case PERSONAL_GROWTH -> "한 사람의 태도가 형성된 흐름을 보여주고 현재 일하는 방식으로 연결한다.";
            case CULTURE_FIT -> "성향이나 가치관을 실제 상황에서의 선택과 반응, 주변 영향으로 증명한다.";
            case TREND_INSIGHT -> "외부 기술/산업 흐름을 먼저 세우고 회사 적용 장면과 조건을 제시한다.";
            case DEFAULT -> "문항의 가장 강한 요구 하나를 중심축으로 삼고 나머지 요구는 서사 안에 흡수한다.";
        };
    }

    private static List<String> buildInjectionPoints(QuestionDraftPlan plan) {
        List<String> points = new ArrayList<>();
        for (String intent : plan.secondaryIntents()) {
            points.add("보조 요구 '" + intent + "'는 별도 문단 제목으로 분리하지 말고 행동, 결과, 회고 문장 중 필요한 위치에 삽입한다.");
        }
        for (String required : plan.requiredElements()) {
            points.add("필수 요소 '" + required + "'를 1개 이상의 구체 문장으로 통합한다.");
        }
        if (points.isEmpty() && plan.compound()) {
            points.add("복합 문항의 보조 요구는 중심 서사를 대체하지 않고, 원인/행동/결과/회고 중 자연스러운 위치에 흡수한다.");
        }
        return points;
    }

    private static List<String> buildStyleGuardrails() {
        return List.of(
                "AI 탐지 회피용 난독화, 의도적 오탈자, 어색한 구어체를 넣지 않는다.",
                "과도하게 매끄러운 연결어 반복을 피하고 문장 길이를 자연스럽게 섞는다.",
                "추상 수식어보다 역할, 판단, 행동, 결과, 측정 기준을 우선한다.",
                "수동태와 3인칭 평가자 문체를 줄이고 지원자의 1인칭 행동으로 쓴다.",
                "면접에서 설명할 수 없는 성과, 기술명, 수치, 직책을 만들지 않는다."
        );
    }

    private static List<String> buildVerificationFocus(QuestionCategory category) {
        return switch (category) {
            case MOTIVATION -> List.of("회사 고유 근거", "지원자의 준비 경험", "왜 지금인지", "초기 기여 범위");
            case EXPERIENCE -> List.of("본인 역할", "기술 선택 이유", "실행 순서", "성과와 측정 기준");
            case PROBLEM_SOLVING -> List.of("근본 원인", "방치 시 피해", "대안 비교", "판단 변화");
            case COLLABORATION -> List.of("공동 목표", "역할 분담", "조율 방식", "팀 성과와 개인 기여 분리");
            case PERSONAL_GROWTH -> List.of("성장 흐름", "전환점", "형성된 태도", "현재 행동");
            case CULTURE_FIT -> List.of("성향이 드러난 상황", "선택과 반응", "개선 습관", "팀/고객 영향");
            case TREND_INSIGHT -> List.of("외부 트렌드", "회사 적용 장면", "한계와 조건", "개인 경험의 보조 근거");
            case DEFAULT -> List.of("문항 중심 요구", "검증 가능한 경험", "보조 요구 통합", "면접 설명 가능성");
        };
    }

    private static List<String> buildCategoryDirectives(QuestionCategory category) {
        return switch (category) {
            case MOTIVATION -> List.of("회사 찬양, 안정성, 복지, 브랜드 선호를 중심 동기로 쓰지 않는다.", "회사 맥락 1개와 준비 경험 1개를 연결한다.");
            case EXPERIENCE -> List.of("기술 스택 나열형 문장을 실패로 보고 문제-역할-판단-결과를 우선한다.", "측정 기준 없는 수치는 쓰지 않는다.");
            case PROBLEM_SOLVING -> List.of("어려움 극복담으로 흐르지 않게 원인 확인 방식과 선택 기준을 드러낸다.", "대안 비교는 짧아도 반드시 판단 근거를 남긴다.");
            case COLLABORATION -> List.of("팀이 한 일과 내가 한 일을 분리한다.", "갈등이 없으면 일정 압박, 역할 공백, 정보 불일치 같은 실제 조율 난점을 사용한다.");
            case PERSONAL_GROWTH -> List.of("프로젝트 성과 회고로 변질되지 않게 태도 형성 흐름을 중심에 둔다.", "회사 지원동기 문장으로 길게 마무리하지 않는다.");
            case CULTURE_FIT -> List.of("성향을 선언하지 말고 실제 상황에서의 선택과 반응으로 보여준다.", "약점 문항은 인식, 보완 행동, 이후 적용을 포함한다.");
            case TREND_INSIGHT -> List.of("개인 프로젝트가 아니라 외부 흐름으로 시작한다.", "회사 적용 장면과 도입 조건 또는 리스크를 포함한다.");
            case DEFAULT -> List.of("카테고리 템플릿 대신 문항의 micro-asks를 읽고 중심축 하나를 정한다.", "보조 요구사항은 별도 목록이 아니라 본문 흐름에 흡수한다.");
        };
    }

    private static final class QuestionDraftPlanFallback {
        private static QuestionDraftPlan defaultPlan(String question) {
            return new QuestionDraftPlan(
                    QuestionCategory.DEFAULT,
                    "DEFAULT",
                    "COMPETENCY_PROOF",
                    "질문 의도와 직접 관련된 검증된 경험을 사용한다.",
                    "ROLE_RELEVANT",
                    question != null && (question.contains("및") || question.contains("또는")),
                    "문항의 핵심 요구를 먼저 답한다.",
                    List.of(),
                    "medium",
                    2,
                    new AnswerContract(List.of("문항의 명시 요구사항", "본인의 역할", "구체적 행동", "결과"), List.of(), List.of()),
                    List.of("상황", "역할", "행동", "결과"),
                    List.of("중심 요구를 먼저 쓰고 보조 요구는 행동과 결과 안에 결합한다."),
                    List.of(),
                    new DraftBlueprint(600, 2, List.of()),
                    "",
                    List.of(),
                    List.of()
            );
        }
    }
}
