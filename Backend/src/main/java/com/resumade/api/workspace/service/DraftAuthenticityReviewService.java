package com.resumade.api.workspace.service;

import com.resumade.api.workspace.dto.DraftAuthenticityReport;
import com.resumade.api.workspace.prompt.DraftParams;
import com.resumade.api.workspace.prompt.QuestionCategory;
import com.resumade.api.workspace.prompt.QuestionDraftPlanV3;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
public class DraftAuthenticityReviewService {

    private static final Pattern METRIC_PATTERN = Pattern.compile(
            "(\\d+(?:\\.\\d+)?\\s*(?:%|퍼센트|배|건|명|개|초|ms|분|시간|일|주|개월|년|p|회))",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CHARACTER_CLASS
    );
    private static final Pattern SENTENCE_PATTERN = Pattern.compile("[^.!?\\n]+[.!?]?", Pattern.UNICODE_CHARACTER_CLASS);

    private static final List<String> TRANSITION_PHRASES = List.of(
            "이를 통해", "나아가", "구체적으로는", "구체적으로,", "또한", "더불어", "이러한 경험을 바탕으로", "이번 경험을 통해", "결과적으로"
    );
    private static final List<String> ABSTRACT_WORDS = List.of(
            "혁신", "열정", "최선", "성실", "책임감", "주도적", "적극적", "효율적", "최적화", "시너지", "성장 가능성"
    );
    private static final List<String> STACK_WORDS = List.of(
            "spring", "jpa", "redis", "kafka", "mysql", "mongodb", "aws", "docker", "kubernetes", "react", "next.js", "websocket"
    );
    private static final List<String> ROLE_WORDS = List.of("담당", "맡", "역할", "책임", "제가", "저는", "직접");
    private static final List<String> JUDGMENT_WORDS = List.of("판단", "선택", "검토", "비교", "원인", "근거", "제약", "대안");
    private static final List<String> ACTION_WORDS = List.of("설계", "구현", "개선", "분리", "조정", "정리", "도입", "검증", "분석", "운영");
    private static final List<String> RESULT_WORDS = List.of("결과", "감소", "단축", "증가", "개선", "완료", "해결", "안정", "전환", "확인");

    public DraftAuthenticityReport review(DraftParams params, QuestionDraftPlanV3 plan, String draft) {
        String text = bodyOnly(draft);
        if (text.isBlank()) {
            return DraftAuthenticityReport.empty("검토할 초안 본문이 없습니다.");
        }

        List<String> riskFlags = new ArrayList<>();
        List<String> factGaps = new ArrayList<>();

        int transitionRisk = repeatedTransitionRisk(text, riskFlags);
        int abstractRisk = abstractWordRisk(text, riskFlags);
        int rhythmRisk = sentenceRhythmRisk(text, riskFlags);
        int reportRisk = reportStyleRisk(text, riskFlags);
        int corporateVoiceRisk = corporateVoiceRisk(text, riskFlags, factGaps);
        int stackRisk = stackListRisk(text, riskFlags);
        int passiveRisk = passiveVoiceRisk(text, riskFlags);
        int unsupportedMetricRisk = unsupportedMetricRisk(text, params == null ? "" : params.experienceContext(), factGaps);
        int roleGap = missingEvidenceGap(text, ROLE_WORDS, "본인 역할이 충분히 분리되어 있지 않습니다.", factGaps);
        int judgmentGap = missingEvidenceGap(text, JUDGMENT_WORDS, "판단 근거나 대안 비교가 부족합니다.", factGaps);
        int resultGap = missingEvidenceGap(text, RESULT_WORDS, "결과 또는 관찰 가능한 변화가 부족합니다.", factGaps);

        int evidenceSignals = evidenceSignals(text);
        int density = clamp(38 + (evidenceSignals * 7) - (abstractRisk / 2) - stackRisk - (roleGap / 2));
        int risk = clamp(transitionRisk + abstractRisk + rhythmRisk + reportRisk + corporateVoiceRisk + stackRisk + passiveRisk + unsupportedMetricRisk);
        int defensibility = clamp(52 + (evidenceSignals * 6) - risk / 2 - roleGap - judgmentGap - resultGap - factGaps.size() * 8);

        List<String> verificationQuestions = buildVerificationQuestions(plan, factGaps, text);
        String rewriteDirective = buildRewriteDirective(riskFlags, factGaps, plan);
        String summary = buildSummary(density, risk, defensibility, riskFlags, factGaps);

        return DraftAuthenticityReport.builder()
                .experienceDensityScore(density)
                .authenticityRiskScore(risk)
                .interviewDefensibilityScore(defensibility)
                .riskFlags(distinctLimit(riskFlags, 8))
                .factGaps(distinctLimit(factGaps, 8))
                .verificationQuestions(verificationQuestions)
                .rewriteDirective(rewriteDirective)
                .summary(summary)
                .build();
    }

