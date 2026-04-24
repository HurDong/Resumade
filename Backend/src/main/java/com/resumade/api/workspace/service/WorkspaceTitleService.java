package com.resumade.api.workspace.service;

import com.resumade.api.workspace.prompt.QuestionCategory;
import com.resumade.api.workspace.prompt.QuestionProfile;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 자기소개서 본문 생성 후 제목만 별도 LLM 호출로 정제하는 서비스.
 *
 * <p>본문 작성 LLM은 답변의 사실·구조·글자 수에 집중하고, 이 서비스는 완성된 본문을 읽은 뒤
 * 문항 의도와 본문 근거가 함께 드러나는 제목으로 바꿉니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WorkspaceTitleService {

    private static final int TITLE_MIN_VISIBLE_CHARS = 8;
    private static final int TITLE_MAX_VISIBLE_CHARS = 45;
    private static final int CONTEXT_SNIPPET_LIMIT = 2200;
    private static final int OTHERS_SNIPPET_LIMIT = 1400;
    private static final int DIRECTIVE_SNIPPET_LIMIT = 900;

    private static final List<String> GENERIC_TITLE_PATTERNS = List.of(
            "성장경험",
            "문제해결",
            "작업요약",
            "지원동기",
            "기술강점",
            "프로젝트",
            "성장과정",
            "자기소개",
            "성과요약",
            "직무경험",
            "리더십경험",
            "경력비전",
            "프로젝트경험",
            "협업사례",
            "책임감",
            "도전정신",
            "열정",
            "노력",
            "성과",
            "경험"
    );

    private static final List<String> CONCRETE_SIGNALS = List.of(
            "해결", "개선", "구축", "설계", "구현", "운영", "최적화", "분석", "협업", "주도",
            "달성", "검증", "자동화", "안정화", "고도화", "확장", "전환", "개발", "정리", "조율",
            "단축", "감소", "향상", "절감", "완료", "효율", "품질", "응답시간", "오류율", "전환율"
    );

    private static final List<String> EXPERIENCE_GENERIC_SIGNALS = List.of(
            "프로젝트경험", "개발경험", "직무역량", "기술역량", "백엔드개발", "서비스개발", "도전경험", "성장경험"
    );

    private static final List<String> PROBLEM_META_SIGNALS = List.of(
            "문제해결", "도전경험", "실패극복", "위기극복", "해결경험", "어려움극복"
    );

    private static final List<String> COLLABORATION_META_SIGNALS = List.of(
            "협업경험", "팀워크", "소통능력", "갈등해결", "협업역량", "팀프로젝트", "리더십경험"
    );

    private static final List<String> PERSONAL_GROWTH_META_SIGNALS = List.of(
            "성장과정", "가치관", "배움과성장", "나를만든경험", "인생의전환점", "삶의태도"
    );

    private static final List<String> CULTURE_FIT_META_SIGNALS = List.of(
            "조직문화적합성", "조직문화", "문화적합성", "성격의장단점", "장단점", "가치관", "일하는방식",
            "실행력", "고객중심", "오너십"
    );

    private static final List<String> TREND_GENERIC_SIGNALS = List.of(
            "기술트렌드", "산업트렌드", "미래기술", "트렌드", "동향", "디지털전환", "ai혁신", "생성형ai", "신기술"
    );

    private static final List<String> MOTIVATION_META_SIGNALS = List.of(
            "지원동기", "지원이유", "입사이유", "입사후포부", "입사후목표", "합류이유", "왜이회사"
    );

    private final WorkspaceDraftAiService workspaceDraftAiService;

    public String rewriteTitleFromDraft(TitleRewriteRequest request) {
        String normalizedDraft = normalizeTitleSpacing(normalizeLengthText(request.draft())).trim();
        if (normalizedDraft.isBlank()) {
            return "";
        }

        String currentTitleLine = normalizeTitleLine(extractActualTitleLine(normalizedDraft));

        try {
            WorkspaceDraftAiService.DraftResponse rewritten = workspaceDraftAiService.rewriteTitle(
                    safeTrim(request.company()),
                    safeTrim(request.position()),
                    safeTrim(request.questionTitle()),
                    safeTrim(request.companyContext()),
                    normalizedDraft,
                    buildTitleRewriteContext(request));

            String candidateTitleLine = rewritten != null && rewritten.text != null
                    ? normalizeTitleLine(extractActualTitleLine(rewritten.text))
                    : "";

            String rejectionReason = findTitleRejectionReason(candidateTitleLine, request);
            if (rejectionReason == null) {
                log.info("Title rewrite accepted category={} title={}",
                        request.primaryCategory(), safeSnippet(candidateTitleLine, 100));
                return applyTitleLine(normalizedDraft, candidateTitleLine);
            }

            log.info("Title rewrite rejected category={} title={} reason={}",
                    request.primaryCategory(), safeSnippet(candidateTitleLine, 100), rejectionReason);
        } catch (Exception e) {
            log.warn("Title rewrite failed company={} position={} question={}",
                    safeTrim(request.company()), safeTrim(request.position()), safeTrim(request.questionTitle()), e);
        }

        if (findTitleRejectionReason(currentTitleLine, request) == null) {
            return applyTitleLine(normalizedDraft, currentTitleLine);
        }
        return normalizedDraft;
    }

    private String buildTitleRewriteContext(TitleRewriteRequest request) {
        StringBuilder builder = new StringBuilder();

        appendSection(builder, "Title Framing Guide", buildTitleFramingGuide(request));

        QuestionProfile profile = request.profile();
        if (profile != null) {
            StringBuilder profileBlock = new StringBuilder();
            profileBlock.append("Primary category: ").append(profile.primaryCategory()).append("\n");
            profileBlock.append("Compound question: ").append(profile.isCompound()).append("\n");
            if (profile.hasFramingOverride()) {
                profileBlock.append("Framing note:\n").append(profile.framingNote()).append("\n");
            }
            if (!profile.requiredElements().isEmpty()) {
                profileBlock.append("Required elements:\n");
                for (String element : profile.requiredElements()) {
                    profileBlock.append("- ").append(element).append("\n");
                }
            }
            if (!profile.ragKeywords().isEmpty()) {
                profileBlock.append("RAG keywords: ").append(String.join(", ", profile.ragKeywords())).append("\n");
            }
            appendSection(builder, "Question Analysis Profile", profileBlock.toString().trim());
        }

        appendSection(builder, "User Directive For This Draft",
                safeSnippet(request.directive(), DIRECTIVE_SNIPPET_LIMIT));
        appendSection(builder, "Evidence Context To Ground The Title",
                safeSnippet(request.experienceContext(), CONTEXT_SNIPPET_LIMIT));
        appendSection(builder, "Other Question Titles To Avoid Overlapping With",
                safeSnippet(request.othersContext(), OTHERS_SNIPPET_LIMIT));

        if (builder.length() == 0) {
            return "No supporting title context available. Still infer the title from the question and body.";
        }
        return builder.toString();
    }

    private String buildTitleFramingGuide(TitleRewriteRequest request) {
        QuestionCategory category = request.primaryCategory() != null
                ? request.primaryCategory()
                : QuestionCategory.DEFAULT;

        String requiredShape = "Use a concrete evidence-first headline instead of a generic slogan.";
        String preferredPatterns = "- [core evidence] + [strongest role-fit signal]\n"
                + "- [problem or action] + [result or value created]";
        String avoidLine = "Avoid titles that only paraphrase the question or sound like a project retrospective label.";

        switch (category) {
            case MOTIVATION -> {
                requiredShape = "Show one prepared capability or selection criterion that naturally points to the applicant's early contribution direction.";
                preferredPatterns = """
                        - [prepared capability or criterion] + [contribution direction]
                        - [past proof] + [why-this-role value]
                        - [problem awareness or value] + [execution direction]
                        """;
                avoidLine = "Avoid pure achievement-summary titles, abstract company-praise titles, and aspiration-only titles that never show why this company and role are the next step now.";
            }
            case EXPERIENCE -> {
                requiredShape = "Show the owned technical action or decision and the bounded result, not just the project name or a competence slogan.";
                preferredPatterns = """
                        - [technical action or decision] + [measurable result]
                        - [problem] + [solution or architecture choice] + [result]
                        - [owned scope] + [stabilized or improved outcome]
                        """;
                avoidLine = "Avoid project-name-only titles, vague competence slogans, and titles that hide the applicant's actual role, decision, or measurable outcome.";
            }
            case PROBLEM_SOLVING -> {
                requiredShape = "Name the problem pressure or failure point and the resolution or turnaround, not just the final achievement.";
                preferredPatterns = """
                        - [problem pressure or bottleneck] + [resolution]
                        - [root cause or re-diagnosis] + [turnaround result]
                        - [constraint or failure point] + [chosen fix]
                        """;
                avoidLine = "Avoid meta titles like 문제 해결 or 도전 경험, and avoid pure result titles that hide what had to be solved or re-diagnosed.";
            }
            case COLLABORATION -> {
                requiredShape = "Show the shared goal, the applicant's owned role, and the coordination or conflict-handling method through a concrete team scene.";
                preferredPatterns = """
                        - [owned role or coordination action] + [team outcome]
                        - [conflict or blocker] + [resolution method]
                        - [shared goal] + [alignment process]
                        """;
                avoidLine = "Avoid meta titles like 협업 경험 or 팀워크 역량, and avoid titles that only name an individual achievement without the shared goal, team context, or coordination process.";
            }
            case PERSONAL_GROWTH -> {
                requiredShape = "Show one formed value or work principle through a decisive episode and the way it still appears in current behavior.";
                preferredPatterns = """
                        - [formed value] + [current behavior]
                        - [turning-point lesson] + [today's work principle]
                        - [decisive episode] + [formed standard]
                        """;
                avoidLine = "Avoid meta titles like 성장과정 or 가치관, pure company-choice framing, and technical metric headlines that erase the human story or value formation.";
            }
            case CULTURE_FIT -> {
                requiredShape = "Show one value, trait, or working style through a concrete behavior episode and its team or customer impact.";
                preferredPatterns = """
                        - [value or trait] + [behavioral proof]
                        - [customer or team-facing action] + [impact]
                        - [weakness improvement] + [changed behavior]
                        """;
                avoidLine = "Avoid abstract culture praise, trait-only slogans, and meta titles like 성격의 장단점 or 고객 중심 without a concrete behavior trace.";
            }
            case TREND_INSIGHT -> {
                requiredShape = "Name one external issue and the company-side implication or application scene instead of turning it into a generic opinion label.";
                preferredPatterns = """
                        - [external issue or trend] + [company-side implication]
                        - [judgment] + [product, customer, service, or system scene]
                        - [trade-off or condition] + [practical direction]
                        """;
                avoidLine = "Avoid broad theme labels, project-retrospective titles, and abstract AI slogans that never show the applicant's actual angle, evidence, or company-side implication.";
            }
            default -> {
            }
        }

        return """
                Question (raw): %s
                Evaluation intent: Read the Question above and identify what the recruiter is specifically testing. The title must directly address that intent, not just highlight the most impressive body detail.
                Mandatory shape: %s
                Preferred patterns:
                %s
                Avoid:
                %s
                """.formatted(
                safeTrim(request.questionTitle()).isBlank() ? "No question text provided." : safeTrim(request.questionTitle()),
                requiredShape,
                preferredPatterns.stripTrailing(),
                avoidLine);
    }

    private String findTitleRejectionReason(String titleLine, TitleRewriteRequest request) {
        if (!isBracketTitleLine(titleLine)) {
            return "not_bracket_title";
        }

        String core = extractBracketTitleCore(titleLine);
        if (core.isBlank()) {
            return "empty_core";
        }

        int visibleChars = countVisibleChars(core);
        if (visibleChars < TITLE_MIN_VISIBLE_CHARS || visibleChars > TITLE_MAX_VISIBLE_CHARS) {
            return "length_out_of_range(" + visibleChars + ")";
        }

        String normalizedCore = normalizeTitleComparison(core);
        if (normalizedCore.isBlank()) {
            return "blank_after_normalization";
        }

        if (normalizedCore.contains("기타") || normalizedCore.contains("제목")) {
            return "contains_meta_word";
        }

        if (containsNormalized(normalizedCore, request.company())
                || containsNormalized(normalizedCore, request.position())) {
            return "contains_company_or_position";
        }

        String normalizedQuestion = normalizeTitleComparison(request.questionTitle());
        if (isQuestionParaphraseTitle(normalizedCore, normalizedQuestion)) {
            return "too_similar_to_question";
        }

        if (GENERIC_TITLE_PATTERNS.stream().anyMatch(normalizedCore::equals)) {
            return "generic_title_pattern";
        }

        if (!hasConcreteTitleShape(core, normalizedCore)) {
            return "not_concrete_enough";
        }

        return findCategorySpecificRejection(request.primaryCategory(), normalizedCore);
    }

    private String findCategorySpecificRejection(QuestionCategory category, String normalizedTitle) {
        QuestionCategory effectiveCategory = category != null ? category : QuestionCategory.DEFAULT;
        if (effectiveCategory == QuestionCategory.EXPERIENCE) {
            if (isBareMetaTitle(normalizedTitle, EXPERIENCE_GENERIC_SIGNALS)) {
                return "experience_project_or_skill_only_title";
            }
            return null;
        }
        if (effectiveCategory == QuestionCategory.PROBLEM_SOLVING
                && isBareMetaTitle(normalizedTitle, PROBLEM_META_SIGNALS)) {
            return "problem_solving_meta_title";
        }
        if (effectiveCategory == QuestionCategory.COLLABORATION
                && isBareMetaTitle(normalizedTitle, COLLABORATION_META_SIGNALS)) {
            return "collaboration_meta_title";
        }
        if (effectiveCategory == QuestionCategory.PERSONAL_GROWTH
                && isBareMetaTitle(normalizedTitle, PERSONAL_GROWTH_META_SIGNALS)) {
            return "personal_growth_meta_title";
        }
        if (effectiveCategory == QuestionCategory.CULTURE_FIT
                && isBareMetaTitle(normalizedTitle, CULTURE_FIT_META_SIGNALS)) {
            return "culture_fit_meta_title";
        }
        if (effectiveCategory == QuestionCategory.TREND_INSIGHT
                && isBareMetaTitle(normalizedTitle, TREND_GENERIC_SIGNALS)) {
            return "trend_generic_theme_title";
        }
        if (effectiveCategory == QuestionCategory.MOTIVATION
                && isBareMetaTitle(normalizedTitle, MOTIVATION_META_SIGNALS)) {
            return "motivation_meta_title";
        }
        return null;
    }

    private boolean isBareMetaTitle(String normalizedTitle, List<String> signals) {
        if (normalizedTitle == null || normalizedTitle.isBlank()) {
            return false;
        }
        for (String signal : signals) {
            String normalizedSignal = normalizeTitleComparison(signal);
            if (normalizedSignal.isBlank()) {
                continue;
            }
            if (normalizedTitle.equals(normalizedSignal)) {
                return true;
            }
            if (normalizedTitle.contains(normalizedSignal)
                    && normalizedTitle.length() <= normalizedSignal.length() + 4) {
                return true;
            }
        }
        return false;
    }

    private boolean hasConcreteTitleShape(String titleCore, String normalizedTitle) {
        if (titleCore.chars().anyMatch(Character::isDigit)) {
            return true;
        }
        if (titleCore.contains(":")) {
            return true;
        }
        if (CONCRETE_SIGNALS.stream().anyMatch(titleCore::contains)) {
            return true;
        }
        if (containsAny(normalizedTitle, "개발", "엔지니어", "매니저", "기획", "사용자", "분석", "아키텍트", "연구", "pm", "리더")) {
            return true;
        }
        return titleCore.trim().split("\\s+").length >= 3;
    }

    private boolean isQuestionParaphraseTitle(String normalizedCore, String normalizedQuestion) {
        if (normalizedCore == null || normalizedCore.isBlank()
                || normalizedQuestion == null || normalizedQuestion.isBlank()) {
            return false;
        }
        if (normalizedQuestion.contains(normalizedCore) && normalizedCore.length() >= 8) {
            return true;
        }
        int overlap = 0;
        for (int i = 0; i < normalizedCore.length(); i++) {
            char ch = normalizedCore.charAt(i);
            if (normalizedQuestion.indexOf(ch) >= 0) {
                overlap++;
            }
        }
        return normalizedCore.length() >= 10 && overlap >= Math.ceil(normalizedCore.length() * 0.85);
    }

    private String applyTitleLine(String text, String titleLine) {
        String normalizedText = normalizeTitleSpacing(normalizeLengthText(text)).trim();
        String normalizedTitleLine = normalizeTitleLine(titleLine);
        if (normalizedText.isBlank() || !isBracketTitleLine(normalizedTitleLine)) {
            return normalizedText;
        }

        String[] lines = normalizedText.split("\n", -1);
        int firstNonBlankLineIndex = -1;
        for (int i = 0; i < lines.length; i++) {
            if (!lines[i].trim().isBlank()) {
                firstNonBlankLineIndex = i;
                break;
            }
        }
        if (firstNonBlankLineIndex < 0) {
            return normalizedTitleLine;
        }
        if (isBracketTitleLine(lines[firstNonBlankLineIndex].trim())) {
            lines[firstNonBlankLineIndex] = normalizedTitleLine;
            return collapseRepeatedLeadingTitleLines(String.join("\n", lines));
        }
        return collapseRepeatedLeadingTitleLines(normalizedTitleLine + "\n\n" + normalizedText);
    }

    private String collapseRepeatedLeadingTitleLines(String text) {
        String normalized = normalizeTitleSpacing(normalizeLengthText(text)).trim();
        String[] lines = normalized.split("\n", -1);
        int firstTitleIndex = -1;
        String firstTitleKey = "";
        StringBuilder cleaned = new StringBuilder();
        boolean scanningLeadingTitleBlock = true;

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            String trimmed = line.trim();
            if (scanningLeadingTitleBlock && trimmed.isBlank()) {
                if (!cleaned.isEmpty() && cleaned.charAt(cleaned.length() - 1) != '\n') {
                    cleaned.append('\n');
                }
                continue;
            }
            if (scanningLeadingTitleBlock && isBracketTitleLine(trimmed)) {
                String key = normalizeTitleComparison(extractBracketTitleCore(trimmed));
                if (firstTitleIndex < 0) {
                    firstTitleIndex = i;
                    firstTitleKey = key;
                    appendLine(cleaned, trimmed);
                } else if (firstTitleKey.equals(key)) {
                    continue;
                } else {
                    scanningLeadingTitleBlock = false;
                    appendLine(cleaned, line);
                }
                continue;
            }
            scanningLeadingTitleBlock = false;
            appendLine(cleaned, line);
        }
        return normalizeTitleSpacing(cleaned.toString()).trim();
    }

    private void appendLine(StringBuilder builder, String line) {
        if (!builder.isEmpty()) {
            builder.append('\n');
        }
        builder.append(line);
    }

    private String extractActualTitleLine(String draft) {
        String extracted = extractTitleLine(draft);
        return "No title".equals(extracted) ? "" : extracted;
    }

    private String extractTitleLine(String draft) {
        if (draft == null || draft.isBlank()) {
            return "No title";
        }
        String firstLine = normalizeLengthText(draft).lines()
                .map(String::trim)
                .filter(line -> !line.isBlank())
                .findFirst()
                .orElse("");
        return firstLine.isBlank() ? "No title" : firstLine;
    }

    private boolean isBracketTitleLine(String titleLine) {
        if (titleLine == null) {
            return false;
        }
        String trimmed = titleLine.trim();
        return trimmed.startsWith("[") && trimmed.endsWith("]") && trimmed.indexOf('\n') < 0;
    }

    private String extractBracketTitleCore(String titleLine) {
        if (!isBracketTitleLine(titleLine)) {
            return "";
        }
        String trimmed = titleLine.trim();
        return trimmed.substring(1, trimmed.length() - 1).trim();
    }

    private String normalizeTitleLine(String titleLine) {
        if (titleLine == null) {
            return "";
        }
        return titleLine.replace("\r", "").trim();
    }

    private String normalizeTitleComparison(String value) {
        if (value == null) {
            return "";
        }
        return value.toLowerCase()
                .replaceAll("[\\s\\p{Punct}\\p{IsPunctuation}]+", "")
                .trim();
    }

    private boolean containsNormalized(String normalizedTitle, String rawValue) {
        String normalizedValue = normalizeTitleComparison(rawValue);
        return normalizedValue.length() >= 2 && normalizedTitle.contains(normalizedValue);
    }

    private boolean containsAny(String value, List<String> needles) {
        if (value == null || value.isBlank()) {
            return false;
        }
        return needles.stream().anyMatch(value::contains);
    }

    private boolean containsAny(String value, String... needles) {
        if (value == null || value.isBlank()) {
            return false;
        }
        for (String needle : needles) {
            if (value.contains(needle)) {
                return true;
            }
        }
        return false;
    }

    private void appendSection(StringBuilder builder, String title, String body) {
        String safeBody = safeTrim(body);
        if (safeBody.isBlank()) {
            return;
        }
        if (builder.length() > 0) {
            builder.append("\n---\n");
        }
        builder.append("[").append(title).append("]\n").append(safeBody);
    }

    private String normalizeLengthText(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("\r\n", "\n").replace('\r', '\n');
    }

    private String normalizeTitleSpacing(String text) {
        if (text == null) {
            return "";
        }
        return text.replaceAll("[ \t]+\\n", "\n")
                .replaceAll("\\n[ \t]+", "\n")
                .replaceAll("\\n{3,}", "\n\n");
    }

    private int countVisibleChars(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        String normalized = normalizeLengthText(text);
        int count = 0;
        for (int i = 0; i < normalized.length();) {
            int codePoint = normalized.codePointAt(i);
            i += Character.charCount(codePoint);
            if (codePoint == '\n') {
                count++;
                continue;
            }
            if (Character.isISOControl(codePoint)) {
                continue;
            }
            if (Character.getType(codePoint) == Character.FORMAT) {
                continue;
            }
            count++;
        }
        return count;
    }

    private static String safeTrim(String value) {
        return value == null ? "" : value.trim();
    }

    private static String safeSnippet(String value, int maxLen) {
        String safe = safeTrim(value);
        if (safe.length() <= maxLen) {
            return safe;
        }
        return safe.substring(0, maxLen) + "...";
    }

    public record TitleRewriteRequest(
            String draft,
            String company,
            String position,
            String questionTitle,
            QuestionCategory primaryCategory,
            QuestionProfile profile,
            String companyContext,
            String experienceContext,
            String othersContext,
            String directive
    ) {}
}
