package com.resumade.api.workspace.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.resumade.api.experience.domain.Experience;
import com.resumade.api.experience.domain.ExperienceRepository;
import com.resumade.api.experience.service.ExperienceVectorRetrievalService;
import com.resumade.api.infra.sse.Utf8SseSupport;
import com.resumade.api.workspace.domain.QuestionSnapshotRepository;
import com.resumade.api.workspace.domain.SnapshotType;
import com.resumade.api.workspace.domain.WorkspaceQuestion;
import com.resumade.api.workspace.domain.WorkspaceQuestionRepository;
import com.resumade.api.workspace.dto.DraftAnalysisResult;
import com.resumade.api.workspace.dto.SentencePairAnalysisResult;
import com.resumade.api.workspace.dto.TitleSuggestionResponse;
import com.resumade.api.workspace.prompt.ClassificationResult;
import com.resumade.api.workspace.prompt.DraftParams;
import com.resumade.api.workspace.prompt.PromptFactory;
import com.resumade.api.workspace.prompt.QuestionCategory;
import dev.langchain4j.data.message.ChatMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class WorkspaceService {
    private static final long HEARTBEAT_INTERVAL_SECONDS = 8L;
    private static final Pattern REQUESTED_LENGTH_PATTERN = Pattern.compile(
            "([0-9][0-9,]{1,4})\\s*(?:\\uAE00\\uC790\\uC218|\\uAE00\\uC790|\\uC790|characters?|chars?)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern PREFIX_MINIMUM_LENGTH_PATTERN = Pattern.compile(
            "(?:\\uCD5C\\uC18C|minimum|at\\s+least)\\s*([0-9][0-9,]{1,4})\\s*(?:\\uAE00\\uC790\\uC218|\\uAE00\\uC790|\\uC790|characters?|chars?)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern SUFFIX_MINIMUM_LENGTH_PATTERN = Pattern.compile(
            "([0-9][0-9,]{1,4})\\s*(?:\\uAE00\\uC790\\uC218|\\uAE00\\uC790|\\uC790|characters?|chars?)\\s*(?:\\uC774\\uC0C1|\\uCD5C\\uC18C|or\\s+more|minimum)",
            Pattern.CASE_INSENSITIVE);
    private static final int MINIMUM_LENGTH_EXPANSION_ATTEMPTS = 3;
    private static final int MINIMUM_LENGTH_DRAFT_FAMILIES = 3;
    private static final String LENGTH_RETRY_MARKER = "[LENGTH_RETRY]";
    private static final String LENGTH_REGENERATION_MARKER = "[LENGTH_REGENERATION]";
    private static final String NO_EXTRA_USER_DIRECTIVE = "No extra user directive.";
    private static final String STAGE_RAG = "RAG";
    private static final String STAGE_DRAFT = "DRAFT";
    private static final String STAGE_WASH = "WASH";
    private static final String STAGE_PATCH = "PATCH";
    private static final String STAGE_DONE = "DONE";
    private static final double DEFAULT_TARGET_MIN_RATIO = 0.80;
    private static final double DEFAULT_TARGET_MAX_RATIO = 1.00;
    private static final int TITLE_MIN_VISIBLE_CHARS = 8;
    private static final int TITLE_MAX_VISIBLE_CHARS = 45;
    private static final Pattern REDUNDANT_TITLE_BLOCK_PATTERN = Pattern.compile(
            "\\n\\s*\\n(?:\\*\\*[^*\\n]+\\*\\*\\s*)?(?:\\[[^\\]\\n]+\\])");
    private static final int REDUNDANT_TITLE_BLOCK_MIN_PREFIX_CHARS = 180;
    private static final List<String> GENERIC_TITLE_PATTERNS = List.of(
            "\uC131\uC7A5\uACBD\uD5D8",
            "\uBB38\uC81C\uD574\uACB0",
            "\uC791\uC5C5\uC694\uC57D",
            "\uC9C0\uC6D0\uB3D9\uAE30",
            "\uAE30\uC220\uAC15\uC810",
            "\uD504\uB85C\uC81D\uD2B8",
            "\uC131\uC7A5\uACFC\uC815",
            "\uC790\uAE30\uC18C\uAC1C",
            "\uC131\uACFC\uC694\uC57D",
            "\uC9C1\uBB34\uACBD\uD5D8",
            "\uB9AC\uB354\uC2ED\uACBD\uD5D8",
            "\uACBD\uB825\uBE44\uC804",
            "\uD504\uB85C\uC81D\uD2B8\uACBD\uD5D8",
            "\uD611\uC5C5\uC0AC\uB840",
            "\uCC45\uC784\uAC10",
            "\uB3C4\uC804\uC815\uC2E0",
            "\uC5F4\uC815",
            "\uB178\uB825",
            "\uC131\uACFC",
            "\uACBD\uD5D8");
    private static final List<String> TITLE_ACTION_SIGNALS = List.of(
            "\uD574\uACB0",
            "\uAC1C\uC120",
            "\uAD6C\uCD95",
            "\uC124\uACC4",
            "\uAD6C\uD604",
            "\uC6B4\uC601",
            "\uCD5C\uC801\uD654",
            "\uBD84\uC11D",
            "\uD611\uC5C5",
            "\uC8FC\uB3C4",
            "\uB2EC\uC131",
            "\uAC80\uC99D",
            "\uC790\uB3D9\uD654",
            "\uC548\uC815\uD654",
            "\uACE0\uB3C4\uD654",
            "\uD655\uC7A5",
            "\uC804\uD658",
            "\uAC1C\uBC1C");
    private static final List<String> MOTIVATION_TITLE_BRIDGE_SIGNALS = List.of(
            "\uBC14\uD0D5\uC73C\uB85C",
            "\uAE30\uBC18\uC73C\uB85C",
            "\uD1A0\uB300\uB85C",
            "\uD1B5\uD574",
            "\uC5F0\uACB0",
            "\uC0B4\uB824",
            "\uAE30\uC5EC",
            "\uD5A5\uC0C1",
            "\uAC1C\uC120",
            "\uAC00\uCE58",
            "\uD6A8\uC728",
            "\uC811\uADFC\uC131",
            "\uACE0\uB3C4\uD654",
            "\uC548\uC815\uD654",
            "\uC2E4\uD589");
    private static final List<String> MOTIVATION_TITLE_VALUE_SIGNALS = List.of(
            "\uAE30\uC5EC",
            "\uD5A5\uC0C1",
            "\uAC1C\uC120",
            "\uD6A8\uC728",
            "\uC811\uADFC\uC131",
            "\uC548\uC815\uC131",
            "\uACE0\uB3C4\uD654",
            "\uC0AC\uC6A9\uC131",
            "\uC6B4\uC601",
            "\uD65C\uC6A9",
            "\uAC00\uCE58",
            "\uC815\uC791",
            "\uC758\uC0AC\uACB0\uC815");
    private static final List<String> MOTIVATION_TITLE_PLAN_SIGNALS = List.of(
            "\uACC4\uD68D",
            "\uC2E4\uD589",
            "\uC815\uCC29",
            "\uACE0\uB3C4\uD654",
            "\uC548\uC815\uD654",
            "\uAC1C\uC120",
            "\uD5A5\uC0C1",
            "\uC6B4\uC601");
    private static final List<String> MOTIVATION_TITLE_META_SIGNALS = List.of(
            "\uC9C0\uC6D0\uB3D9\uAE30",
            "\uC9C0\uC6D0\uC774\uC720",
            "\uC785\uC0AC\uC774\uC720",
            "\uC785\uC0AC\uD6C4\uD3EC\uBD80",
            "\uC785\uC0AC\uD6C4\uBAA9\uD45C",
            "\uD569\uB958\uC774\uC720",
            "\uC65C\uC774\uD68C\uC0AC");
    private static final List<String> MOTIVATION_TITLE_GENERIC_PRAISE_SIGNALS = List.of(
            "\uAE00\uB85C\uBC8C",
            "\uC120\uB3C4",
            "\uCD5C\uACE0",
            "\uD601\uC2E0",
            "\uC131\uC7A5\uAC00\uB2A5\uC131",
            "\uBBF8\uB798",
            "\uBE44\uC804",
            "\uB3C4\uC804\uC815\uC2E0");
    private static final List<String> EXPERIENCE_TITLE_GENERIC_SIGNALS = List.of(
            "\uD504\uB85C\uC81D\uD2B8\uACBD\uD5D8",
            "\uAC1C\uBC1C\uACBD\uD5D8",
            "\uC9C1\uBB34\uC5ED\uB7C9",
            "\uAE30\uC220\uC5ED\uB7C9",
            "\uBC31\uC5D4\uB4DC\uAC1C\uBC1C",
            "\uC11C\uBE44\uC2A4\uAC1C\uBC1C",
            "\uB3C4\uC804\uACBD\uD5D8",
            "\uC131\uC7A5\uACBD\uD5D8");
    private static final List<String> EXPERIENCE_TITLE_ACTION_SIGNALS = List.of(
            "\uC124\uACC4",
            "\uAD6C\uD604",
            "\uC7AC\uC124\uACC4",
            "\uC790\uB3D9\uD654",
            "\uCD5C\uC801\uD654",
            "\uB9AC\uD329\uD1A0\uB9C1",
            "\uD45C\uC900\uD654",
            "\uAD6C\uCD95",
            "\uB3C4\uC785",
            "\uBD84\uC11D",
            "\uAC1C\uC120",
            "\uC548\uC815\uD654");
    private static final List<String> EXPERIENCE_TITLE_RESULT_SIGNALS = List.of(
            "\uB2E8\uCD95",
            "\uAC10\uC18C",
            "\uD5A5\uC0C1",
            "\uC808\uAC10",
            "\uC548\uC815\uD654",
            "\uACE0\uB3C4\uD654",
            "\uAC1C\uC120",
            "\uB2EC\uC131",
            "\uC644\uB8CC",
            "\uD6A8\uC728",
            "\uD488\uC9C8",
            "\uC751\uB2F5\uC2DC\uAC04",
            "\uC624\uB958\uC728");
    private static final List<String> COLLABORATION_TITLE_META_SIGNALS = List.of(
            "\uD611\uC5C5\uACBD\uD5D8",
            "\uD300\uC6CC\uD06C",
            "\uC18C\uD1B5\uB2A5\uB825",
            "\uAC08\uB4F1\uD574\uACB0",
            "\uD611\uC5C5\uC5ED\uB7C9",
            "\uD300\uD504\uB85C\uC81D\uD2B8",
            "\uB9AC\uB354\uC2ED\uACBD\uD5D8");
    private static final List<String> COLLABORATION_TITLE_ROLE_SIGNALS = List.of(
            "\uD300\uC7A5",
            "\uC2E4\uBB34\uB2F4\uB2F9",
            "\uAE30\uD68D",
            "\uC870\uC728",
            "\uB2F4\uB2F9",
            "\uC6B4\uC601",
            "\uC911\uC7AC",
            "\uB9AC\uB4DC");
    private static final List<String> COLLABORATION_TITLE_PROCESS_SIGNALS = List.of(
            "\uC870\uC728",
            "\uC124\uB4DD",
            "\uC911\uC7AC",
            "\uD569\uC758",
            "\uC815\uB9AC",
            "\uBB38\uC11C\uD654",
            "\uACF5\uC720",
            "\uC815\uB82C",
            "\uD53C\uB4DC\uBC31",
            "\uD68C\uC758",
            "\uC2E4\uD5D8",
            "\uC2DC\uBBAC\uB808\uC774\uC158");
    private static final List<String> COLLABORATION_TITLE_OUTCOME_SIGNALS = List.of(
            "\uD5A5\uC0C1",
            "\uAC10\uC18C",
            "\uB2E8\uCD95",
            "\uB2EC\uC131",
            "\uD655\uBCF4",
            "\uC644\uC218",
            "\uC815\uCC29",
            "\uC548\uC815",
            "\uD488\uC9C8",
            "\uD6A8\uC728",
            "\uC751\uB300");
    private static final List<String> PERSONAL_GROWTH_TITLE_META_SIGNALS = List.of(
            "\uC131\uC7A5\uACFC\uC815",
            "\uAC00\uCE58\uAD00",
            "\uBC30\uC6C0\uACFC\uC131\uC7A5",
            "\uB098\uB97C\uB9CC\uB4E0\uACBD\uD5D8",
            "\uC778\uC0DD\uC758\uC804\uD658\uC810",
            "\uC0B6\uC758\uD0DC\uB3C4");
    private static final List<String> PERSONAL_GROWTH_TITLE_VALUE_SIGNALS = List.of(
            "\uCC45\uC784\uAC10",
            "\uAE30\uC900",
            "\uC6D0\uCE59",
            "\uD0DC\uB3C4",
            "\uC2DC\uC120",
            "\uC2B5\uAD00",
            "\uB07C\uAE30",
            "\uACBD\uCCAD",
            "\uBC30\uB824",
            "\uC2E4\uD589",
            "\uC815\uB9AC",
            "\uC2E0\uB8B0",
            "\uC790\uC138");
    private static final List<String> PERSONAL_GROWTH_TITLE_BEHAVIOR_SIGNALS = List.of(
            "\uAE30\uB85D",
            "\uC9C8\uBB38",
            "\uBCF5\uAE30",
            "\uC815\uB9AC",
            "\uB4E3\uAE30",
            "\uBC84\uD2F0\uAE30",
            "\uBC14\uAFB8\uAE30",
            "\uC804\uD658",
            "\uC774\uC5B4\uAC00\uAE30",
            "\uB2E4\uC2DC",
            "\uB05D\uAE4C\uC9C0",
            "\uC2E4\uD589",
            "\uAC1C\uC120");
    private static final List<String> CULTURE_FIT_TITLE_META_SIGNALS = List.of(
            "\uC870\uC9C1\uBB38\uD654\uC801\uD569\uC131",
            "\uC870\uC9C1\uBB38\uD654",
            "\uBB38\uD654\uC801\uD569\uC131",
            "\uC131\uACA9\uC758\uC7A5\uB2E8\uC810",
            "\uC7A5\uB2E8\uC810",
            "\uAC00\uCE58\uAD00",
            "\uC77C\uD558\uB294\uBC29\uC2DD",
            "\uC2E4\uD589\uB825",
            "\uACE0\uAC1D\uC911\uC2EC",
            "\uC624\uB108\uC2ED");
    private static final List<String> CULTURE_FIT_TITLE_GENERIC_TRAIT_SIGNALS = List.of(
            "\uCC45\uC784\uAC10",
            "\uC5F4\uC815",
            "\uC131\uC2E4",
            "\uAF3C\uAF3C\uD568",
            "\uC2E0\uC911\uD568",
            "\uC801\uADF9\uC131",
            "\uC18C\uD1B5\uB2A5\uB825",
            "\uB9AC\uB354\uC2ED",
            "\uC624\uB108\uC2ED",
            "\uACE0\uAC1D\uC911\uC2EC",
            "\uBE60\uB978\uC2E4\uD589",
            "\uB3C4\uC804\uC815\uC2E0");
    private static final List<String> CULTURE_FIT_TITLE_BEHAVIOR_SIGNALS = List.of(
            "\uBC18\uC601",
            "\uC870\uC728",
            "\uC804\uD658",
            "\uAC1C\uC120",
            "\uB3C4\uC785",
            "\uC870\uC815",
            "\uC124\uB4DD",
            "\uC815\uB9AC",
            "\uBB38\uC11C\uD654",
            "\uACF5\uC720",
            "\uC2E4\uD589",
            "\uC2E4\uD5D8",
            "\uAC80\uC99D",
            "\uACBD\uCCAD",
            "\uC644\uD654",
            "\uB300\uC751",
            "\uC6B4\uC601",
            "\uC8FC\uB3C4");
    private static final List<String> CULTURE_FIT_TITLE_IMPACT_SIGNALS = List.of(
            "\uC99D\uAC00",
            "\uAC10\uC18C",
            "\uD5A5\uC0C1",
            "\uAC1C\uC120",
            "\uB2EC\uC131",
            "\uC815\uCC29",
            "\uC548\uC815",
            "\uB9CC\uC871\uB3C4",
            "\uC804\uD658\uC728",
            "\uBB38\uC758\uC728",
            "\uCC38\uC5EC\uC728",
            "\uC2E0\uB8B0",
            "\uD569\uC758",
            "\uC644\uB8CC");
    private static final List<String> PROBLEM_SOLVING_TITLE_META_SIGNALS = List.of(
            "\uBB38\uC81C\uD574\uACB0",
            "\uB3C4\uC804\uACBD\uD5D8",
            "\uC2E4\uD328\uADF9\uBCF5",
            "\uC704\uAE30\uADF9\uBCF5",
            "\uD574\uACB0\uACBD\uD5D8",
            "\uC5B4\uB824\uC6C0\uADF9\uBCF5");
    private static final List<String> PROBLEM_SOLVING_TITLE_PROBLEM_SIGNALS = List.of(
            "\uBCD1\uBAA9",
            "\uC2E4\uD328\uC728",
            "\uC9C0\uC5F0",
            "\uC624\uB958",
            "\uACE0\uAC08",
            "\uCDA9\uB3CC",
            "\uB204\uB77D",
            "\uC815\uC9C0",
            "\uBD88\uB7C9",
            "\uACE0\uC7A5",
            "\uB099\uC81C",
            "\uC704\uAE30",
            "\uD3B8\uCC28",
            "\uACFC\uBD80\uD558",
            "\uC624\uC791\uB3D9",
            "\uD63C\uC120",
            "\uBD88\uC77C\uCE58");
    private static final List<String> PROBLEM_SOLVING_TITLE_RESOLUTION_SIGNALS = List.of(
            "\uC7AC\uC9C4\uB2E8",
            "\uC7AC\uC124\uACC4",
            "\uBCF5\uAD6C",
            "\uC804\uD658",
            "\uAC1C\uC120",
            "\uC218\uC815",
            "\uC870\uC815",
            "\uD574\uC18C",
            "\uBCF5\uC6D0",
            "\uD45C\uC900\uD654",
            "\uC7AC\uAD6C\uC131",
            "\uAD50\uCCB4",
            "\uCD5C\uC801\uD654",
            "\uBD84\uC11D",
            "\uC644\uC8FC");
    private static final List<String> PROBLEM_SOLVING_TITLE_JUDGMENT_SIGNALS = List.of(
            "\uC6D0\uC778",
            "\uD310\uB2E8",
            "\uC120\uD0DD",
            "\uAE30\uC900",
            "\uB9AC\uC2A4\uD06C",
            "\uC81C\uC57D",
            "\uC6B0\uC120\uC21C\uC704",
            "\uB300\uC751");
    private static final List<String> TREND_INSIGHT_TITLE_GENERIC_THEME_SIGNALS = List.of(
            "\uAE30\uC220\uD2B8\uB80C\uB4DC",
            "\uC0B0\uC5C5\uD2B8\uB80C\uB4DC",
            "\uBBF8\uB798\uAE30\uC220",
            "\uD2B8\uB80C\uB4DC",
            "\uB3D9\uD5A5",
            "\uB514\uC9C0\uD138\uC804\uD658",
            "ai\uD601\uC2E0",
            "\uC0DD\uC131\uD615ai",
            "\uC2E0\uAE30\uC220");
    private static final List<String> TREND_INSIGHT_TITLE_IMPLICATION_SIGNALS = List.of(
            "\uD568\uC758",
            "\uC601\uD5A5",
            "\uBCC0\uD654",
            "\uC804\uD658",
            "\uD655\uC0B0",
            "\uB300\uC751",
            "\uACE0\uB3C4\uD654",
            "\uC7AC\uD3B8",
            "\uC2E0\uB8B0",
            "\uACBD\uC7C1\uB825",
            "\uC6B0\uC120\uC21C\uC704",
            "\uB9AC\uC2A4\uD06C",
            "\uAE30\uD68C",
            "\uC801\uC6A9");
    private static final List<String> TREND_INSIGHT_TITLE_SCENE_SIGNALS = List.of(
            "\uC11C\uBE44\uC2A4",
            "\uACE0\uAC1D",
            "\uC2DC\uC2A4\uD15C",
            "\uC5C5\uBB34",
            "\uC6B4\uC601",
            "\uC81C\uD488",
            "\uD50C\uB7AB\uD3FC",
            "\uC0C1\uB2F4",
            "\uD488\uC9C8",
            "\uACF5\uC815",
            "\uBA54\uBAA8\uB9AC",
            "\uC815\uBE44",
            "\uACB0\uC81C",
            "\uBB38\uC11C",
            "\uAC80\uC99D");
    private static final List<String> TREND_INSIGHT_TITLE_EVIDENCE_SIGNALS = List.of(
            "\uB370\uC774\uD130",
            "\uC9C0\uD45C",
            "\uD1B5\uACC4",
            "\uC815\uCC45",
            "\uADDC\uC81C",
            "\uAC80\uC0C9",
            "\uC218\uC694",
            "\uADFC\uAC70",
            "\uBE44\uAD50",
            "\uC2E0\uD638");
    private static final List<String> TITLE_ACHIEVEMENT_SUMMARY_SIGNALS = List.of(
            "\uB2E8\uCD95",
            "\uCD5C\uC801\uD654",
            "\uD29C\uB2DD",
            "\uAD6C\uCD95",
            "\uC124\uACC4",
            "\uAD6C\uD604",
            "\uBD84\uC11D",
            "\uB2EC\uC131",
            "\uC751\uB2F5\uC2DC\uAC04",
            "\uBCD1\uBAA9",
            "\uC131\uB2A5",
            "\uC9C0\uC5F0",
            "\uCFFC\uB9AC",
            "\uC778\uB371\uC2F1",
            "\uAC80\uC0C9");

    private static class SseConnectionClosedException extends RuntimeException {
        private SseConnectionClosedException(Throwable cause) {
            super(cause);
        }
    }

    private final ExperienceRepository experienceRepository;
    private final WorkspaceDraftAiService workspaceDraftAiService;
    private final WorkspacePatchAiService workspacePatchAiService;
    private final TranslationService translationService;
    private final ObjectMapper objectMapper;
    private final WorkspaceQuestionRepository questionRepository;
    private final QuestionSnapshotService questionSnapshotService;
    private final ExperienceVectorRetrievalService experienceVectorRetrievalService;

    // ── 1차 고도화: Prompt Strategy + Classifier 파이프라인 ──────────────────
    private final QuestionClassifierService questionClassifierService;
    private final PromptFactory promptFactory;
    private final StrategyDraftGeneratorService strategyDraftGeneratorService;
    private final WorkspaceTaskCache workspaceTaskCache;
    private final com.resumade.api.experience.domain.PersonalStoryRepository personalStoryRepository;

    public List<com.resumade.api.workspace.dto.ExperienceContextResponse.ContextItem> getMatchedExperiences(
            Long questionId, String customQuery) {
        WorkspaceQuestion question = questionRepository.findById(questionId)
                .orElseThrow(() -> new RuntimeException("Question not found: " + questionId));

        String query = (customQuery != null && !customQuery.isBlank()) ? customQuery : question.getTitle();
        List<Experience> allExperiences = experienceRepository.findAll();
        QuestionCategory category = resolveQuestionCategory(question);
        return experienceVectorRetrievalService.search(
                query,
                3,
                extractUsedExperienceIds(question, questionId, allExperiences),
                buildSupportingQueries(question, query),
                category);
    }

    @Transactional
    public TitleSuggestionResponse suggestTitles(Long questionId) {
        WorkspaceQuestion question = questionRepository.findById(questionId)
                .orElseThrow(() -> new RuntimeException("Question not found: " + questionId));

        String currentDraft = preferredQuestionDraft(question);
        if (currentDraft == null || currentDraft.isBlank()) {
            throw new IllegalStateException("Draft is empty");
        }

        String company = question.getApplication().getCompanyName();
        String position = question.getApplication().getPosition();
        QuestionCategory category = resolveQuestionCategory(question);
        String companyContext = buildApplicationResearchContext(question, category);
        List<Experience> allExperiences = experienceRepository.findAll();
        String context = buildFilteredContext(question, questionId, allExperiences, category);
        String others = buildOthersContext(question, questionId, allExperiences);

        List<TitleSuggestionResponse.TitleCandidate> candidates = buildTitleSuggestionCandidates(
                currentDraft,
                company,
                position,
                question.getTitle(),
                category,
                companyContext,
                context,
                others);

        log.info("Title suggestions generated questionId={} candidateCount={} currentTitle={}",
                questionId,
                candidates.size(),
                safeSnippet(normalizeTitleLine(extractActualTitleLine(currentDraft)), 80));

        try {
            question.setTitleCandidatesJson(objectMapper.writeValueAsString(candidates));
        } catch (Exception e) {
            log.warn("제목 추천 캐시 저장 실패: questionId={}", questionId);
        }

        return TitleSuggestionResponse.builder()
                .currentTitle(normalizeTitleLine(extractActualTitleLine(currentDraft)))
                .candidates(candidates)
                .build();
    }

    @Transactional
    public WorkspaceQuestion updateCategory(Long questionId, QuestionCategory category) {
        WorkspaceQuestion question = questionRepository.findById(questionId)
                .orElseThrow(() -> new RuntimeException("Question not found: " + questionId));
        question.setCategory(category);
        return questionRepository.save(question);
    }

    @Transactional
    public WorkspaceQuestion applyTitleSuggestion(Long questionId, String requestedTitleLine) {
        WorkspaceQuestion question = questionRepository.findById(questionId)
                .orElseThrow(() -> new RuntimeException("Question not found: " + questionId));

        String currentDraft = preferredQuestionDraft(question);
        if (currentDraft == null || currentDraft.isBlank()) {
            throw new IllegalStateException("Draft is empty");
        }

        String company = question.getApplication().getCompanyName();
        String position = question.getApplication().getPosition();
        QuestionCategory category = resolveQuestionCategory(question);
        String improvedTitleLine = normalizeTitleLine(requestedTitleLine);

        if (!isBracketTitleLine(improvedTitleLine)) {
            throw new IllegalArgumentException("Title must be a single bracketed line");
        }

        if (!isAcceptedTitleLine(improvedTitleLine, company, position, question.getTitle(), category)) {
            throw new IllegalArgumentException("Title does not meet title quality rules");
        }

        boolean changed = false;
        int maxLength = question.getMaxLength() == null ? 0 : question.getMaxLength();

        if (question.getContent() != null && !question.getContent().isBlank()) {
            String updatedContent = applyTitleLine(question.getContent(), improvedTitleLine);
            if ((maxLength <= 0 || countResumeCharacters(updatedContent) <= maxLength)
                    && !updatedContent.equals(question.getContent())) {
                question.setContent(updatedContent);
                changed = true;
            }
        }

        if (question.getWashedKr() != null && !question.getWashedKr().isBlank()) {
            String updatedWashed = applyTitleLine(question.getWashedKr(), improvedTitleLine);
            if ((maxLength <= 0 || countResumeCharacters(updatedWashed) <= maxLength)
                    && !updatedWashed.equals(question.getWashedKr())) {
                question.setWashedKr(updatedWashed);
                changed = true;
            }
        }

        if (!changed && (question.getContent() == null || question.getContent().isBlank())) {
            String updatedContent = applyTitleLine(currentDraft, improvedTitleLine);
            if (maxLength <= 0 || countResumeCharacters(updatedContent) <= maxLength) {
                question.setContent(updatedContent);
                changed = true;
            }
        }

        if (changed) {
            question = questionRepository.save(question);
        }

        return question;
    }

    @Transactional
    public void processRefinement(Long questionId, String directive, Integer targetChars, List<Long> storyIds, SseEmitter emitter) {
        HeartbeatHandle heartbeat = startHeartbeat(emitter);
        workspaceTaskCache.setRunning(questionId);
        try {
            WorkspaceQuestion initialQuestion = questionRepository.findById(questionId)
                    .orElseThrow(() -> new RuntimeException("Question not found: " + questionId));

            String company = initialQuestion.getApplication().getCompanyName();
            String position = initialQuestion.getApplication().getPosition();
            QuestionCategory category = resolveQuestionCategory(initialQuestion);
            String companyContext = buildApplicationResearchContext(initialQuestion, category);
            String questionTitle = initialQuestion.getTitle();
            String currentInput = initialQuestion.getWashedKr() != null
                    ? initialQuestion.getWashedKr()
                    : initialQuestion.getContent();

            sendProgress(emitter, STAGE_RAG, "지원한 기업 정보와 문항을 바탕으로 초안 컨텍스트를 구성하고 있어요. 🧭");
            sendProgress(emitter, STAGE_RAG, "다른 문항과 겹치지 않도록 경험 포인트를 자연스럽게 조정 중입니다. 🧩");

            List<Experience> allExperiences = experienceRepository.findAll();
            String others = buildOthersContext(initialQuestion, questionId, allExperiences);

            // storyIds가 있으면 서사 컨텍스트 구축, 없으면 기존 RAG/필터링 컨텍스트 사용
            String context = buildContext(initialQuestion, questionId, allExperiences, category, storyIds);
            String writingGuideContext = buildWritingGuideContext(storyIds, category);

            paceProcessing();
            sendProgress(emitter, STAGE_DRAFT, "선택한 경험과 요청 사항을 반영해 초안을 다시 생성하고 있습니다. ✍️");

            int maxLength = initialQuestion.getMaxLength();
            int[] targetRange = resolveTargetRange(
                    maxLength,
                    directive,
                    targetChars,
                    DEFAULT_TARGET_MIN_RATIO,
                    DEFAULT_TARGET_MAX_RATIO);
            int minTargetChars = targetRange[0];
            int maxTargetChars = targetRange[1];
            String batchStrategy = safeTrim(initialQuestion.getBatchStrategyDirective());
            String directiveForPrompt = augmentDirectiveForPrompt(
                    mergeDirectiveLayers(batchStrategy, directive),
                    maxLength,
                    targetChars);
            log.info(
                    "Refine directives questionId={} hasBatchStrategy={} hasUserDirective={} batchStrategySnippet={} userDirectiveSnippet={}",
                    questionId,
                    !batchStrategy.isBlank(),
                    directive != null && !directive.isBlank(),
                    safeSnippet(batchStrategy, 220),
                    safeSnippet(safeTrim(directive), 220));

            DraftParams refineParams = buildDraftParams(
                    company,
                    position,
                    questionTitle,
                    companyContext,
                    maxLength,
                    minTargetChars,
                    maxTargetChars,
                    context,
                    others,
                    directiveForPrompt,
                    writingGuideContext);

            WorkspaceDraftAiService.DraftResponse refineResponse = generateRefinedDraftWithStrategy(
                    category,
                    refineParams,
                    currentInput,
                    false);
            String assembledRefineDraft = assembleDraftText(refineResponse);
            logLengthMetrics("refine", maxLength, minTargetChars, maxTargetChars, assembledRefineDraft, 0);

            String pipelineRefinedDraft = expandToMinimumLength(
                    normalizeTitleSpacing(assembledRefineDraft).trim(),
                    category,
                    minTargetChars,
                    maxTargetChars,
                    maxLength,
                    company,
                    position,
                    questionTitle,
                    companyContext,
                    context,
                    others,
                    directiveForPrompt);
            String rawRefinedDraft = pipelineRefinedDraft;
            sendMinimumLengthWarningIfNeeded(emitter, rawRefinedDraft, minTargetChars);
            rawRefinedDraft = applyTitleLine(
                    rawRefinedDraft,
                    resolveImprovedTitleLine(
                            rawRefinedDraft,
                            company,
                            position,
                            questionTitle,
                            category,
                            companyContext,
                            context,
                            others,
                            false));
            String refinedDraft = prepareDraftForTranslation(
                    rawRefinedDraft,
                    maxLength,
                    minTargetChars,
                    maxTargetChars,
                    company,
                    position,
                    companyContext,
                    context,
                    others);
            refinedDraft = selectBetterDraft(refinedDraft, rawRefinedDraft, minTargetChars, maxTargetChars, maxLength);
            refinedDraft = selectBetterDraft(refinedDraft, pipelineRefinedDraft, minTargetChars, maxTargetChars, maxLength);

            WorkspaceQuestion question = questionRepository.findById(questionId).orElseThrow();
            question.setContent(refinedDraft);
            question.setUserDirective(directive);
            questionRepository.save(question);
            sendSse(emitter, "draft_intermediate", refinedDraft);
            questionSnapshotService.saveSnapshot(questionId, SnapshotType.DRAFT_GENERATED, refinedDraft);

            paceProcessing();
            sendProgress(emitter, STAGE_WASH, "기계적인 말투를 줄이기 위해 1차 번역(한->영)을 진행 중입니다. 🌐");
            String translatedEn = translationService.translateToEnglish(refinedDraft);

            paceProcessing();
            sendProgress(emitter, STAGE_WASH, "더 자연스러운 한국어 문장으로 다듬기 위해 세탁본을 만들고 있습니다. 🫧");
            String washedKr = prepareWashedDraft(
                    translationService.translateToKorean(translatedEn));
            logLengthMetrics("wash", maxLength, minTargetChars, maxTargetChars, washedKr, 0);

            question = questionRepository.findById(questionId).orElseThrow();
            question.setWashedKr(washedKr);
            question.setFinalText(null); // 새 세탁본 생성 시 이전 다림질 내용 초기화
            questionRepository.save(question);
            questionSnapshotService.saveSnapshot(questionId, SnapshotType.WASHED, washedKr);
            sendSse(emitter, "washed_intermediate", washedKr);

            paceProcessing();
            sendProgress(emitter, STAGE_PATCH, "세탁본 문장에서 의미 손실과 어색한 표현을 휴먼 패치로 분석 중입니다. 🔎");
            DraftAnalysisResult analysis = analyzeBySentencePairsWithFallback(emitter, refinedDraft, washedKr);

            paceProcessing();
            normalizeAnalysis(analysis, refinedDraft, washedKr);

            question = questionRepository.findById(questionId).orElseThrow();
            question.setMistranslations(objectMapper.writeValueAsString(analysis.getMistranslations()));
            question.setAiReview(objectMapper.writeValueAsString(analysis.getAiReviewReport()));

            String responseDraft = washedKr;
            responseDraft = prepareDraftForTranslation(
                    normalizeTitleSpacing(responseDraft),
                    maxLength,
                    minTargetChars,
                    maxTargetChars,
                    company,
                    position,
                    companyContext,
                    context,
                    others);
            responseDraft = selectBetterDraft(responseDraft, washedKr, minTargetChars, maxTargetChars, maxLength);
            responseDraft = selectBetterDraft(responseDraft, refinedDraft, minTargetChars, maxTargetChars, maxLength);

            Map<String, Object> result = buildCompletionResult(
                    refinedDraft,
                    minTargetChars,
                    washedKr,
                    responseDraft,
                    analysis);
            logLengthMetrics("final", maxLength, minTargetChars, maxTargetChars, responseDraft, 0);

            workspaceTaskCache.setComplete(questionId, result);
            sendStage(emitter, STAGE_DONE);
            sendSse(emitter, "complete", result);
            emitter.complete();
        } catch (SseConnectionClosedException e) {
            log.info("Refinement stream closed by client");
        } catch (Exception e) {
            log.error("Refinement process failed", e);
            workspaceTaskCache.setError(questionId, resolveUserFacingErrorMessage(e));
            try {
                sendSse(emitter, "error", resolveUserFacingErrorMessage(e));
            } catch (SseConnectionClosedException ignored) {
                log.info("Refinement stream already closed while reporting error");
            }
            completeQuietly(emitter);
        } finally {
            heartbeat.stop();
        }
    }

    @Transactional
    public void processHumanPatch(Long questionId, boolean useDirective, Integer targetChars, List<Long> storyIds, SseEmitter emitter) {
        HeartbeatHandle heartbeat = startHeartbeat(emitter);
        workspaceTaskCache.setRunning(questionId);
        try {
            WorkspaceQuestion initialQuestion = questionRepository.findById(questionId)
                    .orElseThrow(() -> new RuntimeException("Question not found: " + questionId));

            String company = initialQuestion.getApplication().getCompanyName();
            String position = initialQuestion.getApplication().getPosition();
            String questionTitle = initialQuestion.getTitle();

            sendComment(emitter, "flush buffer");

            sendProgress(emitter, STAGE_RAG, "자기소개서 생성을 위해 기업 분석 데이터와 문항을 준비하고 있어요. 🧭");

            // ── [STEP 1] 문항 카테고리 분류 (복합 문항이면 intent도 추출) ─────
            ClassificationResult classification = questionClassifierService.classifyWithIntents(questionTitle);
            QuestionCategory category = classification.primaryCategory();
            String companyContext = buildApplicationResearchContext(initialQuestion, category);
            sendProgress(emitter, STAGE_RAG,
                    String.format("문항 유형 분석 완료 (%s). 맞춤 전략을 적용합니다. 🎯", category.getDisplayName()));

            paceProcessing();

            // ── [STEP 2] 컨텍스트 구축 ──────────────────────────────────────
            sendProgress(emitter, STAGE_RAG, "질문 의도와 글자 수 조건을 먼저 맞춰 초안 방향을 정리하고 있습니다. 📐");

            List<Experience> allExperiences = experienceRepository.findAll();
            String others = buildOthersContext(initialQuestion, questionId, allExperiences);

            sendProgress(emitter, STAGE_RAG, "문항에 가장 잘 맞는 핵심 소재를 골라 연결하고 있어요. 🧩");

            // storyIds가 있으면 서사 컨텍스트 구축, 없으면 기존 RAG/필터링 컨텍스트 사용
            String context = buildContext(initialQuestion, questionId, allExperiences, category, storyIds);
            String writingGuideContext = buildWritingGuideContext(storyIds, category);

            paceProcessing();
            sendProgress(emitter, STAGE_DRAFT, "엄선한 경험 데이터를 바탕으로 새로운 초안을 생성 중입니다. ✍️");

            int maxLengthGen = initialQuestion.getMaxLength();
            String batchStrategy = safeTrim(initialQuestion.getBatchStrategyDirective());
            String userDirective = safeTrim(initialQuestion.getUserDirective());
            String rawDirective = mergeDirectiveLayers(
                    batchStrategy,
                    userDirective);
            String directiveForPrompt = useDirective
                    ? augmentDirectiveForPrompt(rawDirective, maxLengthGen, targetChars)
                    : NO_EXTRA_USER_DIRECTIVE;
            log.info(
                    "HumanPatch directives questionId={} useDirective={} hasBatchStrategy={} hasUserDirective={} batchStrategySnippet={} userDirectiveSnippet={}",
                    questionId,
                    useDirective,
                    !batchStrategy.isBlank(),
                    !userDirective.isBlank(),
                    safeSnippet(batchStrategy, 220),
                    safeSnippet(userDirective, 220)
            );
            int[] targetRange = resolveTargetRange(
                    maxLengthGen,
                    rawDirective,
                    targetChars,
                    DEFAULT_TARGET_MIN_RATIO,
                    DEFAULT_TARGET_MAX_RATIO);
            int minTargetChars = targetRange[0];
            int preferredTargetChars = targetRange[1];

            // ── [STEP 3] Prompt Strategy 기반 초안 생성 ──────────────────────
            // PromptFactory가 카테고리에 맞는 전략(XML 구조 프롬프트 + Few-shot)을 조립하고,
            // StrategyDraftGeneratorService가 ChatLanguageModel.generate()로 직접 실행합니다.
            DraftParams draftParams = buildDraftParams(
                    company,
                    position,
                    questionTitle,
                    companyContext,
                    maxLengthGen,
                    minTargetChars,
                    preferredTargetChars,
                    context,
                    others,
                    directiveForPrompt,
                    writingGuideContext,
                    classification.allIntents());

            WorkspaceDraftAiService.DraftResponse draftResponse = generateDraftWithStrategy(category, draftParams);
            String assembledDraft = assembleDraftText(draftResponse);
            logLengthMetrics("generate", maxLengthGen, minTargetChars, preferredTargetChars, assembledDraft, 0);

            String pipelineDraft = expandToMinimumLength(
                    normalizeTitleSpacing(assembledDraft).trim(),
                    category,
                    minTargetChars,
                    preferredTargetChars,
                    maxLengthGen,
                    company,
                    position,
                    questionTitle,
                    companyContext,
                    context,
                    others,
                    directiveForPrompt);
            String rawDraft = pipelineDraft;
            logTraceLength("humanPatch.generate.expanded", rawDraft, maxLengthGen, minTargetChars, preferredTargetChars);
            sendMinimumLengthWarningIfNeeded(emitter, rawDraft, minTargetChars);
            rawDraft = applyTitleLine(
                    rawDraft,
                    resolveImprovedTitleLine(
                            rawDraft,
                            company,
                            position,
                            questionTitle,
                            category,
                            companyContext,
                            context,
                            others,
                            false));
            logTraceLength("humanPatch.generate.titleApplied", rawDraft, maxLengthGen, minTargetChars, preferredTargetChars);
            String draft = prepareDraftForTranslation(
                    rawDraft,
                    maxLengthGen,
                    minTargetChars,
                    preferredTargetChars,
                    company,
                    position,
                    companyContext,
                    context,
                    others);
            draft = selectBetterDraft(draft, rawDraft, minTargetChars, preferredTargetChars, maxLengthGen);
            draft = selectBetterDraft(draft, pipelineDraft, minTargetChars, preferredTargetChars, maxLengthGen);
            logTraceLength("humanPatch.generate.translationInput", draft, maxLengthGen, minTargetChars, preferredTargetChars);

            WorkspaceQuestion question = questionRepository.findById(questionId).orElseThrow();
            question.setContent(draft);
            questionRepository.save(question);
            sendSse(emitter, "draft_intermediate", draft);
            questionSnapshotService.saveSnapshot(questionId, SnapshotType.DRAFT_GENERATED, draft);

            paceProcessing();
            sendProgress(emitter, STAGE_WASH, "기계적인 말투를 줄이기 위해 1차 번역 공정을 진행하고 있습니다. 🌐");
            String translatedEn = translationService.translateToEnglish(draft);
            logTraceLength("humanPatch.wash.translatedEn", translatedEn, 0, 0, 0);

            paceProcessing();
            sendProgress(emitter, STAGE_WASH, "더 자연스러운 한국어 문장으로 다듬기 위해 세탁본을 만들고 있습니다. 🫧");
            String washedKr = prepareWashedDraft(
                    translationService.translateToKorean(translatedEn));
            logTraceLength("humanPatch.wash.washedKr", washedKr, maxLengthGen, minTargetChars, preferredTargetChars);
            logLengthMetrics("wash", maxLengthGen, minTargetChars, preferredTargetChars, washedKr, 0);

            question = questionRepository.findById(questionId).orElseThrow();
            question.setWashedKr(washedKr);
            question.setFinalText(null); // 새 세탁본 생성 시 이전 다림질 내용 초기화
            questionRepository.save(question);
            questionSnapshotService.saveSnapshot(questionId, SnapshotType.WASHED, washedKr);
            sendSse(emitter, "washed_intermediate", washedKr);

            paceProcessing();
            sendProgress(emitter, STAGE_PATCH, "더 사람 냄새 나는 문장을 위해 휴먼 패치 분석을 진행하고 있어요. 🔎");
            int maxLengthFinal = initialQuestion.getMaxLength();
            DraftAnalysisResult analysis = analyzeBySentencePairsWithFallback(emitter, draft, washedKr);

            paceProcessing();
            normalizeAnalysis(analysis, draft, washedKr);
            if (analysis != null) {
                logTraceLength("humanPatch.patch.humanPatchedText", analysis.getHumanPatchedText(), maxLengthFinal, minTargetChars, preferredTargetChars);
            }

            question = questionRepository.findById(questionId).orElseThrow();
            question.setMistranslations(objectMapper.writeValueAsString(analysis.getMistranslations()));
            question.setAiReview(objectMapper.writeValueAsString(analysis.getAiReviewReport()));

            String responseDraft = washedKr;
            logTraceLength("humanPatch.final.beforeLengthLimit", responseDraft, maxLengthFinal, minTargetChars, preferredTargetChars);
            responseDraft = prepareDraftForTranslation(
                    normalizeTitleSpacing(responseDraft),
                    maxLengthFinal,
                    minTargetChars,
                    preferredTargetChars,
                    company,
                    position,
                    companyContext,
                    context,
                    others);
            responseDraft = selectBetterDraft(responseDraft, washedKr, minTargetChars, preferredTargetChars, maxLengthFinal);
            responseDraft = selectBetterDraft(responseDraft, draft, minTargetChars, preferredTargetChars, maxLengthFinal);
            logTraceLength("humanPatch.final.afterLengthLimit", responseDraft, maxLengthFinal, minTargetChars, preferredTargetChars);

            Map<String, Object> result = buildCompletionResult(
                    draft,
                    minTargetChars,
                    washedKr,
                    responseDraft,
                    analysis);
            logLengthMetrics("final", maxLengthFinal, minTargetChars, preferredTargetChars, responseDraft, 0);

            workspaceTaskCache.setComplete(questionId, result);
            sendStage(emitter, STAGE_DONE);
            sendSse(emitter, "complete", result);
            emitter.complete();
        } catch (SseConnectionClosedException e) {
            log.info("Human patch stream closed by client");
        } catch (Exception e) {
            log.error("Human Patch process failed", e);
            workspaceTaskCache.setError(questionId, resolveUserFacingErrorMessage(e));
            try {
                sendSse(emitter, "error", resolveUserFacingErrorMessage(e));
            } catch (SseConnectionClosedException ignored) {
                log.info("Human patch stream already closed while reporting error");
            }
            completeQuietly(emitter);
        } finally {
            heartbeat.stop();
        }
    }

    @Transactional
    public void processRewash(Long questionId, SseEmitter emitter) {
        HeartbeatHandle heartbeat = startHeartbeat(emitter);
        workspaceTaskCache.setRunning(questionId);
        try {
            WorkspaceQuestion question = questionRepository.findById(questionId)
                    .orElseThrow(() -> new RuntimeException("Question not found: " + questionId));

            String draft = question.getContent();
            if (draft == null || draft.isBlank()) {
                throw new IllegalStateException("Draft is empty");
            }

            String company = question.getApplication().getCompanyName();
            String position = question.getApplication().getPosition();
            String questionTitle = question.getTitle();
            QuestionCategory category = resolveQuestionCategory(question);
            String companyContext = buildApplicationResearchContext(question, category);
            List<Experience> allExperiences = experienceRepository.findAll();
            String others = buildOthersContext(question, questionId, allExperiences);
            String context = buildFilteredContext(question, questionId, allExperiences, category);
            draft = collapseRepeatedLeadingTitleLines(draft);
            if (!draft.equals(question.getContent())) {
                question.setContent(draft);
                questionRepository.save(question);
            }

            sendComment(emitter, "flush buffer");
            sendProgress(emitter, STAGE_DRAFT, "현재 초안을 바탕으로 세탁 파이프라인을 다시 시작합니다. 🌊");
            sendSse(emitter, "draft_intermediate", draft);
            questionSnapshotService.saveSnapshot(questionId, SnapshotType.DRAFT_GENERATED, draft);

            paceProcessing();
            sendProgress(emitter, STAGE_WASH, "초안 고도화를 위해 중간 번역 과정을 거치고 있습니다. 🌐");
            String translatedEn = translationService.translateToEnglish(draft);

            paceProcessing();
            sendProgress(emitter, STAGE_WASH, "한국어로 다시 번역하며 표현을 더 자연스럽게 다듬고 있습니다. 🫧");

            int maxLength = question.getMaxLength();
            String rawDirective = question.getUserDirective();
            String directiveForPrompt = augmentDirectiveForPrompt(rawDirective, maxLength, null);
            int[] targetRange = resolveTargetRange(
                    maxLength,
                    rawDirective,
                    null,
                    DEFAULT_TARGET_MIN_RATIO,
                    DEFAULT_TARGET_MAX_RATIO);
            int minTargetChars = targetRange[0];
            int preferredTargetChars = targetRange[1];

            String washedKr = prepareWashedDraft(
                    translationService.translateToKorean(translatedEn));
            logLengthMetrics("wash", maxLength, minTargetChars, preferredTargetChars, washedKr, 0);

            question = questionRepository.findById(questionId).orElseThrow();
            question.setWashedKr(washedKr);
            question.setFinalText(null); // 재세탁 시 이전 다림질 내용 초기화
            questionRepository.save(question);
            questionSnapshotService.saveSnapshot(questionId, SnapshotType.WASHED, washedKr);
            sendSse(emitter, "washed_intermediate", washedKr);

            paceProcessing();
            sendProgress(emitter, STAGE_PATCH, "이제 새 문장을 바탕으로 휴먼 패치 분석을 다시 진행합니다. 🔎");
            finalizePatchAnalysis(
                    emitter,
                    questionId,
                    draft,
                    washedKr,
                    maxLength,
                    context,
                    company,
                    position,
                    companyContext,
                    others);
            emitter.complete();
        } catch (SseConnectionClosedException e) {
            log.info("Rewash stream closed by client");
        } catch (Exception e) {
            log.error("Rewash process failed", e);
            workspaceTaskCache.setError(questionId, resolveUserFacingErrorMessage(e));
            try {
                sendSse(emitter, "error", resolveUserFacingErrorMessage(e));
            } catch (SseConnectionClosedException ignored) {
                log.info("Rewash stream already closed while reporting error");
            }
            completeQuietly(emitter);
        } finally {
            heartbeat.stop();
        }
    }

    @Transactional
    public void processRepatch(Long questionId, SseEmitter emitter) {
        HeartbeatHandle heartbeat = startHeartbeat(emitter);
        workspaceTaskCache.setRunning(questionId);
        try {
            WorkspaceQuestion question = questionRepository.findById(questionId)
                    .orElseThrow(() -> new RuntimeException("Question not found: " + questionId));

            String draft = question.getContent();
            String washedKr = question.getWashedKr();
            if (draft == null || draft.isBlank()) {
                throw new IllegalStateException("Draft is empty");
            }
            if (washedKr == null || washedKr.isBlank()) {
                throw new IllegalStateException("Washed draft is empty");
            }

            List<Experience> allExperiences = experienceRepository.findAll();
            QuestionCategory category = resolveQuestionCategory(question);
            String context = buildFilteredContext(question, questionId, allExperiences, category);
            String company = question.getApplication().getCompanyName();
            String position = question.getApplication().getPosition();
            String companyContext = buildApplicationResearchContext(question, category);
            String others = buildOthersContext(question, questionId, allExperiences);

            sendComment(emitter, "flush buffer");
            sendProgress(emitter, STAGE_PATCH, "재분석을 위해 초안과 세탁본을 불러오고 있습니다. 📂");
            sendSse(emitter, "draft_intermediate", draft);
            sendSse(emitter, "washed_intermediate", washedKr);

            paceProcessing();
            sendProgress(emitter, STAGE_PATCH, "문장을 다시 읽으며 핵심 패치 포인트를 재분석하고 있습니다. 🔎");
            finalizePatchAnalysis(
                    emitter,
                    questionId,
                    draft,
                    washedKr,
                    question.getMaxLength(),
                    context,
                    company,
                    position,
                    companyContext,
                    others);
            emitter.complete();
        } catch (SseConnectionClosedException e) {
            log.info("Repatch stream closed by client");
        } catch (Exception e) {
            log.error("Repatch process failed", e);
            workspaceTaskCache.setError(questionId, resolveUserFacingErrorMessage(e));
            try {
                sendSse(emitter, "error", resolveUserFacingErrorMessage(e));
            } catch (SseConnectionClosedException ignored) {
                log.info("Repatch stream already closed while reporting error");
            }
            completeQuietly(emitter);
        } finally {
            heartbeat.stop();
        }
    }

    private void finalizePatchAnalysis(
            SseEmitter emitter,
            Long questionId,
            String originalDraft,
            String washedKr,
            int maxLength,
            String context,
            String company,
            String position,
            String companyContext,
            String others) throws Exception {
        DraftAnalysisResult analysis = analyzeBySentencePairsWithFallback(emitter, originalDraft, washedKr);

        paceProcessing();
        normalizeAnalysis(analysis, originalDraft, washedKr);

        WorkspaceQuestion question = questionRepository.findById(questionId).orElseThrow();
        question.setMistranslations(objectMapper.writeValueAsString(analysis.getMistranslations()));
        question.setAiReview(objectMapper.writeValueAsString(analysis.getAiReviewReport()));
        question.setWashedKr(washedKr);
        questionRepository.save(question);
        questionSnapshotService.saveSnapshot(questionId, SnapshotType.WASHED, washedKr);

        String responseDraft = (analysis.getHumanPatchedText() != null && !analysis.getHumanPatchedText().isBlank())
                ? analysis.getHumanPatchedText()
                : washedKr;
        int[] finalTargetRange = resolveTargetRange(
                maxLength,
                null,
                null,
                DEFAULT_TARGET_MIN_RATIO,
                DEFAULT_TARGET_MAX_RATIO);
        responseDraft = prepareDraftForTranslation(
                normalizeTitleSpacing(responseDraft),
                maxLength,
                finalTargetRange[0],
                finalTargetRange[1],
                company,
                position,
                companyContext,
                context,
                others);
        responseDraft = selectBetterDraft(responseDraft, washedKr, finalTargetRange[0], finalTargetRange[1], maxLength);
        responseDraft = selectBetterDraft(responseDraft, question.getContent(), finalTargetRange[0], finalTargetRange[1], maxLength);
        Map<String, Object> result = buildCompletionResult(
                question.getContent(),
                finalTargetRange[0],
                washedKr,
                responseDraft,
                analysis);
        logLengthMetrics("final", maxLength, finalTargetRange[0], finalTargetRange[1], responseDraft, 0);

        workspaceTaskCache.setComplete(questionId, result);
        sendStage(emitter, STAGE_DONE);
        sendSse(emitter, "complete", result);
    }

    private HeartbeatHandle startHeartbeat(SseEmitter emitter) {
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        ScheduledFuture<?> future = scheduler.scheduleAtFixedRate(() -> {
            try {
                emitter.send(SseEmitter.event().comment("heartbeat"));
            } catch (IOException | IllegalStateException e) {
                log.debug("Stopping workspace heartbeat: {}", e.getMessage());
                throw new SseConnectionClosedException(e);
            }
        }, HEARTBEAT_INTERVAL_SECONDS, HEARTBEAT_INTERVAL_SECONDS, TimeUnit.SECONDS);

        emitter.onCompletion(() -> future.cancel(true));
        emitter.onTimeout(() -> future.cancel(true));
        emitter.onError(error -> future.cancel(true));

        return new HeartbeatHandle(scheduler, future);
    }

    private String buildOthersContext(WorkspaceQuestion initialQuestion, Long questionId,
            List<Experience> allExperiences) {
        String others = initialQuestion.getApplication().getQuestions().stream()
                .filter(q -> !q.getId().equals(questionId))
                .map(q -> {
                    String draft = preferredQuestionDraft(q);
                    String usedProjects = detectUsedProjects(draft, allExperiences);
                    String usedFacets = extractFacetHintsFromDirective(q.getBatchStrategyDirective());
                    String titleLine = extractTitleLine(draft);
                    String bodySnippet = summarizeDraftForOverlap(draft);
                    return """
                            [OTHER_QUESTION]
                            Question: %s
                            Used projects: %s
                            Used facets: %s
                            Title used: %s
                            Body snippet: %s
                            Project overlap itself is allowed when needed, but avoid reusing the same detailed technical decision, troubleshooting point, lesson learned, metric cluster, opening claim, or action-result arc for the current question unless the user explicitly requires it.
                            """.formatted(
                            safeSnippet(q.getTitle(), 180),
                            safeSnippet(usedProjects, 180),
                            safeSnippet(usedFacets, 220),
                            safeSnippet(titleLine, 120),
                            safeSnippet(bodySnippet, 320));
                })
                .collect(Collectors.joining("\n"));

        return others.isBlank() ? "[OTHER_QUESTION]\nNo other question drafts available." : others;
    }

    private String preferredQuestionDraft(WorkspaceQuestion question) {
        if (question.getFinalText() != null && !question.getFinalText().isBlank()) {
            return question.getFinalText();
        }
        if (question.getWashedKr() != null && !question.getWashedKr().isBlank()) {
            return question.getWashedKr();
        }
        if (question.getContent() != null && !question.getContent().isBlank()) {
            return question.getContent();
        }
        return "";
    }

    private String detectUsedProjects(String draft, List<Experience> allExperiences) {
        if (draft == null || draft.isBlank()) {
            return "None detected";
        }

        String matches = allExperiences.stream()
                .map(Experience::getTitle)
                .filter(title -> title != null && !title.isBlank() && draft.contains(title))
                .distinct()
                .collect(Collectors.joining(", "));

        return matches.isBlank() ? "None detected" : matches;
    }

    private String extractTitleLine(String draft) {
        if (draft == null || draft.isBlank()) {
            return "No title";
        }

        String firstLine = draft.lines()
                .map(String::trim)
                .filter(line -> !line.isBlank())
                .findFirst()
                .orElse("");

        return firstLine.isBlank() ? "No title" : firstLine;
    }

    private String extractActualTitleLine(String draft) {
        String extracted = extractTitleLine(draft);
        return "No title".equals(extracted) ? "" : extracted;
    }

    private String resolveImprovedTitleLine(
            String draft,
            String company,
            String position,
            String questionTitle,
            QuestionCategory category,
            String companyContext,
            String context,
            String others,
            boolean forceRewrite) {
        String normalizedDraft = normalizeLengthText(normalizeTitleSpacing(draft));
        if (normalizedDraft == null || normalizedDraft.isBlank()) {
            return "";
        }

        String currentTitleLine = normalizeTitleLine(extractActualTitleLine(normalizedDraft));
        if (!forceRewrite && isAcceptedTitleLine(currentTitleLine, company, position, questionTitle, category)) {
            return currentTitleLine;
        }

        try {
            WorkspaceDraftAiService.DraftResponse rewritten = workspaceDraftAiService.rewriteTitle(
                    safeTrim(company),
                    safeTrim(position),
                    safeTrim(questionTitle),
                    safeTrim(companyContext),
                    normalizedDraft.trim(),
                    buildTitleRewriteContext(category, questionTitle, context, others));

            if (rewritten == null || rewritten.text == null || rewritten.text.isBlank()) {
                return currentTitleLine;
            }

            String candidateTitleLine = normalizeTitleLine(extractActualTitleLine(rewritten.text));
            if (isAcceptedTitleLine(candidateTitleLine, company, position, questionTitle, category)) {
                return candidateTitleLine;
            }
        } catch (Exception e) {
            log.warn("Title rewrite failed for company={} position={} question={}",
                    safeTrim(company),
                    safeTrim(position),
                    safeTrim(questionTitle),
                    e);
        }

        return currentTitleLine;
    }

    private String buildTitleRewriteContext(
            QuestionCategory category,
            String questionTitle,
            String context,
            String others) {
        StringBuilder builder = new StringBuilder();

        String titleFramingGuide = buildTitleFramingGuide(category, questionTitle);
        if (!titleFramingGuide.isBlank()) {
            builder.append(titleFramingGuide);
        }

        String normalizedContext = safeTrim(context);
        if (!normalizedContext.isBlank()) {
            if (builder.length() > 0) {
                builder.append("\n---\n");
            }
            builder.append(normalizedContext);
        }

        String normalizedOthers = safeTrim(others);
        if (!normalizedOthers.isBlank()) {
            if (builder.length() > 0) {
                builder.append("\n---\n");
            }
            builder.append("[Other question titles to avoid overlapping with]\n")
                    .append(safeSnippet(normalizedOthers, 1400));
        }

        if (builder.length() == 0) {
            return "No supporting title context available.";
        }

        return builder.toString();
    }

    private String buildTitleFramingGuide(QuestionCategory category, String questionTitle) {
        String safeQuestionTitle = safeTrim(questionTitle);
        QuestionCategory effectiveCategory = category != null ? category : QuestionCategory.DEFAULT;

        // 카테고리별 제목 형태(shape) 및 패턴 가이드 — Intent는 문항 원문에서 AI가 직접 추론
        String requiredShape = "Use a concrete evidence-first headline instead of a generic slogan.";
        String preferredPatterns = "- [core evidence] + [strongest role-fit signal]\n- [problem or action] + [result or value created]";
        String avoidLine = "Avoid titles that only paraphrase the question or sound like a project retrospective label.";

        switch (effectiveCategory) {
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
                [Title Framing Guide]
                Question (raw): %s
                Evaluation intent: Read the Question above and identify what the recruiter is specifically testing \
                (e.g., self-awareness of strength/weakness, growth mindset, technical depth, motivation fit, \
                collaboration style). The title must directly address that intent — not just highlight the most \
                impressive content regardless of what was asked.
                Mandatory shape: %s
                Preferred patterns:
                %s
                Avoid:
                %s
                """.formatted(
                safeQuestionTitle.isBlank() ? "No question text provided." : safeQuestionTitle,
                requiredShape,
                preferredPatterns.stripTrailing(),
                avoidLine);
    }

    private List<TitleSuggestionResponse.TitleCandidate> buildTitleSuggestionCandidates(
            String draft,
            String company,
            String position,
            String questionTitle,
            QuestionCategory category,
            String companyContext,
            String context,
            String others) {
        Map<String, TitleSuggestionResponse.TitleCandidate> deduped = new HashMap<>();
        int filteredOutCount = 0;

        try {
            WorkspaceDraftAiService.TitleCandidatesResponse response = workspaceDraftAiService.suggestTitles(
                    safeTrim(company),
                    safeTrim(position),
                    safeTrim(questionTitle),
                    safeTrim(companyContext),
                    normalizeLengthText(normalizeTitleSpacing(draft)).trim(),
                    buildTitleRewriteContext(category, questionTitle, context, others));

            if (response != null && response.candidates != null) {
                for (WorkspaceDraftAiService.TitleCandidate candidate : response.candidates) {
                    if (candidate == null) {
                        continue;
                    }

                    String titleLine = normalizeTitleLine(candidate.title);
                    String rejectionReason = findTitleRejectionReason(titleLine, company, position, questionTitle, category);
                    if (rejectionReason != null) {
                        filteredOutCount++;
                        log.info("Title candidate rejected source=ai title={} reason={}",
                                safeSnippet(titleLine, 120),
                                rejectionReason);
                        continue;
                    }

                    String dedupeKey = normalizeTitleComparison(extractBracketTitleCore(titleLine));
                    if (dedupeKey.isBlank() || deduped.containsKey(dedupeKey)) {
                        continue;
                    }

                    deduped.put(dedupeKey, TitleSuggestionResponse.TitleCandidate.builder()
                            .title(titleLine)
                            .score(adjustTitleCandidateScore(
                                    candidate.score == null ? 0 : candidate.score,
                                    titleLine,
                                    category))
                            .reason(safeTrim(candidate.reason))
                            .recommended(false)
                            .build());
                }
            }
        } catch (Exception e) {
            log.warn("Title suggestion generation failed for company={} position={} question={}",
                    safeTrim(company),
                    safeTrim(position),
                    safeTrim(questionTitle),
                    e);
        }

        String improvedTitleLine = resolveImprovedTitleLine(
                draft,
                company,
                position,
                questionTitle,
                category,
                companyContext,
                context,
                others,
                true);
        addTitleSuggestionIfEligible(deduped, improvedTitleLine, company, position, questionTitle, category, 96,
                "\uD604\uC7AC \uBB38\uD56D\uACFC \uCD08\uC548 \uD750\uB984\uC5D0 \uAC00\uC7A5 \uC548\uC815\uC801\uC73C\uB85C \uB9DE\uB294 \uCD94\uCC9C \uC81C\uBAA9\uC785\uB2C8\uB2E4.");
        addTitleSuggestionIfEligible(deduped, normalizeTitleLine(extractActualTitleLine(draft)), company, position,
                questionTitle, category, 72, "\uD604\uC7AC \uCD08\uC548\uC758 \uB9E5\uB77D\uC744 \uADF8\uB300\uB85C \uC720\uC9C0\uD558\uB294 \uC81C\uBAA9\uC785\uB2C8\uB2E4.");

        List<TitleSuggestionResponse.TitleCandidate> ranked = deduped.values().stream()
                .sorted(Comparator.comparingInt(TitleSuggestionResponse.TitleCandidate::getScore).reversed())
                .limit(3)
                .collect(Collectors.toCollection(ArrayList::new));

        if (!ranked.isEmpty()) {
            ranked.get(0).setRecommended(true);
            if (ranked.get(0).getReason() == null || ranked.get(0).getReason().isBlank()) {
                ranked.get(0).setReason("\uC9C8\uBB38 \uC758\uB3C4\uC640 \uD604\uC7AC \uCD08\uC548\uC758 \uD575\uC2EC \uADFC\uAC70\uB97C \uAC00\uC7A5 \uC120\uBA85\uD558\uAC8C \uB4DC\uB7EC\uB0C5\uB2C8\uB2E4.");
            }
        } else {
            log.warn("Title suggestions resolved to zero candidates company={} position={} question={} filteredOutCount={}",
                    safeTrim(company),
                    safeTrim(position),
                    safeTrim(questionTitle),
                    filteredOutCount);
        }

        return ranked;
    }

    private void addTitleSuggestionIfEligible(
            Map<String, TitleSuggestionResponse.TitleCandidate> deduped,
            String titleLine,
            String company,
            String position,
            String questionTitle,
            QuestionCategory category,
            int score,
            String reason) {
        String normalizedTitleLine = normalizeTitleLine(titleLine);
        String rejectionReason = findTitleRejectionReason(normalizedTitleLine, company, position, questionTitle, category);
        if (rejectionReason != null) {
            log.info("Title candidate rejected source=fallback title={} reason={}",
                    safeSnippet(normalizedTitleLine, 120),
                    rejectionReason);
            return;
        }

        String dedupeKey = normalizeTitleComparison(extractBracketTitleCore(normalizedTitleLine));
        if (dedupeKey.isBlank() || deduped.containsKey(dedupeKey)) {
            return;
        }

        deduped.put(dedupeKey, TitleSuggestionResponse.TitleCandidate.builder()
                .title(normalizedTitleLine)
                .score(adjustTitleCandidateScore(score, normalizedTitleLine, category))
                .reason(reason)
                .recommended(false)
                .build());
    }

    private boolean isAcceptedTitleLine(
            String titleLine,
            String company,
            String position,
            String questionTitle,
            QuestionCategory category) {
        return findTitleRejectionReason(titleLine, company, position, questionTitle, category) == null;
    }

    private String findTitleRejectionReason(
            String titleLine,
            String company,
            String position,
            String questionTitle,
            QuestionCategory category) {
        if (!isBracketTitleLine(titleLine)) {
            return "not_bracket_title";
        }

        String core = extractBracketTitleCore(titleLine);
        if (core.isBlank()) {
            return "empty_core";
        }

        int visibleChars = countResumeCharacters(core);
        if (visibleChars < TITLE_MIN_VISIBLE_CHARS || visibleChars > TITLE_MAX_VISIBLE_CHARS) {
            return "length_out_of_range(" + visibleChars + ")";
        }

        String normalizedCore = normalizeTitleComparison(core);
        if (normalizedCore.isBlank()) {
            return "blank_after_normalization";
        }

        if (normalizedCore.contains("\uAE30\uD0C0") || normalizedCore.contains("\uC81C\uBAA9")) {
            return "contains_meta_word";
        }

        if (containsNormalized(normalizedCore, company) || containsNormalized(normalizedCore, position)) {
            return "contains_company_or_position";
        }

        String normalizedQuestion = normalizeTitleComparison(questionTitle);
        if (isQuestionParaphraseTitle(normalizedCore, normalizedQuestion)) {
            return "too_similar_to_question";
        }

        if (matchesGenericTitlePattern(normalizedCore)) {
            return "generic_title_pattern";
        }

        if (!hasConcreteTitleShape(core, normalizedCore)) {
            return "not_concrete_enough";
        }

        String intentSpecificRejection = findIntentSpecificTitleRejectionReason(category, core, normalizedCore);
        if (intentSpecificRejection != null) {
            return intentSpecificRejection;
        }

        return null;
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

    private boolean matchesGenericTitlePattern(String normalizedTitle) {
        for (String pattern : GENERIC_TITLE_PATTERNS) {
            if (normalizedTitle.equals(pattern)) {
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

        for (String actionSignal : TITLE_ACTION_SIGNALS) {
            if (titleCore.contains(actionSignal)) {
                return true;
            }
        }

        if (containsAny(normalizedTitle,
                "\uAC1C\uBC1C",
                "\uC5D4\uC9C0\uB2C8\uC5B4",
                "\uB9E4\uB2C8\uC800",
                "\uAE30\uD68D",
                "\uC0AC\uC6A9\uC790",
                "\uBD84\uC11D",
                "\uC544\uD0A4\uD14D\uD2B8",
                "\uC5F0\uAD6C",
                "pm",
                "\uB9AC\uB354")) {
            return true;
        }

        String[] tokens = titleCore.trim().split("\\s+");
        return tokens.length >= 3;
    }

    private String findIntentSpecificTitleRejectionReason(
            QuestionCategory category,
            String titleCore,
            String normalizedTitle) {
        QuestionCategory effectiveCategory = category != null ? category : QuestionCategory.DEFAULT;
        if (effectiveCategory == QuestionCategory.EXPERIENCE) {
            boolean hasAction = hasExperienceTitleActionSignal(titleCore, normalizedTitle);
            boolean hasResult = hasExperienceTitleResultSignal(titleCore, normalizedTitle);

            if (containsAny(normalizedTitle, EXPERIENCE_TITLE_GENERIC_SIGNALS) && !hasAction && !hasResult) {
                return "experience_generic_skill_title";
            }

            if (containsAny(normalizedTitle, "\uD504\uB85C\uC81D\uD2B8", "\uACBD\uD5D8") && !hasAction && !hasResult) {
                return "experience_project_only_title";
            }

            return null;
        }

        if (effectiveCategory == QuestionCategory.PROBLEM_SOLVING) {
            boolean hasProblem = hasProblemSolvingProblemSignal(titleCore, normalizedTitle);
            boolean hasResolution = hasProblemSolvingResolutionSignal(titleCore, normalizedTitle);

            if (containsAny(normalizedTitle, PROBLEM_SOLVING_TITLE_META_SIGNALS) && !hasProblem && !hasResolution) {
                return "problem_solving_meta_title";
            }

            return null;
        }

        if (effectiveCategory == QuestionCategory.COLLABORATION) {
            boolean hasRole = hasCollaborationRoleSignal(titleCore, normalizedTitle);
            boolean hasProcess = hasCollaborationProcessSignal(titleCore, normalizedTitle);
            boolean hasOutcome = hasCollaborationOutcomeSignal(titleCore, normalizedTitle);

            if (containsAny(normalizedTitle, COLLABORATION_TITLE_META_SIGNALS) && !hasRole && !hasProcess && !hasOutcome) {
                return "collaboration_meta_title";
            }

            if (containsAny(normalizedTitle, "\uC18C\uD1B5", "\uD611\uC5C5", "\uD300\uC6CC\uD06C") && !hasProcess && !hasOutcome) {
                return "collaboration_trait_only_title";
            }

            return null;
        }

        if (effectiveCategory == QuestionCategory.PERSONAL_GROWTH) {
            boolean hasValue = hasPersonalGrowthValueSignal(titleCore, normalizedTitle);
            boolean hasBehavior = hasPersonalGrowthBehaviorSignal(titleCore, normalizedTitle);

            if (containsAny(normalizedTitle, PERSONAL_GROWTH_TITLE_META_SIGNALS) && !hasValue && !hasBehavior) {
                return "personal_growth_meta_title";
            }

            if (containsAny(normalizedTitle,
                    "\uC9C0\uC6D0\uB3D9\uAE30",
                    "\uC9C1\uBB34\uC120\uD0DD",
                    "\uD68C\uC0AC\uC120\uD0DD",
                    "\uC785\uC0AC\uC774\uC720")) {
                return "personal_growth_motivation_drift_title";
            }

            return null;
        }

        if (effectiveCategory == QuestionCategory.CULTURE_FIT) {
            boolean hasBehavior = hasCultureFitTitleBehaviorSignal(titleCore, normalizedTitle);
            boolean hasImpact = hasCultureFitTitleImpactSignal(titleCore, normalizedTitle);

            if (containsAny(normalizedTitle, CULTURE_FIT_TITLE_META_SIGNALS) && !hasBehavior && !hasImpact) {
                return "culture_fit_meta_title";
            }

            if (containsAny(normalizedTitle, CULTURE_FIT_TITLE_GENERIC_TRAIT_SIGNALS) && !hasBehavior && !hasImpact) {
                return "culture_fit_trait_only_title";
            }

            return null;
        }

        if (effectiveCategory == QuestionCategory.TREND_INSIGHT) {
            boolean hasImplication = hasTrendInsightImplicationSignal(titleCore, normalizedTitle);
            boolean hasScene = hasTrendInsightSceneSignal(titleCore, normalizedTitle);
            boolean hasEvidence = hasTrendInsightEvidenceSignal(titleCore, normalizedTitle);
            boolean looksLikeAchievementSummary = looksLikeAchievementSummaryTitle(titleCore, normalizedTitle);

            if (containsAny(normalizedTitle, TREND_INSIGHT_TITLE_GENERIC_THEME_SIGNALS)
                    && !hasImplication
                    && !hasScene
                    && !hasEvidence) {
                return "trend_generic_theme_title";
            }

            if (looksLikeAchievementSummary
                    && !hasImplication
                    && !hasScene
                    && !hasEvidence) {
                return "trend_project_centric_title";
            }

            return null;
        }

        if (effectiveCategory != QuestionCategory.MOTIVATION) {
            return null;
        }

        boolean hasBridge = hasMotivationTitleBridge(titleCore, normalizedTitle);
        boolean hasValue = hasMotivationValueSignal(titleCore, normalizedTitle);
        boolean hasPlan = hasMotivationPlanSignal(titleCore, normalizedTitle);
        boolean hasEvidence = hasMotivationEvidenceSignal(titleCore, normalizedTitle);
        boolean looksLikeAchievementSummary = looksLikeAchievementSummaryTitle(titleCore, normalizedTitle);

        if (containsAny(normalizedTitle, MOTIVATION_TITLE_META_SIGNALS)) {
            return "motivation_meta_title";
        }

        if (looksLikeAchievementSummary && !hasBridge && !hasValue && !hasPlan) {
            return "motivation_past_only_title";
        }

        if (containsAny(normalizedTitle, MOTIVATION_TITLE_GENERIC_PRAISE_SIGNALS)
                && !hasEvidence
                && !hasValue
                && !hasPlan) {
            return "motivation_generic_praise_title";
        }

        if (containsAny(normalizedTitle, "\uCD5C\uC120\uC744\uB2E4\uD558", "\uC5F4\uC2EC\uD788\uD558", "\uD568\uAED8\uC131\uC7A5", "\uC131\uC7A5\uD558\uACA0\uC2B5\uB2C8", "\uAE30\uC5EC\uD558\uACA0\uC2B5\uB2C8")
                && !hasEvidence) {
            return "motivation_vague_promise_title";
        }

        return null;
    }

    private boolean hasMotivationTitleBridge(String titleCore, String normalizedTitle) {
        return containsAny(titleCore, MOTIVATION_TITLE_BRIDGE_SIGNALS)
                || containsAny(normalizedTitle, MOTIVATION_TITLE_BRIDGE_SIGNALS);
    }

    private boolean hasMotivationValueSignal(String titleCore, String normalizedTitle) {
        return containsAny(titleCore, MOTIVATION_TITLE_VALUE_SIGNALS)
                || containsAny(normalizedTitle, MOTIVATION_TITLE_VALUE_SIGNALS);
    }

    private boolean hasMotivationPlanSignal(String titleCore, String normalizedTitle) {
        return containsAny(titleCore, MOTIVATION_TITLE_PLAN_SIGNALS)
                || containsAny(normalizedTitle, MOTIVATION_TITLE_PLAN_SIGNALS);
    }

    private boolean hasMotivationEvidenceSignal(String titleCore, String normalizedTitle) {
        return looksLikeAchievementSummaryTitle(titleCore, normalizedTitle)
                || containsAny(normalizedTitle,
                "\uACBD\uD5D8",
                "\uAE30\uC900",
                "\uC900\uBE44",
                "\uD604\uC7A5",
                "\uAC80\uC99D",
                "\uBD84\uC11D",
                "\uC6B4\uC601",
                "\uC124\uACC4",
                "\uC815\uBE44",
                "\uD488\uC9C8");
    }

    private boolean hasExperienceTitleActionSignal(String titleCore, String normalizedTitle) {
        return containsAny(titleCore, EXPERIENCE_TITLE_ACTION_SIGNALS)
                || containsAny(normalizedTitle, EXPERIENCE_TITLE_ACTION_SIGNALS)
                || containsAny(titleCore, TITLE_ACTION_SIGNALS);
    }

    private boolean hasExperienceTitleResultSignal(String titleCore, String normalizedTitle) {
        return titleCore.chars().anyMatch(Character::isDigit)
                || containsAny(titleCore, EXPERIENCE_TITLE_RESULT_SIGNALS)
                || containsAny(normalizedTitle, EXPERIENCE_TITLE_RESULT_SIGNALS)
                || looksLikeAchievementSummaryTitle(titleCore, normalizedTitle);
    }

    private boolean hasProblemSolvingProblemSignal(String titleCore, String normalizedTitle) {
        return containsAny(titleCore, PROBLEM_SOLVING_TITLE_PROBLEM_SIGNALS)
                || containsAny(normalizedTitle, PROBLEM_SOLVING_TITLE_PROBLEM_SIGNALS);
    }

    private boolean hasProblemSolvingResolutionSignal(String titleCore, String normalizedTitle) {
        return containsAny(titleCore, PROBLEM_SOLVING_TITLE_RESOLUTION_SIGNALS)
                || containsAny(normalizedTitle, PROBLEM_SOLVING_TITLE_RESOLUTION_SIGNALS)
                || containsAny(titleCore, TITLE_ACTION_SIGNALS);
    }

    private boolean hasProblemSolvingJudgmentSignal(String titleCore, String normalizedTitle) {
        return containsAny(titleCore, PROBLEM_SOLVING_TITLE_JUDGMENT_SIGNALS)
                || containsAny(normalizedTitle, PROBLEM_SOLVING_TITLE_JUDGMENT_SIGNALS);
    }

    private boolean hasCollaborationRoleSignal(String titleCore, String normalizedTitle) {
        return containsAny(titleCore, COLLABORATION_TITLE_ROLE_SIGNALS)
                || containsAny(normalizedTitle, COLLABORATION_TITLE_ROLE_SIGNALS);
    }

    private boolean hasCollaborationProcessSignal(String titleCore, String normalizedTitle) {
        return containsAny(titleCore, COLLABORATION_TITLE_PROCESS_SIGNALS)
                || containsAny(normalizedTitle, COLLABORATION_TITLE_PROCESS_SIGNALS)
                || containsAny(titleCore, TITLE_ACTION_SIGNALS);
    }

    private boolean hasCollaborationOutcomeSignal(String titleCore, String normalizedTitle) {
        return titleCore.chars().anyMatch(Character::isDigit)
                || containsAny(titleCore, COLLABORATION_TITLE_OUTCOME_SIGNALS)
                || containsAny(normalizedTitle, COLLABORATION_TITLE_OUTCOME_SIGNALS)
                || looksLikeAchievementSummaryTitle(titleCore, normalizedTitle);
    }

    private boolean hasPersonalGrowthValueSignal(String titleCore, String normalizedTitle) {
        return containsAny(titleCore, PERSONAL_GROWTH_TITLE_VALUE_SIGNALS)
                || containsAny(normalizedTitle, PERSONAL_GROWTH_TITLE_VALUE_SIGNALS);
    }

    private boolean hasPersonalGrowthBehaviorSignal(String titleCore, String normalizedTitle) {
        return containsAny(titleCore, PERSONAL_GROWTH_TITLE_BEHAVIOR_SIGNALS)
                || containsAny(normalizedTitle, PERSONAL_GROWTH_TITLE_BEHAVIOR_SIGNALS)
                || containsAny(titleCore, TITLE_ACTION_SIGNALS);
    }

    private boolean hasCultureFitTitleBehaviorSignal(String titleCore, String normalizedTitle) {
        return containsAny(titleCore, CULTURE_FIT_TITLE_BEHAVIOR_SIGNALS)
                || containsAny(normalizedTitle, CULTURE_FIT_TITLE_BEHAVIOR_SIGNALS)
                || containsAny(titleCore, TITLE_ACTION_SIGNALS);
    }

    private boolean hasCultureFitTitleImpactSignal(String titleCore, String normalizedTitle) {
        return titleCore.chars().anyMatch(Character::isDigit)
                || containsAny(titleCore, CULTURE_FIT_TITLE_IMPACT_SIGNALS)
                || containsAny(normalizedTitle, CULTURE_FIT_TITLE_IMPACT_SIGNALS)
                || looksLikeAchievementSummaryTitle(titleCore, normalizedTitle);
    }

    private boolean hasTrendInsightImplicationSignal(String titleCore, String normalizedTitle) {
        return containsAny(titleCore, TREND_INSIGHT_TITLE_IMPLICATION_SIGNALS)
                || containsAny(normalizedTitle, TREND_INSIGHT_TITLE_IMPLICATION_SIGNALS);
    }

    private boolean hasTrendInsightSceneSignal(String titleCore, String normalizedTitle) {
        return containsAny(titleCore, TREND_INSIGHT_TITLE_SCENE_SIGNALS)
                || containsAny(normalizedTitle, TREND_INSIGHT_TITLE_SCENE_SIGNALS);
    }

    private boolean hasTrendInsightEvidenceSignal(String titleCore, String normalizedTitle) {
        return containsAny(titleCore, TREND_INSIGHT_TITLE_EVIDENCE_SIGNALS)
                || containsAny(normalizedTitle, TREND_INSIGHT_TITLE_EVIDENCE_SIGNALS);
    }

    private boolean looksLikeAchievementSummaryTitle(String titleCore, String normalizedTitle) {
        return titleCore.chars().anyMatch(Character::isDigit)
                || containsAny(titleCore, TITLE_ACTION_SIGNALS)
                || containsAny(normalizedTitle, TITLE_ACHIEVEMENT_SUMMARY_SIGNALS);
    }

    private int adjustTitleCandidateScore(int baseScore, String titleLine, QuestionCategory category) {
        int adjusted = Math.max(0, Math.min(100, baseScore));
        QuestionCategory effectiveCategory = category != null ? category : QuestionCategory.DEFAULT;
        String core = extractBracketTitleCore(titleLine);
        String normalizedCore = normalizeTitleComparison(core);
        if (core.isBlank() || normalizedCore.isBlank()) {
            return adjusted;
        }

        if (effectiveCategory == QuestionCategory.EXPERIENCE) {
            boolean hasAction = hasExperienceTitleActionSignal(core, normalizedCore);
            boolean hasResult = hasExperienceTitleResultSignal(core, normalizedCore);

            if (hasAction) {
                adjusted += 6;
            }

            if (hasResult) {
                adjusted += 6;
            }

            if (hasAction && hasResult) {
                adjusted += 4;
            }

            if (containsAny(normalizedCore, EXPERIENCE_TITLE_GENERIC_SIGNALS) && !hasAction && !hasResult) {
                adjusted -= 10;
            }

            if (containsAny(normalizedCore, "\uD504\uB85C\uC81D\uD2B8", "\uACBD\uD5D8") && !hasAction && !hasResult) {
                adjusted -= 8;
            }

            return Math.max(0, Math.min(100, adjusted));
        }

        if (effectiveCategory == QuestionCategory.PROBLEM_SOLVING) {
            boolean hasProblem = hasProblemSolvingProblemSignal(core, normalizedCore);
            boolean hasResolution = hasProblemSolvingResolutionSignal(core, normalizedCore);
            boolean hasJudgment = hasProblemSolvingJudgmentSignal(core, normalizedCore);
            boolean looksLikeAchievementSummary = looksLikeAchievementSummaryTitle(core, normalizedCore);

            if (hasProblem) {
                adjusted += 6;
            }

            if (hasResolution) {
                adjusted += 6;
            }

            if (hasJudgment) {
                adjusted += 4;
            }

            if (hasProblem && hasResolution) {
                adjusted += 4;
            }

            if (containsAny(normalizedCore, PROBLEM_SOLVING_TITLE_META_SIGNALS) && !hasProblem && !hasResolution) {
                adjusted -= 10;
            }

            if (looksLikeAchievementSummary && !hasProblem) {
                adjusted -= 6;
            }

            return Math.max(0, Math.min(100, adjusted));
        }

        if (effectiveCategory == QuestionCategory.COLLABORATION) {
            boolean hasRole = hasCollaborationRoleSignal(core, normalizedCore);
            boolean hasProcess = hasCollaborationProcessSignal(core, normalizedCore);
            boolean hasOutcome = hasCollaborationOutcomeSignal(core, normalizedCore);

            if (hasRole) {
                adjusted += 5;
            }

            if (hasProcess) {
                adjusted += 6;
            }

            if (hasOutcome) {
                adjusted += 5;
            }

            if (hasRole && hasProcess) {
                adjusted += 4;
            }

            if (containsAny(normalizedCore, COLLABORATION_TITLE_META_SIGNALS) && !hasRole && !hasProcess && !hasOutcome) {
                adjusted -= 10;
            }

            if (containsAny(normalizedCore, "\uC18C\uD1B5", "\uD611\uC5C5", "\uD300\uC6CC\uD06C") && !hasProcess && !hasOutcome) {
                adjusted -= 8;
            }

            return Math.max(0, Math.min(100, adjusted));
        }

        if (effectiveCategory == QuestionCategory.PERSONAL_GROWTH) {
            boolean hasValue = hasPersonalGrowthValueSignal(core, normalizedCore);
            boolean hasBehavior = hasPersonalGrowthBehaviorSignal(core, normalizedCore);

            if (hasValue) {
                adjusted += 5;
            }

            if (hasBehavior) {
                adjusted += 5;
            }

            if (hasValue && hasBehavior) {
                adjusted += 4;
            }

            if (containsAny(normalizedCore, PERSONAL_GROWTH_TITLE_META_SIGNALS) && !hasValue && !hasBehavior) {
                adjusted -= 10;
            }

            if (containsAny(normalizedCore,
                    "\uC9C0\uC6D0\uB3D9\uAE30",
                    "\uC9C1\uBB34\uC120\uD0DD",
                    "\uD68C\uC0AC\uC120\uD0DD",
                    "\uC785\uC0AC\uC774\uC720")) {
                adjusted -= 8;
            }

            return Math.max(0, Math.min(100, adjusted));
        }

        if (effectiveCategory == QuestionCategory.CULTURE_FIT) {
            boolean hasBehavior = hasCultureFitTitleBehaviorSignal(core, normalizedCore);
            boolean hasImpact = hasCultureFitTitleImpactSignal(core, normalizedCore);

            if (hasBehavior) {
                adjusted += 6;
            }

            if (hasImpact) {
                adjusted += 5;
            }

            if (hasBehavior && hasImpact) {
                adjusted += 4;
            }

            if (containsAny(normalizedCore, CULTURE_FIT_TITLE_META_SIGNALS) && !hasBehavior && !hasImpact) {
                adjusted -= 10;
            }

            if (containsAny(normalizedCore, CULTURE_FIT_TITLE_GENERIC_TRAIT_SIGNALS) && !hasBehavior && !hasImpact) {
                adjusted -= 8;
            }

            return Math.max(0, Math.min(100, adjusted));
        }

        if (effectiveCategory == QuestionCategory.TREND_INSIGHT) {
            boolean hasImplication = hasTrendInsightImplicationSignal(core, normalizedCore);
            boolean hasScene = hasTrendInsightSceneSignal(core, normalizedCore);
            boolean hasEvidence = hasTrendInsightEvidenceSignal(core, normalizedCore);
            boolean looksLikeAchievementSummary = looksLikeAchievementSummaryTitle(core, normalizedCore);

            if (hasImplication) {
                adjusted += 6;
            }

            if (hasScene) {
                adjusted += 6;
            }

            if (hasEvidence) {
                adjusted += 4;
            }

            if (hasImplication && hasScene) {
                adjusted += 4;
            }

            if (containsAny(normalizedCore, TREND_INSIGHT_TITLE_GENERIC_THEME_SIGNALS) && !hasImplication && !hasScene) {
                adjusted -= 10;
            }

            if (looksLikeAchievementSummary && !hasImplication && !hasScene && !hasEvidence) {
                adjusted -= 8;
            }

            return Math.max(0, Math.min(100, adjusted));
        }

        if (effectiveCategory != QuestionCategory.MOTIVATION) {
            return adjusted;
        }

        boolean hasBridge = hasMotivationTitleBridge(core, normalizedCore);
        boolean hasValue = hasMotivationValueSignal(core, normalizedCore);
        boolean hasPlan = hasMotivationPlanSignal(core, normalizedCore);
        boolean hasEvidence = hasMotivationEvidenceSignal(core, normalizedCore);
        boolean looksLikeAchievementSummary = looksLikeAchievementSummaryTitle(core, normalizedCore);

        if (hasEvidence && hasValue) {
            adjusted += 8;
        } else if (hasValue) {
            adjusted += 3;
        }

        if (hasEvidence && hasPlan) {
            adjusted += 6;
        } else if (hasPlan) {
            adjusted += 2;
        }

        if (hasEvidence && hasBridge) {
            adjusted += 4;
        } else if (hasBridge) {
            adjusted += 1;
        }

        if (hasEvidence && (hasValue || hasPlan)) {
            adjusted += 4;
        }

        if (!hasValue && !hasPlan && looksLikeAchievementSummary) {
            adjusted -= 12;
        }

        if (hasBridge && !hasEvidence) {
            adjusted -= 2;
        }

        if (hasMotivationTitleBridge(core, normalizedCore)) {
            adjusted += 2;
        }

        if (containsAny(core, "\uC9C0\uC6D0", "\uC785\uC0AC") && !hasEvidence && !hasValue && !hasPlan) {
            adjusted -= 4;
        }

        return Math.max(0, Math.min(100, adjusted));
    }

    private boolean isQuestionParaphraseTitle(String normalizedCore, String normalizedQuestion) {
        if (normalizedCore == null || normalizedCore.isBlank() || normalizedQuestion == null || normalizedQuestion.isBlank()) {
            return false;
        }

        if (normalizedQuestion.equals(normalizedCore)) {
            return true;
        }

        return normalizedCore.length() >= 14 && normalizedQuestion.contains(normalizedCore);
    }

    private String applyTitleLine(String text, String titleLine) {
        String normalizedText = normalizeLengthText(text);
        String normalizedTitleLine = normalizeTitleLine(titleLine);
        if (normalizedText == null || normalizedText.isBlank()) {
            return normalizedText == null ? null : normalizeTitleSpacing(normalizedText).trim();
        }
        if (!isBracketTitleLine(normalizedTitleLine)) {
            return collapseRepeatedLeadingTitleLines(normalizedText);
        }

        String[] lines = normalizedText.split("\n", -1);
        int firstNonBlankLineIndex = -1;
        for (int index = 0; index < lines.length; index++) {
            if (!lines[index].trim().isBlank()) {
                firstNonBlankLineIndex = index;
                break;
            }
        }

        if (firstNonBlankLineIndex < 0) {
            return normalizeTitleSpacing(normalizedTitleLine).trim();
        }

        if (isBracketTitleLine(lines[firstNonBlankLineIndex].trim())) {
            String previousTitleLine = lines[firstNonBlankLineIndex].trim();
            lines[firstNonBlankLineIndex] = normalizedTitleLine;
            String updated = collapseRepeatedLeadingTitleLines(String.join("\n", lines));
            log.debug("TRACE_TITLE applyTitleLine mode=replace previousTitle={} newTitle={} resultChars={} snippet={}",
                    safeSnippet(previousTitleLine, 80),
                    safeSnippet(normalizedTitleLine, 80),
                    countResumeCharacters(updated),
                    safeSnippet(updated, 160));
            return updated;
        }

        if (hasSameTitleCore(lines[firstNonBlankLineIndex].trim(), normalizedTitleLine)) {
            String previousTitleLine = lines[firstNonBlankLineIndex].trim();
            lines[firstNonBlankLineIndex] = normalizedTitleLine;
            String updated = collapseRepeatedLeadingTitleLines(String.join("\n", lines));
            log.debug("TRACE_TITLE applyTitleLine mode=normalize previousTitle={} newTitle={} resultChars={} snippet={}",
                    safeSnippet(previousTitleLine, 80),
                    safeSnippet(normalizedTitleLine, 80),
                    countResumeCharacters(updated),
                    safeSnippet(updated, 160));
            return updated;
        }

        String updated = collapseRepeatedLeadingTitleLines(normalizedTitleLine + "\n\n" + normalizedText.trim());
        log.debug("TRACE_TITLE applyTitleLine mode=prepend newTitle={} resultChars={} snippet={}",
                safeSnippet(normalizedTitleLine, 80),
                countResumeCharacters(updated),
                safeSnippet(updated, 160));
        return updated;
    }

    private String collapseRepeatedLeadingTitleLines(String text) {
        if (text == null) {
            return null;
        }

        String normalized = normalizeLengthText(normalizeTitleSpacing(text)).trim();
        if (normalized.isBlank()) {
            return normalized;
        }

        String[] lines = normalized.split("\n", -1);
        int firstNonBlankLineIndex = -1;
        for (int index = 0; index < lines.length; index++) {
            if (!lines[index].trim().isBlank()) {
                firstNonBlankLineIndex = index;
                break;
            }
        }

        if (firstNonBlankLineIndex < 0) {
            return normalized;
        }

        String firstKey = titleLineComparisonKey(lines[firstNonBlankLineIndex]);
        if (firstKey.isBlank()) {
            return normalized;
        }

        List<String> cleaned = new ArrayList<>();
        boolean changed = false;
        boolean scanningLeadingTitleBlock = true;
        boolean pendingBlankAfterSkippedTitle = false;

        for (int index = 0; index < lines.length; index++) {
            String line = lines[index];
            String trimmed = line.trim();

            if (index <= firstNonBlankLineIndex) {
                cleaned.add(line);
                continue;
            }

            if (scanningLeadingTitleBlock && trimmed.isBlank()) {
                if (!pendingBlankAfterSkippedTitle) {
                    cleaned.add(line);
                } else {
                    changed = true;
                }
                continue;
            }

            if (scanningLeadingTitleBlock && firstKey.equals(titleLineComparisonKey(trimmed))) {
                changed = true;
                pendingBlankAfterSkippedTitle = true;
                continue;
            }

            scanningLeadingTitleBlock = false;
            pendingBlankAfterSkippedTitle = false;
            cleaned.add(line);
        }

        return changed ? normalizeTitleSpacing(String.join("\n", cleaned)).trim() : normalized;
    }

    private boolean hasSameTitleCore(String existingLine, String titleLine) {
        String existingKey = titleLineComparisonKey(existingLine);
        String titleKey = titleLineComparisonKey(titleLine);
        return !existingKey.isBlank() && existingKey.equals(titleKey);
    }

    private String titleLineComparisonKey(String line) {
        if (line == null) {
            return "";
        }

        String trimmed = line.trim()
                .replaceAll("^\\*\\*", "")
                .replaceAll("\\*\\*$", "")
                .trim();
        if (isBracketTitleLine(trimmed)) {
            trimmed = extractBracketTitleCore(trimmed);
        }

        return normalizeTitleComparison(trimmed);
    }

    private String summarizeDraftForOverlap(String draft) {
        if (draft == null || draft.isBlank()) {
            return "No draft content";
        }

        String normalized = draft.replaceAll("\\s+", " ").trim();
        return safeSnippet(normalized, 320);
    }

    /**
     * title/text 분리 응답을 "[제목]\n\n본문" 형태로 조립합니다.
     * title이 없으면 text만 반환합니다 (fallback).
     */
    private DraftParams buildDraftParams(
            String company,
            String position,
            String questionTitle,
            String companyContext,
            int maxLength,
            int minTargetChars,
            int maxTargetChars,
            String context,
            String others,
            String directive
    ) {
        return buildDraftParams(company, position, questionTitle, companyContext,
                maxLength, minTargetChars, maxTargetChars, context, others, directive, null);
    }

    private DraftParams buildDraftParams(
            String company,
            String position,
            String questionTitle,
            String companyContext,
            int maxLength,
            int minTargetChars,
            int maxTargetChars,
            String context,
            String others,
            String directive,
            String writingGuideContext
    ) {
        return buildDraftParams(company, position, questionTitle, companyContext,
                maxLength, minTargetChars, maxTargetChars, context, others, directive, writingGuideContext, null);
    }

    private DraftParams buildDraftParams(
            String company,
            String position,
            String questionTitle,
            String companyContext,
            int maxLength,
            int minTargetChars,
            int maxTargetChars,
            String context,
            String others,
            String directive,
            String writingGuideContext,
            java.util.List<String> additionalIntents
    ) {
        return DraftParams.builder()
                .company(company)
                .position(position)
                .questionTitle(questionTitle)
                .companyContext(companyContext)
                .maxLength(maxLength)
                .minTarget(minTargetChars)
                .maxTarget(maxTargetChars)
                .experienceContext(context)
                .othersContext(others)
                .directive(directive)
                .writingGuideContext(writingGuideContext)
                .additionalIntents(additionalIntents)
                .build();
    }

    private WorkspaceDraftAiService.DraftResponse generateDraftWithStrategy(
            QuestionCategory category,
            DraftParams params
    ) {
        List<ChatMessage> strategyMessages = promptFactory.buildMessages(category, params);
        return strategyDraftGeneratorService.generate(strategyMessages);
    }

    private WorkspaceDraftAiService.DraftResponse generateRefinedDraftWithStrategy(
            QuestionCategory category,
            DraftParams params,
            String currentDraft,
            boolean lengthRetry
    ) {
        List<ChatMessage> strategyMessages = promptFactory.buildRefineMessages(
                category,
                params,
                currentDraft,
                lengthRetry);
        return strategyDraftGeneratorService.generate(strategyMessages);
    }

    private String assembleDraftText(WorkspaceDraftAiService.DraftResponse response) {
        if (response == null) return null;
        String body = response.text != null ? response.text.trim() : "";
        if (response.title != null && !response.title.isBlank()) {
            return "[" + response.title.trim() + "]" + "\n\n" + body;
        }
        return body;
    }

    private String safeSnippet(String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return "None";
        }
        if (value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, Math.max(0, maxLength - 3)) + "...";
    }

    private String resolveUserFacingErrorMessage(Exception e) {
        if (e instanceof IllegalStateException && e.getMessage() != null
                && e.getMessage().contains("minimum length requirement")) {
            return "최소 글자수 요구사항을 만족하는 초안을 끝내 만들지 못했습니다. 조건을 조금 완화해 다시 시도해 주세요.";
        }
        return "처리 중 오류가 발생했습니다. 잠시 후 다시 시도해 주세요.";
    }

    private void sendMinimumLengthWarningIfNeeded(SseEmitter emitter, String draft, int minTargetChars) {
        if (draft == null || draft.isBlank() || minTargetChars <= 0) {
            return;
        }

        if (countResumeCharacters(draft) >= minTargetChars) {
            return;
        }

        sendSse(emitter, "warning", Map.of(
                "message", "최소 글자수 요구사항을 완전히 충족하지 못해 가장 목표에 근접한 초안을 기준으로 세탁 결과를 보여드렸습니다."));
    }

    private Map<String, Object> buildCompletionResult(
            String sourceDraft,
            int minTargetChars,
            String washedDraft,
            String humanPatchedDraft,
            DraftAnalysisResult analysis) {
        boolean usedFallbackDraft = sourceDraft != null
                && !sourceDraft.isBlank()
                && minTargetChars > 0
                && countResumeCharacters(sourceDraft) < minTargetChars;

        Map<String, Object> result = new HashMap<>();
        result.put("draft", washedDraft);
        result.put("humanPatched", washedDraft);
        result.put("sourceDraft", sourceDraft);
        result.put("usedFallbackDraft", usedFallbackDraft);
        result.put("fallbackDraft", usedFallbackDraft ? sourceDraft : null);
        result.put(
                "warningMessage",
                usedFallbackDraft
                        ? "최소 글자수를 완전히 충족하지 못해 가장 목표에 근접한 초안을 기준으로 세탁했습니다."
                        : null);
        result.put("mistranslations", analysis.getMistranslations());
        result.put("aiReviewReport", analysis.getAiReviewReport());
        return result;
    }

    private int[] resolveTargetRange(
            int maxLength,
            String directive,
            Integer targetChars,
            double defaultMinRatio,
            double defaultMaxRatio) {
        RequestedLengthDirective requestedLength = resolveRequestedLengthDirective(directive, targetChars, maxLength);
        if (requestedLength != null) {
            return createRequestedTargetWindow(requestedLength, maxLength);
        }
        return createDefaultTargetWindow(maxLength, defaultMinRatio, defaultMaxRatio);
    }

    private String augmentDirectiveForPrompt(String directive, int maxLength, Integer targetChars) {
        String normalized = directive == null || directive.isBlank() ? NO_EXTRA_USER_DIRECTIVE : directive.trim();
        int[] targetRange = resolveTargetRange(
                maxLength,
                directive,
                targetChars,
                DEFAULT_TARGET_MIN_RATIO,
                DEFAULT_TARGET_MAX_RATIO);
        StringBuilder builder = new StringBuilder();
        if (!NO_EXTRA_USER_DIRECTIVE.equals(normalized)) {
            builder.append("=== USER DIRECTIVE (MANDATORY — follow every line) ===\n");
            builder.append("Rules:\n");
            builder.append("- Every line in the directive below is an independent instruction. Apply ALL of them.\n");
            builder.append("- If the directive lists a numbered/bulleted structure (e.g., '1. …', '- …', '구조 a', '단락 1'), treat each item as a required paragraph or section in the output.\n");
            builder.append("- If the directive says to avoid or exclude something, suppress it even if it appears in retrieved context.\n");
            builder.append("- If the directive names a specific project, technology, or role to emphasize, prioritize that framing.\n");
            builder.append("- Do not merge, skip, or paraphrase directive lines — apply them verbatim in spirit.\n");
            builder.append("Directive:\n");
            builder.append("---\n");
            builder.append(normalized).append("\n");
            builder.append("---\n");
            builder.append("=== END OF USER DIRECTIVE ===\n");
        }

        builder.append("Length guidance:\n");
        builder.append("- Count only the value of the text field in the JSON output.\n");
        builder.append("- Do not count braces, quotes, key names, or escape characters.\n");
        builder.append("- Keep the text field between ")
                .append(targetRange[0])
                .append(" and ")
                .append(targetRange[1])
                .append(" visible characters.\n");
        if (maxLength > 0) {
            builder.append("- The hard limit is ")
                    .append(maxLength)
                    .append(" visible characters. Stay below it with margin; over-limit output is invalid.\n");
        }
        builder.append("- Recount before returning. If the answer is short, add concrete evidence and explanation instead of filler.");

        return builder.toString().trim();
    }

    private String mergeDirectiveLayers(String strategyDirective, String userDirective) {
        List<String> layers = new ArrayList<>();
        if (strategyDirective != null && !strategyDirective.isBlank()) {
            layers.add(strategyDirective.trim());
        }
        if (userDirective != null && !userDirective.isBlank()) {
            layers.add(userDirective.trim());
        }
        if (layers.isEmpty()) {
            return NO_EXTRA_USER_DIRECTIVE;
        }
        return String.join("\n\n", layers);
    }

    private RequestedLengthDirective resolveRequestedLengthDirective(String directive, Integer targetChars, int maxLength) {
        if (targetChars != null && targetChars > 0) {
            int clamped = clampLengthTarget(targetChars, maxLength);
            return new RequestedLengthDirective(clamped, clamped);
        }

        RequestedLengthDirective requestedLength = extractRequestedLengthDirective(directive, maxLength);
        if (requestedLength == null) {
            return null;
        }
        return new RequestedLengthDirective(
                clampLengthTarget(requestedLength.minimum(), maxLength),
                clampLengthTarget(requestedLength.preferredTarget(), maxLength));
    }

    private int[] createRequestedTargetWindow(RequestedLengthDirective requestedLength, int maxLength) {
        int lower = Math.max(1, requestedLength.minimum());
        int upper = Math.max(lower, requestedLength.preferredTarget());
        if (maxLength > 0) {
            upper = Math.min(upper, maxLength);
        }
        return new int[] { lower, Math.max(lower, upper) };
    }

    private int[] createDefaultTargetWindow(int maxLength, double defaultMinRatio, double defaultMaxRatio) {
        if (maxLength <= 0) {
            return new int[] { 1, 1 };
        }

        int lower = Math.max(1, (int) Math.ceil(maxLength * defaultMinRatio));
        int upper = Math.max(lower, Math.min(maxLength, (int) Math.floor(maxLength * defaultMaxRatio)));
        return new int[] { lower, upper };
    }

    private int clampLengthTarget(int target, int maxLength) {
        int clamped = Math.max(1, target);
        if (maxLength > 0) {
            clamped = Math.min(clamped, maxLength);
        }
        return clamped;
    }

    private RequestedLengthDirective extractRequestedLengthDirective(String directive, int maxLength) {
        if (directive == null || directive.isBlank()) {
            return null;
        }

        List<Integer> mentionedLengths = new ArrayList<>();
        Matcher matcher = REQUESTED_LENGTH_PATTERN.matcher(directive);
        while (matcher.find()) {
            mentionedLengths.add(parseLengthNumber(matcher.group(1)));
        }
        if (mentionedLengths.isEmpty()) {
            return null;
        }

        Integer explicitMinimum = extractPatternLength(directive, PREFIX_MINIMUM_LENGTH_PATTERN,
                SUFFIX_MINIMUM_LENGTH_PATTERN);
        int minimum = explicitMinimum != null ? explicitMinimum : mentionedLengths.get(0);
        int preferredTarget = resolvePreferredRequestedTarget(mentionedLengths, minimum);

        if (maxLength > 0) {
            minimum = Math.min(minimum, maxLength);
            preferredTarget = Math.min(preferredTarget, maxLength);
        }

        preferredTarget = Math.max(minimum, preferredTarget);
        return new RequestedLengthDirective(Math.max(1, minimum), Math.max(1, preferredTarget));
    }

    private Integer extractPatternLength(String directive, Pattern... patterns) {
        if (directive == null || directive.isBlank()) {
            return null;
        }

        for (Pattern pattern : patterns) {
            Matcher matcher = pattern.matcher(directive);
            if (matcher.find()) {
                return parseLengthNumber(matcher.group(1));
            }
        }

        return null;
    }

    private int parseLengthNumber(String value) {
        return Integer.parseInt(value.replace(",", ""));
    }

    private int resolvePreferredRequestedTarget(List<Integer> mentionedLengths, int minimum) {
        if (mentionedLengths == null || mentionedLengths.isEmpty()) {
            return minimum;
        }

        int ceiling = mentionedLengths.stream()
                .mapToInt(Integer::intValue)
                .max()
                .orElse(minimum);

        return Math.max(minimum, ceiling);
    }

    private String expandToMinimumLength(
            String text,
            QuestionCategory category,
            int minTargetChars,
            int preferredTargetChars,
            int maxLength,
            String company,
            String position,
            String questionTitle,
            String companyContext,
            String context,
            String others,
            String directive) {
        String normalized = normalizeLengthText(normalizeTitleSpacing(text)).trim();
        int currentLength = countResumeCharacters(normalized);
        logLengthMetrics("expand", maxLength, minTargetChars, preferredTargetChars, normalized, 0);
        if (normalized.isBlank() || minTargetChars <= 0 || preferredTargetChars <= 0) {
            return normalized;
        }
        if (currentLength >= minTargetChars && currentLength <= preferredTargetChars) {
            return normalized;
        }

        String bestCandidate = normalized;
        String familyCandidate = normalized;

        for (int family = 1; family <= MINIMUM_LENGTH_DRAFT_FAMILIES; family++) {
            if (family > 1) {
                familyCandidate = regenerateFreshDraftFamily(
                        bestCandidate,
                        category,
                        minTargetChars,
                        preferredTargetChars,
                        maxLength,
                        company,
                        position,
                        questionTitle,
                        companyContext,
                        context,
                        others,
                        directive,
                        family);
                int regenLength = countResumeCharacters(familyCandidate);
                boolean regenAccepted = isBetterLengthCandidate(
                        familyCandidate,
                        bestCandidate,
                        minTargetChars,
                        preferredTargetChars,
                        maxLength);
                if (regenAccepted) {
                    bestCandidate = familyCandidate;
                }
                log.warn("[RUN] REGEN F{} │ 결과:{}자{} / 전체최고:{}자{} │ (목표:{}-{}) │ {}",
                        family,
                        regenLength, resolveLengthStatus(regenLength, minTargetChars, preferredTargetChars, maxLength),
                        countResumeCharacters(bestCandidate), resolveLengthStatus(countResumeCharacters(bestCandidate), minTargetChars, preferredTargetChars, maxLength),
                        minTargetChars, preferredTargetChars,
                        regenAccepted ? "✓ 최고 갱신" : "- 이전 최고 유지");
                if (isWithinTargetWindow(familyCandidate, minTargetChars, preferredTargetChars)) {
                    return familyCandidate;
                }
            }

            log.warn("[RUN] EXPAND F{} 시작 │ 시드:{}자{} / 전체최고:{}자{} │ (목표:{}-{} / 상한:{})",
                    family,
                    countResumeCharacters(familyCandidate), resolveLengthStatus(countResumeCharacters(familyCandidate), minTargetChars, preferredTargetChars, maxLength),
                    countResumeCharacters(bestCandidate), resolveLengthStatus(countResumeCharacters(bestCandidate), minTargetChars, preferredTargetChars, maxLength),
                    minTargetChars, preferredTargetChars, maxLength);

            String expandedFamilyCandidate = expandDraftFamily(
                    familyCandidate,
                    category,
                    minTargetChars,
                    preferredTargetChars,
                    maxLength,
                    company,
                    position,
                    questionTitle,
                    companyContext,
                    context,
                    others,
                    directive,
                    family);
            int expandedLength = countResumeCharacters(expandedFamilyCandidate);
            boolean expandAccepted = isBetterLengthCandidate(
                    expandedFamilyCandidate,
                    bestCandidate,
                    minTargetChars,
                    preferredTargetChars,
                    maxLength);
            if (expandAccepted) {
                bestCandidate = expandedFamilyCandidate;
            }
            log.warn("[{}] EXPAND F{} 종료 │ F{}최고:{}자{} / 전체최고:{}자{} │ (목표:{}-{}) │ {}",
                    expandAccepted ? "OK" : "WARN",
                    family, family,
                    expandedLength, resolveLengthStatus(expandedLength, minTargetChars, preferredTargetChars, maxLength),
                    countResumeCharacters(bestCandidate), resolveLengthStatus(countResumeCharacters(bestCandidate), minTargetChars, preferredTargetChars, maxLength),
                    minTargetChars, preferredTargetChars,
                    expandAccepted ? "✓ 전체최고 갱신" : "- 이전 최고 유지");
            if (isWithinTargetWindow(expandedFamilyCandidate, minTargetChars, preferredTargetChars)) {
                return expandedFamilyCandidate;
            }
        }

        int finalLength = countResumeCharacters(bestCandidate);
        log.warn("{} DRAFT F{}#- │ {}자 {} │ (목표:{}-{} / 상한:{}) │ → {}",
                resolveStageIcon("DRAFT", "FAILED"),
                MINIMUM_LENGTH_DRAFT_FAMILIES,
                finalLength, resolveStatusIndicator("FAILED"),
                minTargetChars, preferredTargetChars, maxLength,
                toKoreanNextAction("ABORT"));
        return bestCandidate;
    }

    private String expandDraftFamily(
            String seedCandidate,
            QuestionCategory category,
            int minTargetChars,
            int preferredTargetChars,
            int maxLength,
            String company,
            String position,
            String questionTitle,
            String companyContext,
            String context,
            String others,
            String directive,
            int family) {
        String candidate = normalizeLengthText(normalizeTitleSpacing(seedCandidate)).trim();

        for (int attempt = 1; attempt <= MINIMUM_LENGTH_EXPANSION_ATTEMPTS; attempt++) {
            int bestLengthBefore = countResumeCharacters(candidate);
            if (bestLengthBefore >= minTargetChars && bestLengthBefore <= preferredTargetChars) {
                return candidate;
            }

            boolean underMin = bestLengthBefore < minTargetChars;
            try {
                String adjustedCandidate = underMin
                        ? expandDraftCandidate(
                                candidate,
                                category,
                                bestLengthBefore,
                                minTargetChars,
                                preferredTargetChars,
                                maxLength,
                                company,
                                position,
                                questionTitle,
                                companyContext,
                                context,
                                others,
                                directive,
                                family,
                                attempt)
                        : shortenDraftCandidate(
                                candidate,
                                minTargetChars,
                                preferredTargetChars,
                                maxLength,
                                company,
                                position,
                                companyContext,
                                context,
                                others);

                int resultLength = countResumeCharacters(adjustedCandidate);
                boolean accepted = isBetterLengthCandidate(
                        adjustedCandidate,
                        candidate,
                        minTargetChars,
                        preferredTargetChars,
                        maxLength);
                if (accepted) {
                    candidate = adjustedCandidate;
                }

                int bestLengthAfter = countResumeCharacters(candidate);
                String resultStatus = resolveLengthStatus(resultLength, minTargetChars, preferredTargetChars, maxLength);
                String bestStatus   = resolveLengthStatus(bestLengthAfter, minTargetChars, preferredTargetChars, maxLength);
                String acceptedMark = accepted ? "✓ 갱신" : "- 유지";
                log.warn("[WARN] {} F{} #{}/{} │ 결과:{}자{} / 최고:{}자{} │ (목표:{}-{}) │ {}",
                        underMin ? "EXPAND" : "SHORTEN",
                        family, attempt, MINIMUM_LENGTH_EXPANSION_ATTEMPTS,
                        resultLength, resultStatus,
                        bestLengthAfter, bestStatus,
                        minTargetChars, preferredTargetChars,
                        acceptedMark);

                if (isWithinTargetWindow(candidate, minTargetChars, preferredTargetChars)) {
                    return candidate;
                }
            } catch (Exception e) {
                log.warn("[ERROR] EXPAND F{} #{}/{} │ 최고:{}자 │ (목표:{}-{}) │ 확장 호출 실패",
                        family, attempt, MINIMUM_LENGTH_EXPANSION_ATTEMPTS,
                        bestLengthBefore,
                        minTargetChars, preferredTargetChars, e);
            }
        }

        int finalLength = countResumeCharacters(candidate);
        log.warn("[ERROR] EXPAND F{} 계열실패 │ 최고:{}자↓ │ (목표:{}-{}) │ → F{} 재생성",
                family, finalLength, minTargetChars, preferredTargetChars, family + 1);
        return candidate;
    }

    private String regenerateFreshDraftFamily(
            String previousBestDraft,
            QuestionCategory category,
            int minTargetChars,
            int preferredTargetChars,
            int maxLength,
            String company,
            String position,
            String questionTitle,
            String companyContext,
            String context,
            String others,
            String directive,
            int family) {
        int previousLength = countResumeCharacters(previousBestDraft);
        log.warn("{} REGENERATE F{}#- │ {}자 {} │ (목표:{}-{} / 상한:{}) │ → {}",
                resolveStageIcon("REGENERATE", "START"),
                family, previousLength, resolveStatusIndicator("START"),
                minTargetChars, preferredTargetChars, maxLength,
                toKoreanNextAction("GENERATE_FRESH_FAMILY"));

        try {
            String regenerationDirective = buildMinimumLengthRegenerationDirective(
                    directive,
                    previousBestDraft,
                    previousLength,
                    minTargetChars,
                    preferredTargetChars,
                    maxLength,
                    family);
            DraftParams draftParams = buildDraftParams(
                    company,
                    position,
                    questionTitle,
                    companyContext,
                    maxLength,
                    minTargetChars,
                    preferredTargetChars,
                    context,
                    others,
                    regenerationDirective);
            WorkspaceDraftAiService.DraftResponse regenerated = generateDraftWithStrategy(category, draftParams);

            String regeneratedText = assembleDraftText(regenerated);
            if (regenerated == null || regeneratedText == null || regeneratedText.isBlank()) {
                return previousBestDraft;
            }

            return prepareDraftForTranslation(
                    regeneratedText,
                    maxLength,
                    minTargetChars,
                    preferredTargetChars,
                    company,
                    position,
                    companyContext,
                    context,
                    others);
        } catch (Exception e) {
            log.warn("{} REGENERATE F{}#- │ {}자 {} │ (목표:{}-{} / 상한:{}) │ → {} [이유=재생성 실패]",
                    resolveStageIcon("REGENERATE", "ERROR"),
                    family, previousLength, resolveStatusIndicator("ERROR"),
                    minTargetChars, preferredTargetChars, maxLength,
                    toKoreanNextAction("KEEP_PREVIOUS_BEST"), e);
            return previousBestDraft;
        }
    }

    private String expandDraftCandidate(
            String candidate,
            QuestionCategory category,
            int candidateLength,
            int minTargetChars,
            int preferredTargetChars,
            int maxLength,
            String company,
            String position,
            String questionTitle,
            String companyContext,
            String context,
            String others,
            String directive,
            int family,
            int attempt) {
        String expansionDirective = buildMinimumLengthDirective(
                candidate,
                directive,
                candidateLength,
                minTargetChars,
                preferredTargetChars,
                maxLength,
                family,
                attempt);

        DraftParams draftParams = buildDraftParams(
                company,
                position,
                questionTitle,
                companyContext,
                maxLength,
                minTargetChars,
                preferredTargetChars,
                context,
                others,
                expansionDirective);

        WorkspaceDraftAiService.DraftResponse expanded = generateRefinedDraftWithStrategy(
                category,
                draftParams,
                candidate,
                true);

        if (expanded == null || expanded.text == null || expanded.text.isBlank()) {
            return candidate;
        }

        return normalizeLengthText(normalizeTitleSpacing(assembleDraftText(expanded))).trim();
    }

    private String shortenDraftCandidate(
            String candidate,
            int minTargetChars,
            int preferredTargetChars,
            int maxLength,
            String company,
            String position,
            String companyContext,
            String context,
            String others) {
        WorkspaceDraftAiService.DraftResponse shortened = workspaceDraftAiService.shortenToLimit(
                safeTrim(company),
                safeTrim(position),
                safeTrim(companyContext),
                candidate,
                maxLength,
                safeTrim(context),
                safeTrim(others));

        if (shortened == null || shortened.text == null || shortened.text.isBlank()) {
            return hardTrimToLimit(candidate, maxLength);
        }

        String normalized = normalizeLengthText(normalizeTitleSpacing(shortened.text)).trim();
        int normalizedLength = countResumeCharacters(normalized);
        if (normalizedLength < minTargetChars) {
            return hardTrimToLimit(candidate, maxLength);
        }
        if (normalizedLength > maxLength) {
            return hardTrimToLimit(normalized, maxLength);
        }
        return normalized;
    }

    private String buildMinimumLengthDirective(
            String candidate,
            String directive,
            int previousLength,
            int minTargetChars,
            int preferredTargetChars,
            int maxLength,
            int family,
            int attempt) {
        int minGap = Math.max(0, minTargetChars - previousLength);
        int preferredGap = Math.max(minGap, preferredTargetChars - previousLength);
        StringBuilder builder = new StringBuilder();
        builder.append(LENGTH_RETRY_MARKER).append("\n");
        if (directive != null && !directive.isBlank()) {
            builder.append(directive.trim()).append("\n");
        }
        builder.append("Retry feedback: previous output length was ")
                .append(previousLength)
                .append(" characters, which is below the minimum target.\n");
        builder.append("Draft family: ")
                .append(family)
                .append(" / ")
                .append(MINIMUM_LENGTH_DRAFT_FAMILIES)
                .append(".\n");
        builder.append("Retry attempt: ")
                .append(attempt)
                .append(" / ")
                .append(MINIMUM_LENGTH_EXPANSION_ATTEMPTS)
                .append(".\n");
        builder.append("Target text-field window for this retry: ")
                .append(minTargetChars)
                .append(" to ")
                .append(preferredTargetChars)
                .append(" visible characters.\n");
        builder.append("Hard limit: ")
                .append(maxLength)
                .append(" characters.\n");
        builder.append("Count only the value of the text field. Do not count braces, quotes, key names, or escape characters.\n");
        builder.append("Current deficit to minimum target: at least ")
                .append(minGap)
                .append(" more visible characters.\n");
        builder.append("Expansion goal for this retry: add roughly ")
                .append(preferredGap)
                .append(" visible characters so the final answer lands inside the target window.\n");
        builder.append("Floor constraint: your output MUST be strictly longer than the current draft (")
                .append(previousLength)
                .append(" chars). If your rewrite comes out shorter, discard it and try again with more detail.\n");
        builder.append("Do not summarize, compress, or delete any existing content from the current draft.\n");
        builder.append(buildEpisodeExpansionHints(candidate, minGap, preferredTargetChars));
        builder.append(buildDynamicExpansionTactic(minGap, attempt)).append("\n");
        builder.append("Count spaces and line breaks as 1 character each. Generic filler is forbidden.");
        return builder.toString();
    }

    private String buildEpisodeExpansionHints(String candidate, int minGap, int preferredTargetChars) {
        if (candidate == null || candidate.isBlank() || minGap <= 0) return "";

        StringBuilder sb = new StringBuilder();

        // Critical framing: directive defines scope, not depth
        sb.append("Important: the directive above defines CONTENT SCOPE (which topics are allowed), NOT depth or detail level. ");
        sb.append("The draft is ").append(minGap).append("+ characters short — deepen the EXISTING content; do NOT add off-scope topics.\n");

        // Episode checklist: all present elements must be deepened
        int perElementBudget = minGap / 4 + 1;
        sb.append("Episode depth mandate — expand EVERY element already present in the draft, not just the thinnest one.\n");
        sb.append("For each element below that appears in the current draft, add at least ").append(perElementBudget).append(" more characters of concrete detail:\n");
        sb.append("  • background/context: why this problem existed, what constraint triggered it\n");
        sb.append("  • judgment: what alternative you considered and why you chose this specific path\n");
        sb.append("  • execution: a concrete step, method, or tool and exactly how it was applied\n");
        sb.append("  • result: a specific metric, timeline, or observable change that proved success\n");

        return sb.toString();
    }

    private String buildMinimumLengthRegenerationDirective(
            String directive,
            String previousDraft,
            int previousLength,
            int minTargetChars,
            int preferredTargetChars,
            int maxLength,
            int family) {
        int minGap = Math.max(0, minTargetChars - previousLength);
        int preferredGap = Math.max(minGap, preferredTargetChars - previousLength);
        StringBuilder builder = new StringBuilder();
        builder.append(LENGTH_REGENERATION_MARKER).append("\n");
        if (directive != null && !directive.isBlank()) {
            builder.append(directive.trim()).append("\n");
        }
        builder.append("Regeneration feedback: the previous draft stayed at ")
                .append(previousLength)
                .append(" characters and failed to reach the required range after expansion retries.\n");
        builder.append("New draft family: ")
                .append(family)
                .append(" / ")
                .append(MINIMUM_LENGTH_DRAFT_FAMILIES)
                .append(".\n");
        builder.append("Required output range for this fresh attempt: ")
                .append(minTargetChars)
                .append(" to ")
                .append(preferredTargetChars)
                .append(" visible characters.\n");
        builder.append("Hard limit: ")
                .append(maxLength)
                .append(" characters.\n");
        builder.append("You must write a fresh draft angle instead of lightly paraphrasing the failed draft.\n");
        builder.append("Keep the same facts, question intent, and anti-overlap constraints, but choose a fuller structure, stronger evidence ordering, or a different valid lead sentence so the answer naturally reaches the target window.\n");
        builder.append("At minimum, add ")
                .append(minGap)
                .append(" visible characters beyond the failed draft and aim to add about ")
                .append(preferredGap)
                .append(".\n");
        builder.append("Do not copy the failed draft sentence-by-sentence. Rebuild it from the supplied context with denser factual detail and clearer causality.\n");
        builder.append("Failed draft for reference only:\n")
                .append(previousDraft);
        return builder.toString();
    }

    private String buildDynamicExpansionTactic(int minGap, int attempt) {
        if (minGap >= 180) {
            return "This is a large deficit. Use a fuller narrative shape with two evidence-rich movements, and unpack constraints, decisions, execution detail, and outcome causality so the text grows materially without filler.";
        }
        if (minGap >= 110) {
            return "This is a medium-large deficit. Expand the current example with concrete obstacles, trade-offs, implementation detail, and the reason each action mattered to the final result.";
        }
        if (minGap >= 60) {
            return "This is a moderate deficit. Add the missing rationale, execution sequence, and result interpretation instead of repeating high-level claims.";
        }
        if (attempt >= MINIMUM_LENGTH_EXPANSION_ATTEMPTS) {
            return "This is the final expansion retry. If the current angle still feels too thin, broaden the explanation with another valid fact or sub-problem already present in the supplied context.";
        }
        return "This is a small deficit. Add only the missing factual glue, such as why the problem mattered, what judgment you made, and what changed afterward.";
    }

    private int[] resolveExpansionRetryWindow(
            int currentLength,
            int minTargetChars,
            int preferredTargetChars,
            int maxLength,
            int attempt) {
        return new int[] {
                clampLengthTarget(minTargetChars, maxLength),
                clampLengthTarget(Math.max(minTargetChars, preferredTargetChars), maxLength)
        };
    }

    private int[] resolveRegenerationRetryWindow(
            int currentLength,
            int minTargetChars,
            int preferredTargetChars,
            int maxLength) {
        return new int[] {
                clampLengthTarget(minTargetChars, maxLength),
                clampLengthTarget(Math.max(minTargetChars, preferredTargetChars), maxLength)
        };
    }

    private boolean isWithinTargetWindow(String text, int minTargetChars, int preferredTargetChars) {
        int length = countResumeCharacters(text);
        return length >= minTargetChars && length <= preferredTargetChars;
    }

    private boolean isBetterLengthCandidate(
            String challenger,
            String incumbent,
            int minTargetChars,
            int preferredTargetChars,
            int maxLength) {
        return resolveLengthFitnessScore(challenger, minTargetChars, preferredTargetChars, maxLength)
                > resolveLengthFitnessScore(incumbent, minTargetChars, preferredTargetChars, maxLength);
    }

    private int resolveLengthFitnessScore(String text, int minTargetChars, int preferredTargetChars, int maxLength) {
        int length = countResumeCharacters(text);
        if (length >= minTargetChars && length <= preferredTargetChars) {
            return 2_000_000 + length;
        }

        int distanceToWindow = length < minTargetChars
                ? (minTargetChars - length)
                : (length - preferredTargetChars);
        int hardLimitPenalty = maxLength > 0 && length > maxLength ? 100_000 : 0;
        int overTargetPenalty = length > preferredTargetChars ? 10_000 : 0;
        return 1_000_000 - hardLimitPenalty - overTargetPenalty - distanceToWindow;
    }

    private String selectBetterDraft(
            String primary,
            String alternative,
            int minTargetChars,
            int preferredTargetChars,
            int maxLength) {
        if (alternative == null || alternative.isBlank()) {
            return primary;
        }
        if (primary == null || primary.isBlank()) {
            return alternative;
        }
        return isBetterLengthCandidate(alternative, primary, minTargetChars, preferredTargetChars, maxLength)
                ? alternative
                : primary;
    }

    private QuestionCategory resolveQuestionCategory(WorkspaceQuestion question) {
        if (question == null) {
            return QuestionCategory.DEFAULT;
        }
        // 유저가 직접 지정한 카테고리가 있으면 AI 분류 없이 즉시 반환
        if (question.getCategory() != null) {
            log.info("QuestionCategory: using user-set category={} for questionId={}", question.getCategory(), question.getId());
            return question.getCategory();
        }
        return questionClassifierService.classify(safeTrim(question.getTitle()));
    }

    private String buildApplicationResearchContext(WorkspaceQuestion question) {
        return buildApplicationResearchContext(question, QuestionCategory.DEFAULT);
    }

    private String buildApplicationResearchContext(WorkspaceQuestion question, QuestionCategory category) {
        List<String> sections = new ArrayList<>();

        sections.add(buildQuestionIntentContext(question, category));

        String companyLens = buildCompanyWritingLensContext(question, category);
        if (!companyLens.isBlank()) {
            sections.add(companyLens);
        }

        if (question.getApplication().getCompanyResearch() != null
                && !question.getApplication().getCompanyResearch().isBlank()) {
            sections.add("[Company Research]\n" + question.getApplication().getCompanyResearch());
        }

        if (question.getApplication().getAiInsight() != null && !question.getApplication().getAiInsight().isBlank()) {
            sections.add("[JD Insight]\n" + question.getApplication().getAiInsight());
        }

        if (question.getApplication().getRawJd() != null && !question.getApplication().getRawJd().isBlank()) {
            sections.add("[Raw JD]\n" + question.getApplication().getRawJd());
        }

        if (sections.isEmpty()) {
            return "No company-specific research context available.";
        }

        return String.join("\n---\n", sections);
    }

    private String buildQuestionIntentContext(WorkspaceQuestion question, QuestionCategory category) {
        String title = safeTrim(question.getTitle());
        QuestionCategory effectiveCategory = category != null ? category : QuestionCategory.DEFAULT;

        String primaryFocus;
        String weightingRule;

        switch (effectiveCategory) {
            case MOTIVATION -> {
                primaryFocus = "company-choice logic, role-fit proof, timing rationale, and realistic near-term contribution";
                weightingRule = "Use company and JD context as the primary rubric. Select only the past evidence that proves why this company and role are the logical next step now, and make the timing credible.";
            }
            case EXPERIENCE -> {
                primaryFocus = "specific scope, role ownership, technical judgment, verifiable action, measurable outcome, and JD fit";
                weightingRule = "Prioritize owned scope and technical evidence first. Use JD as a matching lens, and end with a grounded role connection rather than a generic promise or a broad project summary.";
            }
            case PROBLEM_SOLVING -> {
                primaryFocus = "problem definition, root-cause diagnosis, inaction risk, option selection, and reflective learning";
                weightingRule = "Prioritize diagnosis and decision logic first. Make the risk of not solving the problem visible, show why the chosen option beat alternatives under the actual constraint, then close with the changed approach and role relevance.";
            }
            case COLLABORATION -> {
                primaryFocus = "shared goal, role ownership, coordination process, friction handling when relevant, and team outcome";
                weightingRule = "Prioritize the common goal and the applicant's specific contribution first. Make team outcome and personal contribution distinct, and if there was disagreement or a blocker, explain how alignment was rebuilt instead of writing a generic teamwork slogan.";
            }
            case PERSONAL_GROWTH -> {
                primaryFocus = "formative episode, value or work principle formed, current behavior trace, and grounded role relevance";
                weightingRule = "Start from one representative experience or turning point, show what changed in the applicant's judgment or attitude, and then show how that principle appears in recent behavior. Metrics may support credibility, but do not let the answer turn into a technical achievement essay or a company-choice story.";
            }
            case CULTURE_FIT -> {
                primaryFocus = "value or trait in action, behavioral proof, team or customer impact, and culture fit";
                weightingRule = "Prioritize one bounded behavior episode first. Show how the value or working style appeared in a team, customer, or execution context, and close with why that pattern fits the company's culture and role. For weakness questions, make the improvement arc visible.";
            }
            case TREND_INSIGHT -> {
                primaryFocus = "issue selection, evidence-backed reasoning, company relevance, applicant viewpoint, and practical implication";
                weightingRule = "Start from one external issue with supplied evidence when available. Use company context as the primary frame, show one company-side application scene, and keep any personal anecdote short and credibility-building.";
            }
            default -> {
                primaryFocus = "the question's dominant competency, factual proof, and role relevance";
                weightingRule = "Infer the main evaluation intent from the question and use company context as a supporting rubric without forcing the answer into a generic template.";
            }
        }

        return """
                [Question Intent]
                Question: %s
                Intent type: %s
                Primary focus: %s
                Weighting rule: %s
                """.formatted(
                title.isBlank() ? "No question title provided." : title,
                effectiveCategory.name(),
                primaryFocus,
                weightingRule);
    }

    private String buildCompanyWritingLensContext(WorkspaceQuestion question, QuestionCategory category) {
        if (question == null || question.getApplication() == null) {
            return "";
        }

        String companyName = safeTrim(question.getApplication().getCompanyName()).toLowerCase();
        String lens = "GENERAL_TECH";
        String emphasis = "Favor evidence-first writing, concrete actions, and believable junior-level scope.";
        String avoid = "Avoid generic passion statements, stack name-dropping, and unsupported grand business impact.";

        if (containsAny(companyName, "토스", "비바리퍼블리카", "토스뱅크", "당근", "쿠팡", "toss", "daangn", "coupang")) {
            lens = "PRODUCT_EXECUTION";
            emphasis = "Emphasize customer inconvenience, fast iteration, MVP judgment, validation loops, and measurable movement in user or product signals.";
            avoid = "Avoid long abstract mission statements or overly academic explanations that never reach user impact.";
        } else if (containsAny(companyName, "네이버", "카카오", "라인", "라인플러스", "naver", "kakao", "line")) {
            lens = "PLATFORM_DEPTH";
            emphasis = "Emphasize technical depth, data flow, system structure, root-cause diagnosis, and careful engineering trade-offs.";
            avoid = "Avoid shallow stack lists, vague growth language, or purely emotional motivation without technical grounding.";
        } else if (containsAny(companyName, "삼성sds", "삼성 sds", "sk c&c", "sk cnc", "lgns", "lg cns")) {
            lens = "ENTERPRISE_TRANSFORMATION";
            emphasis = "Emphasize business context, reliability, cloud or AI adoption, operational stability, and a realistic long-term contribution arc.";
            avoid = "Avoid startup-style slogan writing or trend commentary that is disconnected from enterprise execution and customer trust.";
        }

        if (category == QuestionCategory.MOTIVATION) {
            emphasis += " For motivation questions, anchor the draft in one company-specific reason, one role-fit proof, and one timing signal. Do not let it drift into generic self-introduction, company praise, or autobiography.";
        } else if (category == QuestionCategory.EXPERIENCE) {
            emphasis += " For experience questions, state ownership, bounded scope, and measurable impact early. Show why the chosen method fit the problem, and end with a grounded JD connection rather than generic passion or abstract growth language.";
        } else if (category == QuestionCategory.PROBLEM_SOLVING) {
            emphasis += " For problem-solving questions, make the root cause, impact of inaction, and the chosen option's decision criteria explicit. Keep collaboration as supporting context and make the applicant's own diagnostic judgment visible.";
        } else if (category == QuestionCategory.COLLABORATION) {
            emphasis += " For collaboration questions, start from the shared goal, state the applicant's owned role early, and separate team outcome from individual contribution. Use one real coordination method or conflict-handling process instead of abstract teamwork praise.";
        } else if (category == QuestionCategory.PERSONAL_GROWTH) {
            emphasis += " For personal growth questions, focus on one formative episode, the value or work principle it formed, and one current behavior that proves it still operates today. Use technical outcomes only as supporting proof, and do not drift into company-choice motivation.";
        } else if (category == QuestionCategory.CULTURE_FIT) {
            emphasis += " For culture-fit questions, prove the value or working style with one bounded behavior episode instead of praising the company culture abstractly. Show team or customer impact, and for weakness questions make the recognition → improvement → re-application arc explicit.";
        } else if (category == QuestionCategory.TREND_INSIGHT) {
            emphasis += " For trend-insight questions, connect one external issue to a concrete product, customer, service, system, or operational scene in the company. Prefer evidence-backed reasoning and include one balanced condition, trade-off, or risk instead of writing a broad opinion essay.";
        }

        return """
                [Company Writing Lens]
                Archetype: %s
                Suggested emphasis: %s
                Avoid: %s
                """.formatted(lens, emphasis, avoid);
    }

    private boolean containsAny(String source, String... needles) {
        if (source == null || source.isBlank()) {
            return false;
        }

        for (String needle : needles) {
            if (needle != null && !needle.isBlank() && source.contains(needle.toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    private boolean containsAny(String source, List<String> needles) {
        if (needles == null || needles.isEmpty()) {
            return false;
        }
        return containsAny(source, needles.toArray(String[]::new));
    }

    private List<String> buildSupportingQueries(WorkspaceQuestion question, String primaryQuery) {
        if (question == null || question.getApplication() == null) {
            return List.of();
        }

        List<String> queries = new ArrayList<>();
        String title = safeTrim(question.getTitle());
        String company = safeTrim(question.getApplication().getCompanyName());
        String position = safeTrim(question.getApplication().getPosition());
        String userDirective = compactQueryText(question.getUserDirective(), 180);
        String batchStrategy = compactQueryText(question.getBatchStrategyDirective(), 220);
        String facetHints = compactQueryText(extractFacetHintsFromDirective(question.getBatchStrategyDirective()), 220);
        String jdInsight = compactQueryText(question.getApplication().getAiInsight(), 220);
        String companyResearch = compactQueryText(question.getApplication().getCompanyResearch(), 220);
        String rawJd = compactQueryText(question.getApplication().getRawJd(), 220);

        addSupportingQuery(queries, joinQueryParts(title, position));
        addSupportingQuery(queries, joinQueryParts(title, company, position));
        addSupportingQuery(queries, joinQueryParts(title, position, userDirective));
        addSupportingQuery(queries, joinQueryParts(title, position, batchStrategy));
        addSupportingQuery(queries, joinQueryParts(title, position, facetHints));
        addSupportingQuery(queries, joinQueryParts(title, position, userDirective, facetHints));
        addSupportingQuery(queries, joinQueryParts(title, position, jdInsight, companyResearch));
        addSupportingQuery(queries, joinQueryParts(title, position, rawJd));

        String normalizedPrimary = safeTrim(primaryQuery);
        return queries.stream()
                .filter(query -> !query.equalsIgnoreCase(normalizedPrimary))
                .limit(4)
                .toList();
    }

    private void addSupportingQuery(List<String> target, String query) {
        String normalized = safeTrim(query);
        if (normalized.isBlank()) {
            return;
        }

        boolean exists = target.stream().anyMatch(existing -> existing.equalsIgnoreCase(normalized));
        if (!exists) {
            target.add(normalized);
        }
    }

    private String joinQueryParts(String... parts) {
        return java.util.Arrays.stream(parts)
                .map(this::safeTrim)
                .filter(part -> !part.isBlank())
                .collect(Collectors.joining(" | "));
    }

    private String compactQueryText(String text, int maxLength) {
        String normalized = safeTrim(text)
                .replaceAll("\\s+", " ");
        if (normalized.length() <= maxLength) {
            return normalized;
        }
        return normalized.substring(0, maxLength).trim();
    }

    /**
     * 기존 호환용 오버로드 — 카테고리 없이 호출 시 DEFAULT로 처리합니다.
     */
    private String buildFilteredContext(WorkspaceQuestion initialQuestion, Long questionId,
            List<Experience> allExperiences) {
        return buildFilteredContext(initialQuestion, questionId, allExperiences, QuestionCategory.DEFAULT);
    }

    /**
     * 카테고리 인식 경험 컨텍스트 빌더.
     *
     * <p>판별된 {@link QuestionCategory}를 {@link ExperienceVectorRetrievalService}로 전달하여
     * 카테고리에 특화된 필드 가중치로 관련 경험을 검색합니다.
     */
    private String buildFilteredContext(WorkspaceQuestion initialQuestion, Long questionId,
            List<Experience> allExperiences, QuestionCategory category) {
        Set<Long> excludedExperienceIds = shouldRelaxExperienceExclusion(initialQuestion, category)
                ? Set.of()
                : removeDirectiveMentionedExperiences(
                        extractUsedExperienceIds(initialQuestion, questionId, allExperiences),
                        initialQuestion,
                        allExperiences);
        List<com.resumade.api.workspace.dto.ExperienceContextResponse.ContextItem> selectedContext = experienceVectorRetrievalService
                .search(
                        initialQuestion.getTitle(),
                        4,
                        excludedExperienceIds,
                        buildSupportingQueries(initialQuestion, initialQuestion.getTitle()),
                        category);   // 카테고리 전달 → 필드 부스팅 적용

        if (!selectedContext.isEmpty()) {
            return selectedContext.stream()
                    .map(item -> {
                        String header = (item.getFacetTitle() != null && !item.getFacetTitle().isBlank())
                                ? String.format("[Matched Experience: %s | Facet: %s]", item.getExperienceTitle(), item.getFacetTitle())
                                : String.format("[Matched Experience: %s]", item.getExperienceTitle());
                        return header + "\n" + item.getRelevantPart();
                    })
                    .collect(Collectors.joining("\n---\n"));
        }

        List<Experience> filteredExperiences = allExperiences.stream()
                .filter(exp -> !excludedExperienceIds.contains(exp.getId()))
                .toList();

        if (filteredExperiences.isEmpty()) {
            filteredExperiences = allExperiences;
        }

        return filteredExperiences.stream()
                .map(Experience::getRawContent)
                .collect(Collectors.joining("\n---\n"));
    }

    private Set<Long> extractUsedExperienceIds(WorkspaceQuestion initialQuestion, Long questionId,
            List<Experience> allExperiences) {
        // 다른 문항들에서 제목이 언급된 경험을 모두(findFirst 아닌 전체) 수집.
        // 한 문항에 여러 경험이 언급될 수 있어 flatMap으로 처리.
        return initialQuestion.getApplication().getQuestions().stream()
                .filter(q -> !q.getId().equals(questionId))
                .flatMap(q -> {
                    String searchableContent = String.join("\n",
                            q.getContent() == null ? "" : q.getContent(),
                            q.getWashedKr() == null ? "" : q.getWashedKr());
                    return allExperiences.stream()
                            .filter(exp -> exp.getTitle() != null && !exp.getTitle().isBlank()
                                    && searchableContent.contains(exp.getTitle()))
                            .map(Experience::getId);
                })
                .filter(id -> id != null)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private boolean shouldRelaxExperienceExclusion(WorkspaceQuestion question, QuestionCategory category) {
        if (category == QuestionCategory.TREND_INSIGHT) {
            return true;
        }
        return !extractFacetHintsFromDirective(question == null ? null : question.getBatchStrategyDirective()).isBlank();
    }

    /**
     * 유저 디렉티브에 이미 사용된 경험의 제목이 명시적으로 언급된 경우,
     * 해당 경험을 excluded 목록에서 제거하여 사용 허용.
     * 예: "A프로젝트로 주제 작성해줘" → A프로젝트 exclusion 해제
     */
    private Set<Long> removeDirectiveMentionedExperiences(
            Set<Long> excludedIds,
            WorkspaceQuestion question,
            List<Experience> allExperiences) {
        if (excludedIds.isEmpty()) {
            return excludedIds;
        }
        String directive = String.join("\n",
                safeTrim(question == null ? null : question.getBatchStrategyDirective()),
                safeTrim(question == null ? null : question.getUserDirective()));
        if (directive == null || directive.isBlank()) {
            return excludedIds;
        }

        Set<Long> unlocked = allExperiences.stream()
                .filter(exp -> exp.getTitle() != null && !exp.getTitle().isBlank()
                        && excludedIds.contains(exp.getId())
                        && directive.contains(exp.getTitle()))
                .map(Experience::getId)
                .collect(Collectors.toSet());

        if (unlocked.isEmpty()) {
            return excludedIds;
        }

        Set<Long> relaxed = new LinkedHashSet<>(excludedIds);
        relaxed.removeAll(unlocked);
        log.info("[RAG] Directive mentions {} previously-excluded experience(s) → unlocked for reuse", unlocked.size());
        return relaxed;
    }

    private String extractFacetHintsFromDirective(String directive) {
        if (directive == null || directive.isBlank()) {
            return "";
        }

        return directive.lines()
                .map(String::trim)
                .filter(line -> !line.isBlank())
                .filter(line -> line.toLowerCase().startsWith("use this specific facet"))
                .map(line -> {
                    int separatorIndex = line.indexOf(':');
                    return separatorIndex >= 0 ? line.substring(separatorIndex + 1).trim() : line;
                })
                .findFirst()
                .orElse("");
    }

    private String normalizeTitleSpacing(String text) {
        if (text == null) {
            return null;
        }

        String normalized = text.replaceAll("\\*\\*\\[", "[")
                .replaceAll("\\]\\*\\*", "]");

        if (normalized.contains("]") && !normalized.contains("]\n\n")) {
            normalized = normalized.replaceFirst("\\]\n", "]\n\n");
        }

        return normalized;
    }

    private String enforceLengthLimit(
            String text,
            int maxLength,
            int minTargetChars,
            int preferredTargetChars,
            String company,
            String position,
            String companyContext,
            String context,
            String others) {
        if (text == null) {
            return null;
        }

        String normalized = normalizeLengthText(normalizeTitleSpacing(text)).trim();
        logTraceLength("lengthLimit.input", normalized, maxLength, 0, 0);
        if (maxLength <= 0 || countResumeCharacters(normalized) <= maxLength) {
            return normalized;
        }

        int[] defaultRange = new int[] {
                Math.max(1, minTargetChars),
                Math.max(Math.max(1, minTargetChars), preferredTargetChars > 0 ? preferredTargetChars : maxLength)
        };
        logLengthMetrics("shorten", maxLength, defaultRange[0], defaultRange[1], normalized, 0);
        log.warn("{} SHORTEN #0 │ {}자 {} │ (목표:{}-{} / 상한:{}) │ → {}",
                resolveStageIcon("SHORTEN", "OVER_LIMIT"),
                countResumeCharacters(normalized), resolveStatusIndicator("OVER_LIMIT"),
                defaultRange[0], defaultRange[1], maxLength,
                toKoreanNextAction("SHORTEN_RETRY"));

        String safeCompany = safeTrim(company);
        String safePosition = safeTrim(position);
        String safeCompanyContext = safeTrim(companyContext);
        String safeContext = safeTrim(context);
        String safeOthers = safeTrim(others);

        try {
            WorkspaceDraftAiService.DraftResponse shortened = workspaceDraftAiService.shortenToLimit(
                    safeCompany,
                    safePosition,
                    safeCompanyContext,
                    normalized,
                    maxLength,
                    safeContext,
                    safeOthers);

            if (shortened == null || shortened.text == null || shortened.text.isBlank()) {
                log.warn("{} SHORTEN #1 │ {}자 {} │ (목표:{}-{} / 상한:{}) │ → {}",
                        resolveStageIcon("SHORTEN", "EMPTY_RESULT"),
                        countResumeCharacters(normalized), resolveStatusIndicator("EMPTY_RESULT"),
                        defaultRange[0], defaultRange[1], maxLength,
                        toKoreanNextAction("HARD_TRIM"));
                String trimmed = hardTrimToLimit(normalized, maxLength);
                logTraceLength("lengthLimit.hardTrimFromEmpty", trimmed, maxLength, defaultRange[0], defaultRange[1]);
                logLengthMetrics("shorten", maxLength, defaultRange[0], defaultRange[1], trimmed, 2);
                return trimmed;
            }

            String candidate = normalizeLengthText(normalizeTitleSpacing(shortened.text)).trim();
            logTraceLength("lengthLimit.aiCandidate", candidate, maxLength, defaultRange[0], defaultRange[1]);
            int candidateLength = countResumeCharacters(candidate);
            if (candidateLength <= maxLength && candidateLength >= defaultRange[0]) {
                logLengthMetrics("shorten", maxLength, defaultRange[0], defaultRange[1], candidate, 1);
                return candidate;
            }

            String retryStatus = candidateLength > maxLength ? "OVER_LIMIT" : "UNDER_MIN";
            log.warn("{} SHORTEN #1 │ {}자 {} │ (목표:{}-{} / 상한:{}) │ → {}",
                    resolveStageIcon("SHORTEN", retryStatus),
                    candidateLength, resolveStatusIndicator(retryStatus),
                    defaultRange[0], defaultRange[1], maxLength,
                    toKoreanNextAction("HARD_TRIM"));
            String trimSource = "UNDER_MIN".equals(retryStatus) ? normalized : candidate;
            String trimmed = hardTrimToLimit(trimSource, maxLength);
            logTraceLength("lengthLimit.hardTrimFromOverLimit", trimmed, maxLength, defaultRange[0], defaultRange[1]);
            logLengthMetrics("shorten", maxLength, defaultRange[0], defaultRange[1], trimmed, 2);
            return trimmed;
        } catch (Exception e) {
            log.warn("{} SHORTEN #1 │ {}자 {} │ (목표:{}-{} / 상한:{}) │ → {} [이유=줄이기 호출 실패]",
                    resolveStageIcon("SHORTEN", "ERROR"),
                    countResumeCharacters(normalized), resolveStatusIndicator("ERROR"),
                    defaultRange[0], defaultRange[1], maxLength,
                    toKoreanNextAction("HARD_TRIM"), e);
            String trimmed = hardTrimToLimit(normalized, maxLength);
            logTraceLength("lengthLimit.hardTrimFromError", trimmed, maxLength, defaultRange[0], defaultRange[1]);
            logLengthMetrics("shorten", maxLength, defaultRange[0], defaultRange[1], trimmed, 2);
            return trimmed;
        }
    }

    private String hardTrimToLimit(String text, int maxLength) {
        if (text == null || maxLength <= 0) {
            return text;
        }

        String normalized = normalizeLengthText(text);
        if (countResumeCharacters(normalized) <= maxLength) {
            return normalized;
        }

        String trimmed = substringByCharacterLimit(normalized, maxLength).trim();
        int breakpoint = Math.max(
                Math.max(trimmed.lastIndexOf("\n"), trimmed.lastIndexOf(".")),
                Math.max(trimmed.lastIndexOf("!"), trimmed.lastIndexOf("?")));

        if (breakpoint >= Math.max(0, maxLength - 80)) {
            return trimmed.substring(0, breakpoint + 1).trim();
        }

        int softBreakpoint = Math.max(trimmed.lastIndexOf(","), trimmed.lastIndexOf(" "));
        if (softBreakpoint >= Math.max(0, maxLength - 40)) {
            return appendEllipsisWithinLimit(trimmed.substring(0, softBreakpoint).trim(), maxLength);
        }

        return appendEllipsisWithinLimit(trimmed, maxLength);
    }

    private String appendEllipsisWithinLimit(String text, int maxLength) {
        if (text == null || text.isBlank()) {
            return text;
        }
        if (text.endsWith(".") || text.endsWith("!") || text.endsWith("?")) {
            return text;
        }

        String base = text;
        if (maxLength > 3 && countResumeCharacters(base) > maxLength - 3) {
            base = substringByCharacterLimit(base, maxLength - 3).trim();
        }

        if (base.isBlank()) {
            return text;
        }

        return base + "...";
    }

    private String prepareDraftForTranslation(
            String text,
            int maxLength,
            int minTargetChars,
            int preferredTargetChars,
            String company,
            String position,
            String companyContext,
            String context,
            String others) {
        if (text == null) {
            return null;
        }

        String normalized = normalizeLengthText(normalizeTitleSpacing(text)).trim();
        logTraceLength("prepareDraftForTranslation.input", normalized, maxLength, minTargetChars, preferredTargetChars);
        if (maxLength > 0 && countResumeCharacters(normalized) > maxLength) {
            String shortened = enforceLengthLimit(
                    normalized,
                    maxLength,
                    minTargetChars,
                    preferredTargetChars,
                    company,
                    position,
                    companyContext,
                    context,
                    others);
            logTraceLength("prepareDraftForTranslation.output", shortened, maxLength, minTargetChars, preferredTargetChars);
            return shortened;
        }

        logTraceLength("prepareDraftForTranslation.output", normalized, maxLength, minTargetChars, preferredTargetChars);
        return normalized;
    }

    private String prepareWashedDraft(String text) {
        if (text == null) {
            return null;
        }
        return sanitizeWashedDraftText(text);
    }

    private String normalizeLengthText(String text) {
        if (text == null) {
            return null;
        }
        return text.replace("\r\n", "\n").replace('\r', '\n');
    }

    private int countResumeCharacters(String text) {
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

    private void logLengthMetrics(
            String stage,
            int hardLimit,
            int minimumTarget,
            int preferredTarget,
            String text,
            int retryCount) {
        int actualChars = countResumeCharacters(text);
        String status = resolvePipelineLengthStatus(actualChars, minimumTarget, preferredTarget, hardLimit);
        String attempt = retryCount > 0 ? String.valueOf(retryCount) : "-";
        String next = resolvePipelineNextAction(stage, status);
        String normalizedStage = stage == null ? "" : stage.trim().toUpperCase();
        String icon = resolveStageIcon(normalizedStage, status);
        String indicator = resolveStatusIndicator(status);
        String message = "{} {} #{} │ {}자 {} │ (목표:{}-{} / 상한:{}) │ → {}";
        Object[] args = {icon, normalizedStage, attempt, actualChars, indicator,
                minimumTarget, preferredTarget, hardLimit, toKoreanNextAction(next)};
        boolean isWarn = switch (status) {
            case "OVER_LIMIT", "UNDER_MIN", "ERROR", "FAILED", "FAMILY_FAILED", "EMPTY_RESULT" -> true;
            default -> false;
        };
        if (isWarn) {
            log.warn(message, args);
        } else {
            log.info(message, args);
        }
    }

    private String resolveLengthStatus(int length, int minTarget, int preferredTarget, int maxLength) {
        if (maxLength > 0 && length > maxLength) return "↑초과";
        if (minTarget > 0 && length < minTarget)  return "↓";
        if (preferredTarget > 0 && length <= preferredTarget) return "✓";
        return "~";
    }

    private String resolvePipelineLengthStatus(int actualChars, int minimumTarget, int preferredTarget, int hardLimit) {
        if (hardLimit > 0 && actualChars > hardLimit) {
            return "OVER_LIMIT";
        }
        if (minimumTarget > 0 && actualChars < minimumTarget) {
            return "UNDER_MIN";
        }
        if (preferredTarget > 0 && actualChars <= preferredTarget) {
            return "IN_RANGE";
        }
        return "ABOVE_PREFERRED";
    }

    private String resolvePipelineNextAction(String stage, String status) {
        String normalizedStage = stage == null ? "" : stage.trim().toUpperCase();
        return switch (normalizedStage) {
            case "GENERATE", "REFINE" -> "EXPAND_OR_CONTINUE";
            case "EXPAND" -> "CHECK_TARGET";
            case "REGENERATE" -> "CHECK_TARGET";
            case "SHORTEN" -> "CHECK_LIMIT";
            case "WASH" -> "CHECK_LIMIT";
            case "FINAL" -> "COMPLETE";
            default -> "CONTINUE";
        };
    }

    private String toKoreanStage(String stage) {
        return switch (stage) {
            case "GENERATE" -> "초안 생성";
            case "REFINE" -> "초안 수정";
            case "EXPAND" -> "초안 확장";
            case "REGENERATE" -> "재생성";
            case "SHORTEN" -> "글자수 단축";
            case "WASH" -> "세탁 완료";
            case "FINAL" -> "최종 완료";
            case "DRAFT" -> "초안 파이프라인";
            default -> stage;
        };
    }

    private String toKoreanStatus(String status) {
        return switch (status) {
            case "UNDER_MIN" -> "최소 글자수 미달";
            case "IN_RANGE" -> "목표 구간 충족";
            case "ABOVE_PREFERRED" -> "선호 초과 상태";
            case "OVER_LIMIT" -> "최대 글자수 초과";
            case "FAILED" -> "실패";
            case "FAMILY_FAILED" -> "계열 실패";
            case "ERROR" -> "오류";
            case "START" -> "시작";
            case "EMPTY_RESULT" -> "빈 결과 반환";
            default -> status;
        };
    }

    private String toKoreanNextAction(String next) {
        return switch (next) {
            case "EXPAND_OR_CONTINUE" -> "확장 또는 계속";
            case "CHECK_TARGET" -> "목표 구간 확인";
            case "CHECK_LIMIT" -> "제한 초과 확인";
            case "COMPLETE" -> "워크스페이스로 반환";
            case "ABORT" -> "처리 강제 중단";
            case "EXPAND_RETRY" -> "글자수 부족으로 재시도";
            case "NEW_FAMILY" -> "새 초안군 생성";
            case "GENERATE_FRESH_FAMILY" -> "새 초안으로 재생성";
            case "KEEP_PREVIOUS_BEST" -> "이전 최고안 유지";
            case "SHORTEN_RETRY" -> "단축 재시도";
            case "HARD_TRIM" -> "강제 단축";
            case "CONTINUE" -> "계속 진행";
            default -> next;
        };
    }

    private String resolveStageIcon(String stage, String status) {
        if ("ERROR".equals(status) || "FAILED".equals(status) || "FAMILY_FAILED".equals(status)) {
            return "[ERROR]";
        }
        if ("UNDER_MIN".equals(status) || "OVER_LIMIT".equals(status) || "EMPTY_RESULT".equals(status)) {
            return "[WARN]";
        }
        if ("IN_RANGE".equals(status)) {
            return "[OK]";
        }
        if ("START".equals(status)) {
            return "[RUN]";
        }
        return switch (stage) {
            case "GENERATE", "REFINE", "EXPAND", "DRAFT" -> "[DRAFT]";
            case "REGENERATE" -> "[RETRY]";
            case "SHORTEN" -> "[TRIM]";
            case "WASH" -> "[WASH]";
            case "FINAL" -> "[DONE]";
            default -> "[STEP]";
        };
    }

    private String resolveStatusIndicator(String status) {
        return switch (status) {
            case "IN_RANGE"        -> "✓";
            case "ABOVE_PREFERRED" -> "~";
            case "OVER_LIMIT"      -> "↑ 초과";
            case "UNDER_MIN"       -> "↓ 미달";
            case "START"           -> "▶";
            case "ERROR"           -> "✗ 오류";
            case "FAILED"          -> "✗ 실패";
            case "FAMILY_FAILED"   -> "✗ 계열실패";
            case "EMPTY_RESULT"    -> "✗ 빈결과";
            default                -> status;
        };
    }

    private String substringByCharacterLimit(String text, int maxLength) {
        if (text == null) {
            return null;
        }

        String normalized = normalizeLengthText(text);
        if (maxLength <= 0 || countResumeCharacters(normalized) <= maxLength) {
            return normalized;
        }

        int endIndex = normalized.offsetByCodePoints(0, maxLength);
        return normalized.substring(0, endIndex);
    }

    // ── 문장 단위 병렬 Human Patch 분석 ─────────────────────────────────────────

    /**
     * 원본 초안/세탁본을 문장 단위로 쪼개 LLM에 병렬 전송하고 결과를 합산.
     * 전체 실패 시 빈 결과 반환(fallback).
     */
    private DraftAnalysisResult analyzeBySentencePairsWithFallback(SseEmitter emitter,
                                                                    String originalDraft,
                                                                    String washedKr) {
        try {
            return analyzeBySentencePairs(originalDraft, washedKr);
        } catch (Exception e) {
            log.warn("[HumanPatch] Sentence-pair analysis failed globally: {}", e.getMessage(), e);
            sendProgress(emitter, STAGE_PATCH, "휴먼 패치 분석 응답이 불안정해 세탁본만 반환합니다. ⚠️");
            return DraftAnalysisResult.builder()
                    .mistranslations(new ArrayList<>())
                    .humanPatchedText(washedKr)
                    .aiReviewReport(null)
                    .build();
        }
    }

    /**
     * 전체 원본+세탁본을 LLM 한 번 호출로 분석.
     * 이전의 문장 인덱스 페어링 방식은 번역 세탁 시 문장 수/구조가 변해 인덱스가 어긋나는
     * 근본적 결함이 있었음. 전체 텍스트를 한 번에 주면 LLM이 맥락 전체를 보고 정확히 판단함.
     */
    private DraftAnalysisResult analyzeBySentencePairs(String originalDraft, String washedKr) {
        log.info("[HumanPatch] Full-text single-call analysis starting");

        SentencePairAnalysisResult result = workspacePatchAiService.analyzeSentencePair(
                sanitizeOpenAiPromptText(originalDraft),
                sanitizeOpenAiPromptText(washedKr)
        );

        if (result == null || result.getMistranslations() == null || result.getMistranslations().isEmpty()) {
            log.info("[HumanPatch] No mistranslations detected");
            return DraftAnalysisResult.builder()
                    .mistranslations(new ArrayList<>())
                    .humanPatchedText(washedKr)
                    .aiReviewReport(null)
                    .build();
        }

        AtomicInteger idCounter = new AtomicInteger(1);
        List<DraftAnalysisResult.Mistranslation> mistranslations = result.getMistranslations().stream()
                .filter(m -> m.getTranslated() != null && !m.getTranslated().isBlank())
                // translated 단어가 세탁본에 실제로 존재해야 함 (없으면 LLM 환각)
                .filter(m -> washedKr.contains(safeTrim(m.getTranslated())))
                // original 단어가 세탁본에 그대로 남아있으면 변경되지 않은 것 → 오탐 제거
                // (예: 트랜잭션이 세탁본에도 존재하면 트랜잭션→X 오탐 방지)
                .filter(m -> m.getOriginal() == null || !washedKr.contains(safeTrim(m.getOriginal())))
                .map(m -> DraftAnalysisResult.Mistranslation.builder()
                        .id("mis-" + idCounter.getAndIncrement())
                        .original(safeTrim(m.getOriginal()))
                        .translated(safeTrim(m.getTranslated()))
                        .issueType(m.getIssueType())
                        .reason(safeTrim(m.getReason()))
                        .suggestion(normalizeTitleSpacing(safeTrim(m.getSuggestion())))
                        .originalSentence(findContainingSentence(originalDraft, safeTrim(m.getOriginal())))
                        .translatedSentence(findContainingSentence(washedKr, safeTrim(m.getTranslated())))
                        .severity(resolveSeverity(m.getIssueType()))
                        .build())
                .collect(Collectors.toList());

        log.info("[HumanPatch] Detected {} mistranslations from full-text analysis", mistranslations.size());

        return DraftAnalysisResult.builder()
                .mistranslations(mistranslations)
                .humanPatchedText(washedKr)
                .aiReviewReport(null)
                .build();
    }

    /**
     * phrase를 포함하는 문장을 text에서 찾아 반환.
     * 마침표(+ 공백 or 줄바꿈) 기준으로 분리하되, 못 찾으면 phrase 자체를 fallback으로 반환.
     */
    private String findContainingSentence(String text, String phrase) {
        if (text == null || phrase == null || phrase.isBlank()) return "";
        // 마침표 뒤 공백/줄바꿈으로 문장 분리
        String[] sentences = text.split("\\.(?=\\s|$)");
        for (String sentence : sentences) {
            if (sentence.contains(phrase)) return sentence.trim();
        }
        // fallback: 단순 마침표로 재시도
        String[] sentences2 = text.split("\\.");
        for (String sentence : sentences2) {
            if (sentence.contains(phrase)) return sentence.trim();
        }
        return phrase;
    }

    private String resolveSeverity(String issueType) {
        if (issueType == null) return "CRITICAL";
        return switch (issueType) {
            case "TERM_WEAKENED", "PROPER_NOUN_CHANGED", "METRIC_DROPPED" -> "CRITICAL";
            default -> "WARNING";
        };
    }

    // ── (legacy) 전문 분석 — 현재 미사용, 추후 필요 시 참고용 보존 ──────────────

    private DraftAnalysisResult analyzePatchSafely(
            SseEmitter emitter,
            String originalDraft,
            String washedKr,
            int maxLength,
            int findingTarget,
            String context) {
        try {
            return workspacePatchAiService.analyzePatch(
                    sanitizeOpenAiPromptText(originalDraft),
                    sanitizeOpenAiPromptText(washedKr),
                    maxLength,
                    (int) (maxLength * 0.92),
                    findingTarget,
                    sanitizeOpenAiPromptText(context));
        } catch (Exception e) {
            if (isQuotaError(e) || isPatchAnalysisRequestError(e) || isTimeoutError(e)) {
                log.warn("Patch analysis skipped due to upstream OpenAI issue", e);
                sendProgress(emitter, STAGE_PATCH, "휴먼 패치 분석 응답이 불안정해 세탁본만 반환합니다. ⚠️");

                return DraftAnalysisResult.builder()
                        .humanPatchedText(washedKr)
                        .mistranslations(new ArrayList<>())
                        .aiReviewReport(DraftAnalysisResult.AiReviewReport.builder()
                                .summary("Patch analysis response was unstable, so this run returned the washed draft only.")
                                .build())
                        .build();
            }

            throw e;
        }
    }

    private String sanitizeOpenAiPromptText(String text) {
        if (text == null || text.isBlank()) {
            return text;
        }

        StringBuilder sanitized = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char current = text.charAt(i);

            if (Character.isHighSurrogate(current)) {
                if (i + 1 < text.length() && Character.isLowSurrogate(text.charAt(i + 1))) {
                    sanitized.append(current).append(text.charAt(i + 1));
                    i++;
                }
                continue;
            }

            if (Character.isLowSurrogate(current)) {
                continue;
            }

            if (Character.isISOControl(current) && current != '\n' && current != '\r' && current != '\t') {
                continue;
            }

            sanitized.append(current);
        }

        return sanitized.toString();
    }

    private boolean isQuotaError(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            String message = current.getMessage();
            if (message != null) {
                String lower = message.toLowerCase();
                if (lower.contains("insufficient_quota")
                        || lower.contains("exceeded your current quota")
                        || lower.contains("billing details")) {
                    return true;
                }
            }
            current = current.getCause();
        }
        return false;
    }

    private boolean isTimeoutError(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof java.io.InterruptedIOException
                    || current instanceof java.net.SocketTimeoutException) {
                return true;
            }

            String message = current.getMessage();
            if (message != null) {
                String lower = message.toLowerCase();
                if (lower.contains("timeout") || lower.contains("timed out")) {
                    return true;
                }
            }

            current = current.getCause();
        }
        return false;
    }

    private boolean isPatchAnalysisRequestError(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            String message = current.getMessage();
            if (message != null) {
                String lower = message.toLowerCase();
                if (lower.contains("could not parse the json body of your request")
                        || lower.contains("not valid json")) {
                    return true;
                }
            }
            current = current.getCause();
        }
        return false;
    }

    /**
     * LLM이 반환한 인덱스를 1순위로 신뢰하고, 실패 시 regex로 fallback.
     *
     * @param originalDraft AI 초안 텍스트 (original 측 인덱스 검증용)
     * @param washedKr      세탁본 텍스트 (translated 측 인덱스 검증용)
     */
    private void normalizeAnalysis(DraftAnalysisResult analysis, String originalDraft, String washedKr) {
        if (analysis == null) {
            return;
        }

        analysis.setHumanPatchedText(sanitizeWashedDraftText(analysis.getHumanPatchedText()));

        List<DraftAnalysisResult.Mistranslation> mistranslations = analysis.getMistranslations();
        if (mistranslations == null) {
            mistranslations = new ArrayList<>();
        }

        List<DraftAnalysisResult.Mistranslation> normalized = new ArrayList<>();
        for (DraftAnalysisResult.Mistranslation mis : mistranslations) {
            String translated = safeTrim(mis.getTranslated()).replaceAll("\\s+", " ");
            if (translated.isEmpty()) {
                continue;
            }
            mis.setTranslated(translated);
            mis.setSuggestion(normalizeTitleSpacing(safeTrim(mis.getSuggestion())));
            mis.setReason(safeTrim(mis.getReason()));
            normalized.add(mis);
        }

        analysis.setMistranslations(normalized);
    }

    private int calculateFindingTarget(String washedKr) {
        if (washedKr == null || washedKr.isBlank()) {
            return 5;
        }
        // 200자당 최소 1개 finding, 글자수가 많을수록 더 많이 요청 (제한 15)
        return Math.max(5, Math.min(15, (washedKr.length() / 200) + 3));
    }

    private String safeTrim(String value) {
        return value == null ? "" : value.trim();
    }

    private String sanitizeWashedDraftText(String text) {
        if (text == null) {
            return null;
        }

        String normalized = normalizeLengthText(normalizeTitleSpacing(text)).trim();
        if (normalized.isBlank()) {
            return normalized;
        }

        Matcher duplicateTitleBlockMatcher = REDUNDANT_TITLE_BLOCK_PATTERN.matcher(normalized);
        if (duplicateTitleBlockMatcher.find()) {
            String leadingContent = normalized.substring(0, duplicateTitleBlockMatcher.start()).trim();
            if (countResumeCharacters(leadingContent) >= REDUNDANT_TITLE_BLOCK_MIN_PREFIX_CHARS) {
                log.warn("[TRIM] 워크스페이스 | 단계=세탁 정제 | 상태=제목 블록 제거 | 초안군=- | 시도=- | 글자수={}자→{}자 | 목표=- | 제한=- | 다음=길이 재검증",
                        countResumeCharacters(normalized),
                        countResumeCharacters(leadingContent));
                return leadingContent;
            }
        }

        return normalized;
    }

    private void logTraceLength(
            String label,
            String text,
            int hardLimit,
            int minimumTarget,
            int preferredTarget) {
        if (!log.isDebugEnabled()) {
            return;
        }

        int actualChars = countResumeCharacters(text);
        String status = resolvePipelineLengthStatus(actualChars, minimumTarget, preferredTarget, hardLimit);
        log.debug(
                "TRACE_LENGTH label={} chars={} hardLimit={} minTarget={} preferredTarget={} status={} snippet={}",
                label,
                actualChars,
                hardLimit,
                minimumTarget,
                preferredTarget,
                status,
                safeSnippet(normalizeLengthText(text), 220));
    }

    private void paceProcessing() {
        // No-op: speed is preferred over staged progress pacing.
    }

    private void sendStage(SseEmitter emitter, String stage) {
        sendSse(emitter, "stage", stage);
    }

    private void sendProgress(SseEmitter emitter, String stage, String message) {
        sendStage(emitter, stage);
        sendSse(emitter, "progress", message);
    }

    private void sendSse(SseEmitter emitter, String name, Object data) {
        try {
            log.debug("SSE send: name={}, payloadType={}", name,
                    data == null ? "null" : data.getClass().getSimpleName());
            if (data instanceof String text) {
                emitter.send(Utf8SseSupport.textEvent(name, text));
            } else {
                emitter.send(Utf8SseSupport.jsonEvent(name, data));
            }
        } catch (IOException | IllegalStateException e) {
            log.warn("Failed to send SSE event: {}", name);
            throw new SseConnectionClosedException(e);
        }
    }

    private void sendComment(SseEmitter emitter, String comment) {
        try {
            emitter.send(SseEmitter.event().comment(comment));
        } catch (IOException | IllegalStateException e) {
            log.warn("Failed to send SSE comment");
            throw new SseConnectionClosedException(e);
        }
    }

    private void completeQuietly(SseEmitter emitter) {
        try {
            emitter.complete();
        } catch (Exception ignored) {
            // ignore
        }
    }

    private record HeartbeatHandle(ScheduledExecutorService scheduler, ScheduledFuture<?> future) {
        private void stop() {
            future.cancel(true);
            scheduler.shutdownNow();
        }
    }

    private String buildContext(WorkspaceQuestion question, Long questionId, List<Experience> allExperiences, QuestionCategory category, List<Long> storyIds) {
        if (storyIds != null && !storyIds.isEmpty()) {
            return buildStoryContext(storyIds);
        }
        // PERSONAL_GROWTH 문항은 storyIds 미선택 시 전체 personal story를 자동 로드
        if (category == QuestionCategory.PERSONAL_GROWTH) {
            List<Long> allStoryIds = personalStoryRepository.findAll()
                    .stream().map(com.resumade.api.experience.domain.PersonalStory::getId).toList();
            if (!allStoryIds.isEmpty()) {
                log.info("buildContext: PERSONAL_GROWTH auto-loading all {} personal stories", allStoryIds.size());
                return buildStoryContext(allStoryIds);
            }
        }
        return buildFilteredContext(question, questionId, allExperiences, category);
    }

    private String buildStoryContext(List<Long> storyIds) {
        List<com.resumade.api.experience.domain.PersonalStory> stories = personalStoryRepository.findAllById(storyIds)
                .stream()
                .filter(s -> s.getType() != com.resumade.api.experience.domain.PersonalStory.StoryType.WRITING_GUIDE)
                .toList();
        if (stories.isEmpty()) {
            return "No personal stories selected.";
        }

        StringBuilder sb = new StringBuilder("### SELECTED PERSONAL STORIES ###\n\n");
        for (var story : stories) {
            sb.append("[").append(story.getType().name()).append("] ").append(story.getPeriod()).append("\n");
            sb.append("Content: ").append(story.getContent()).append("\n");
            if (story.getKeywords() != null && !story.getKeywords().isEmpty()) {
                sb.append("Keywords: ").append(String.join(", ", story.getKeywords())).append("\n");
            }
            sb.append("---\n");
        }
        return sb.toString();
    }

    private String buildWritingGuideContext(List<Long> storyIds, QuestionCategory category) {
        List<com.resumade.api.experience.domain.PersonalStory> source;
        if (storyIds == null || storyIds.isEmpty()) {
            // PERSONAL_GROWTH일 때만 전체 서사에서 WRITING_GUIDE를 자동 탐색
            if (category != QuestionCategory.PERSONAL_GROWTH) return null;
            source = personalStoryRepository.findAll();
        } else {
            source = personalStoryRepository.findAllById(storyIds);
        }
        List<com.resumade.api.experience.domain.PersonalStory> guides = source.stream()
                .filter(s -> s.getType() == com.resumade.api.experience.domain.PersonalStory.StoryType.WRITING_GUIDE)
                .toList();
        if (guides.isEmpty()) return null;

        StringBuilder sb = new StringBuilder();
        for (var guide : guides) {
            sb.append(guide.getContent()).append("\n\n");
        }
        return sb.toString().trim();
    }

    private record RequestedLengthDirective(int minimum, int preferredTarget) {
    }
}