    private int repeatedTransitionRisk(String text, List<String> riskFlags) {
        int repeatedCount = 0;
        for (String phrase : TRANSITION_PHRASES) {
            int count = countOccurrences(text, phrase);
            if (count >= 2) {
                repeatedCount += count - 1;
            }
        }
        if (repeatedCount > 0) {
            riskFlags.add("동일한 전환어가 반복되어 기계적인 문장 흐름으로 보일 수 있습니다.");
        }
        return Math.min(26, repeatedCount * 9);
    }

    private int abstractWordRisk(String text, List<String> riskFlags) {
        int count = 0;
        for (String word : ABSTRACT_WORDS) {
            count += countOccurrences(text, word);
        }
        if (count >= 5) {
            riskFlags.add("추상 수식어가 많아 실제 경험 밀도가 약해 보일 수 있습니다.");
        }
        return count >= 5 ? Math.min(22, (count - 3) * 4) : 0;
    }

    private int sentenceRhythmRisk(String text, List<String> riskFlags) {
        List<Integer> lengths = sentenceLengths(text);
        if (lengths.size() < 5) {
            return 0;
        }
        double avg = lengths.stream().mapToInt(Integer::intValue).average().orElse(0);
        double variance = lengths.stream()
                .mapToDouble(length -> Math.pow(length - avg, 2))
                .average()
                .orElse(0);
        double stdDev = Math.sqrt(variance);
        if (avg >= 45 && stdDev < 10) {
            riskFlags.add("문장 길이가 지나치게 균일해 자연스러운 리듬이 부족합니다.");
            return 16;
        }
        return 0;
    }

    private int reportStyleRisk(String text, List<String> riskFlags) {
        String lower = text.toLowerCase(Locale.ROOT);
        boolean reportStyle = lower.contains("핵심 역량은 다음")
                || lower.contains("다음과 같습니다")
                || lower.contains("주제:")
                || lower.contains("구체적으로,")
                || lower.contains("이번 경험을 통해")
                || lower.contains("핵심 교훈은")
                || lower.contains("주요 경력")
                || lower.contains("관련 경험")
                || lower.contains("역할, 조치, 결과")
                || lower.contains("1)")
                || lower.contains("2)")
                || lower.contains("3)");
        if (reportStyle) {
            riskFlags.add("보고서식 또는 항목 나열식 표현이 자기소개서 흐름을 해칩니다.");
            return 28;
        }
        return 0;
    }

    private int corporateVoiceRisk(String text, List<String> riskFlags, List<String> factGaps) {
        int count = countOccurrences(text, "당사는")
                + countOccurrences(text, "저희 회사")
                + countOccurrences(text, "저희의 노력")
                + countOccurrences(text, "저희는")
                + countOccurrences(text, "우리는");
        if (count == 0) {
            return 0;
        }
        riskFlags.add("회사 입장이나 집단 주어로 말해 지원자 본인의 역할과 판단이 흐려집니다.");
        factGaps.add("지원자 관점의 1인칭 역할 문장으로 바꿔야 합니다. '당사는/저희/우리는' 표현을 제거하세요.");
        return Math.min(30, 18 + count * 6);
    }

    private int stackListRisk(String text, List<String> riskFlags) {
        String lower = text.toLowerCase(Locale.ROOT);
        int stackCount = 0;
        for (String stack : STACK_WORDS) {
            if (lower.contains(stack)) {
                stackCount++;
            }
        }
        boolean enoughJudgment = containsAny(text, JUDGMENT_WORDS);
        if (stackCount >= 4 && !enoughJudgment) {
            riskFlags.add("기술명이 여러 개 등장하지만 선택 이유나 문제 맥락이 약합니다.");
            return 32;
        }
        if (stackCount >= 6) {
            riskFlags.add("기술 스택 나열처럼 읽힐 위험이 있습니다.");
            return 18;
        }
        return 0;
    }

    private int passiveVoiceRisk(String text, List<String> riskFlags) {
        int passiveCount = countOccurrences(text, "되었습니다") + countOccurrences(text, "이루어졌") + countOccurrences(text, "진행되었습니다");
        if (passiveCount >= 3) {
            riskFlags.add("수동형 표현이 많아 본인의 행동이 흐려집니다.");
            return 14;
        }
        return 0;
    }

    private int unsupportedMetricRisk(String text, String context, List<String> factGaps) {
        Matcher matcher = METRIC_PATTERN.matcher(text);
        int unsupported = 0;
        String safeContext = context == null ? "" : context;
        while (matcher.find()) {
            String metric = matcher.group(1).replaceAll("\\s+", "");
            if (metric.length() < 2) {
                continue;
            }
            String compactContext = safeContext.replaceAll("\\s+", "");
            if (!compactContext.contains(metric)) {
                unsupported++;
                factGaps.add("경험 컨텍스트에서 확인되지 않은 수치일 수 있습니다: " + matcher.group(1).trim());
            }
        }
        return Math.min(30, unsupported * 12);
    }

    private int missingEvidenceGap(String text, List<String> words, String message, List<String> factGaps) {
        if (containsAny(text, words)) {
            return 0;
        }
        factGaps.add(message);
        return 12;
    }

    private int evidenceSignals(String text) {
        int signals = 0;
        signals += containsAny(text, ROLE_WORDS) ? 1 : 0;
        signals += containsAny(text, JUDGMENT_WORDS) ? 1 : 0;
        signals += containsAny(text, ACTION_WORDS) ? 1 : 0;
        signals += containsAny(text, RESULT_WORDS) ? 1 : 0;
        signals += METRIC_PATTERN.matcher(text).find() ? 1 : 0;
        signals += containsAny(text, List.of("왜", "때문", "그래서", "반면", "대신")) ? 1 : 0;
        signals += containsAny(text, List.of("로그", "테스트", "데이터", "문서", "사용자", "팀원", "고객")) ? 1 : 0;
        return signals;
    }

    private List<String> buildVerificationQuestions(QuestionDraftPlanV3 plan, List<String> factGaps, String text) {
        QuestionCategory category = plan == null ? QuestionCategory.DEFAULT : plan.primaryCategory();
        List<String> questions = new ArrayList<>();
        if (!factGaps.isEmpty()) {
            questions.add("본문에서 가장 강한 주장 하나를 실제 면접에서 어떤 근거로 설명할 수 있나요?");
        }
        switch (category) {
            case MOTIVATION -> {
                questions.add("이 회사가 아니면 안 되는 이유를 경쟁사와 비교해 한 문장으로 설명할 수 있나요?");
                questions.add("지원자가 말한 준비 경험이 입사 초기 어떤 업무 장면에 연결되나요?");
            }
            case EXPERIENCE -> {
                questions.add("해당 프로젝트에서 본인이 직접 책임진 범위와 팀이 함께 한 범위를 어떻게 구분하나요?");
                questions.add("기술 선택 당시 검토했던 대안과 선택 기준은 무엇이었나요?");
            }
            case PROBLEM_SOLVING -> {
                questions.add("처음 의심한 원인과 최종 원인이 달랐다면, 무엇으로 확인했나요?");
                questions.add("선택하지 않은 대안은 왜 당시 제약에서 맞지 않았나요?");
            }
            case COLLABORATION -> {
                questions.add("협업 과정에서 실제로 어긋난 기준이나 역할 공백은 무엇이었나요?");
                questions.add("팀 성과 중 본인의 기여라고 말할 수 있는 부분은 어디까지인가요?");
            }
            case PERSONAL_GROWTH -> {
                questions.add("그 경험 이후 실제 행동 습관이 어떻게 달라졌나요?");
                questions.add("최근 프로젝트나 활동에서도 같은 태도가 드러난 사례가 있나요?");
            }
            case CULTURE_FIT -> {
                questions.add("해당 성향이 장점으로 작동하지 않았던 순간과 보완 방식은 무엇인가요?");
                questions.add("그 행동이 팀원, 고객, 사용자에게 어떤 변화를 만들었나요?");
            }
            case TREND_INSIGHT -> {
                questions.add("이 트렌드가 지원 회사의 어떤 서비스나 업무 흐름에 먼저 적용될 수 있나요?");
                questions.add("도입 시 가장 먼저 통제해야 할 리스크나 조건은 무엇인가요?");
            }
            case DEFAULT -> {
                questions.add("문항의 핵심 요구를 한 문장으로 다시 말하면 무엇인가요?");
                questions.add("본문의 경험을 실제 상황, 행동, 결과 순서로 설명할 수 있나요?");
            }
        }
        return distinctLimit(questions, 5);
    }

    private String buildRewriteDirective(List<String> riskFlags, List<String> factGaps, QuestionDraftPlanV3 plan) {
        if (riskFlags.isEmpty() && factGaps.isEmpty()) {
            return "현재 초안의 사실과 중심 서사를 유지하되, 문장 흐름만 조금 더 자연스럽게 정리하세요.";
        }
        List<String> parts = new ArrayList<>();
        parts.add("v3 진정성 검수 결과를 반영해 재작성하세요.");
        parts.add("검증된 경험 컨텍스트 밖의 수치, 기술명, 역할은 제거하거나 보수적으로 표현하세요.");
        parts.add("문항 중심축은 유지하세요: " + (plan == null ? "문항의 핵심 요구" : plan.dominantSpine()));
        if (!riskFlags.isEmpty()) {
            parts.add("문체 위험: " + String.join(" / ", distinctLimit(riskFlags, 4)));
        }
        if (!factGaps.isEmpty()) {
            parts.add("보강 필요: " + String.join(" / ", distinctLimit(factGaps, 4)));
        }
        parts.add("전환어 반복을 줄이고, 역할-판단-행동-결과가 면접에서 설명 가능한 문장으로 이어지게 하세요.");
        return String.join("\n", parts);
    }

    private String buildSummary(
            int density,
            int risk,
            int defensibility,
            List<String> riskFlags,
            List<String> factGaps
    ) {
        if (riskFlags.isEmpty() && factGaps.isEmpty()) {
            return "v3 검수 결과, 경험 근거와 면접 설명 가능성이 안정적입니다.";
        }
        return "v3 검수 결과, 경험 밀도 " + density + "점, 문체 위험 " + risk
                + "점, 면접 방어력 " + defensibility + "점입니다. "
                + "위험 신호 " + riskFlags.size() + "개와 근거 보강 지점 " + factGaps.size() + "개를 확인했습니다.";
    }

    private String bodyOnly(String draft) {
        if (draft == null) {
            return "";
        }
        String normalized = draft.replace("\r\n", "\n").replace('\r', '\n').trim();
        if (normalized.startsWith("[") && normalized.contains("]")) {
            return normalized.substring(normalized.indexOf(']') + 1).trim();
        }
        return normalized;
    }

    private List<Integer> sentenceLengths(String text) {
        List<Integer> lengths = new ArrayList<>();
        Matcher matcher = SENTENCE_PATTERN.matcher(text.replace("\n", "."));
        while (matcher.find()) {
            String sentence = matcher.group().trim();
            if (sentence.length() >= 8) {
                lengths.add(sentence.length());
            }
        }
        return lengths;
    }

    private boolean containsAny(String text, List<String> words) {
        if (text == null || text.isBlank()) {
            return false;
        }
        String lower = text.toLowerCase(Locale.ROOT);
        for (String word : words) {
            if (lower.contains(word.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private int countOccurrences(String text, String needle) {
        if (text == null || text.isBlank() || needle == null || needle.isBlank()) {
            return 0;
        }
        int count = 0;
        int index = 0;
        while ((index = text.indexOf(needle, index)) >= 0) {
            count++;
            index += needle.length();
        }
        return count;
    }

    private List<String> distinctLimit(List<String> values, int limit) {
        Set<String> deduped = new LinkedHashSet<>();
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                deduped.add(value.trim());
            }
            if (deduped.size() >= limit) {
                break;
            }
        }
        return List.copyOf(deduped);
    }

    private int clamp(int value) {
        return Math.max(0, Math.min(100, value));
    }
}
