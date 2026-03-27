package com.resumade.api.workspace.service;

import com.resumade.api.experience.service.ExperienceVectorRetrievalService;
import com.resumade.api.workspace.domain.Application;
import com.resumade.api.workspace.domain.SnapshotType;
import com.resumade.api.workspace.domain.WorkspaceQuestion;
import com.resumade.api.workspace.domain.WorkspaceQuestionRepository;
import com.resumade.api.workspace.dto.EditActionRequest;
import com.resumade.api.workspace.dto.EditActionResponse;
import com.resumade.api.workspace.dto.ExperienceContextResponse;
import com.resumade.api.workspace.dto.FinalEditorResponse;
import com.resumade.api.workspace.dto.FinalSaveRequest;
import com.resumade.api.workspace.dto.FinalSaveResponse;
import com.resumade.api.workspace.dto.QuestionNavItem;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class FinalEditorService {

    /** RAG 컨텍스트를 활용하는 액션 목록 */
    private static final Set<String> RAG_ACTIONS = Set.of("tech", "metrics", "specific", "confident", "closing");

    private final WorkspaceQuestionRepository questionRepository;
    private final FinalEditorAiService finalEditorAiService;
    private final ExperienceVectorRetrievalService experienceVectorRetrievalService;
    private final TranslationService translationService;
    private final QuestionSnapshotService questionSnapshotService;

    // ── 최종 편집기 초기 데이터 로드 ─────────────────────────────────────────

    @Transactional(readOnly = true)
    public FinalEditorResponse getFinalEditorData(Long questionId) {
        WorkspaceQuestion q = findQuestion(questionId);

        String companyName    = q.getApplication() != null ? q.getApplication().getCompanyName() : null;
        String position       = q.getApplication() != null ? q.getApplication().getPosition()    : null;
        Long   applicationId  = q.getApplication() != null ? q.getApplication().getId()          : null;

        // finalText가 없으면 washedKr을 초기값으로 제공
        String finalText = q.getFinalText() != null ? q.getFinalText() : q.getWashedKr();

        return new FinalEditorResponse(
                q.getId(),
                q.getTitle(),
                q.getMaxLength(),
                q.getContent(),
                q.getWashedKr(),
                finalText,
                q.getSelectedTitle(),
                companyName,
                position,
                applicationId,
                q.getUpdatedAt()
        );
    }

    // ── 자동저장 ─────────────────────────────────────────────────────────────

    @Transactional
    public FinalSaveResponse saveFinalText(Long questionId, FinalSaveRequest request) {
        WorkspaceQuestion q = findQuestion(questionId);

        if (request.finalText() != null) {
            q.setFinalText(request.finalText());
        }
        if (request.selectedTitle() != null) {
            q.setSelectedTitle(request.selectedTitle());
        }

        WorkspaceQuestion saved = questionRepository.save(q);
        log.debug("Final text saved: questionId={}, chars={}",
                questionId,
                request.finalText() != null ? request.finalText().length() : 0);

        if (request.finalText() != null && !request.finalText().isBlank()) {
            questionSnapshotService.saveSnapshot(questionId, SnapshotType.FINAL_EDIT, request.finalText());
        }

        return new FinalSaveResponse(saved.getUpdatedAt());
    }

    // ── AI 편집 액션 (RAG 컨텍스트 주입) ─────────────────────────────────────

    @Transactional(readOnly = true)
    public EditActionResponse applyEditAction(Long questionId, EditActionRequest request) {
        WorkspaceQuestion q = findQuestion(questionId);
        Application app = q.getApplication();

        try {
            String result;

            // custom: 사용자 지정 프롬프트
            if ("custom".equals(request.actionKey()) && request.customPrompt() != null) {
                String prompt = buildCustomPrompt(q, app, request);
                result = finalEditorAiService.editWithCustomPrompt(prompt);
                log.debug("Custom action applied: questionId={}", questionId);

            // 표준 액션
            } else {
                String prompt = buildEnrichedPrompt(q, app, request);
                result = finalEditorAiService.editText(prompt);
                log.debug("Edit action applied: questionId={}, action={}", questionId, request.actionKey());
            }

            String transformed = result != null ? result.strip() : request.selectedText();
            return new EditActionResponse(transformed);

        } catch (Exception e) {
            log.error("Edit action failed: questionId={}, action={}", questionId, request.actionKey(), e);
            return new EditActionResponse(request.selectedText());
        }
    }

    /**
     * 사용자 지정 프롬프트를 위한 빌더 — JD 인사이트 + 문항 + RAG + 사용자 지시 조합
     */
    private String buildCustomPrompt(WorkspaceQuestion q, Application app, EditActionRequest request) {
        StringBuilder sb = new StringBuilder();

        if (app != null && app.getAiInsight() != null && !app.getAiInsight().isBlank()) {
            sb.append("[지원 공고 분석]\n").append(app.getAiInsight().strip()).append("\n\n");
        }
        if (q.getTitle() != null && !q.getTitle().isBlank()) {
            sb.append("[문항]\n").append(q.getTitle().strip()).append("\n\n");
        }

        // RAG 경험 팩트
        try {
            String searchQuery = request.selectedText() + " " + (q.getTitle() != null ? q.getTitle() : "");
            List<ExperienceContextResponse.ContextItem> experiences =
                    experienceVectorRetrievalService.search(searchQuery.strip(), 3);
            if (!experiences.isEmpty()) {
                sb.append("[관련 경험 팩트]\n");
                for (ExperienceContextResponse.ContextItem exp : experiences) {
                    sb.append("• 경험: ").append(exp.getExperienceTitle()).append("\n");
                    if (exp.getRelevantPart() != null && !exp.getRelevantPart().isBlank()) {
                        sb.append("  내용: ").append(exp.getRelevantPart().strip()).append("\n");
                    }
                    sb.append("\n");
                }
            }
        } catch (Exception e) {
            log.warn("RAG retrieval failed for custom action: questionId={}", q.getId());
        }

        sb.append("[입력 길이]\n").append(request.selectedText().length()).append("자\n\n");
        sb.append("[사용자 지시]\n").append(request.customPrompt().strip()).append("\n\n");
        sb.append("[편집할 텍스트]\n").append(request.selectedText());

        return sb.toString();
    }

    /**
     * RAG 경험 팩트 + JD 인사이트 + 문항 정보를 조합한 AI 프롬프트를 빌드합니다.
     */
    private String buildEnrichedPrompt(WorkspaceQuestion q, Application app, EditActionRequest request) {
        StringBuilder sb = new StringBuilder();

        sb.append("action: ").append(request.actionKey()).append("\n\n");

        // ── 1. 지원 공고 분석 (JD insight) ────────────────────────────────
        if (app != null && app.getAiInsight() != null && !app.getAiInsight().isBlank()) {
            sb.append("[지원 공고 분석]\n")
              .append(app.getAiInsight().strip())
              .append("\n\n");
        }

        // ── 2. 문항 정보 ─────────────────────────────────────────────────
        if (q.getTitle() != null && !q.getTitle().isBlank()) {
            sb.append("[문항]\n")
              .append(q.getTitle().strip())
              .append("\n\n");
        }

        // ── 3. 관련 경험 팩트 (RAG - 선택적 액션에만 적용) ────────────────
        if (RAG_ACTIONS.contains(request.actionKey())) {
            try {
                // 선택 텍스트 + 문항 제목을 검색 쿼리로 조합하여 최대 3개의 관련 경험을 검색
                String searchQuery = request.selectedText() + " " + (q.getTitle() != null ? q.getTitle() : "");
                List<ExperienceContextResponse.ContextItem> experiences =
                        experienceVectorRetrievalService.search(searchQuery.strip(), 3);

                if (!experiences.isEmpty()) {
                    sb.append("[관련 경험 팩트]\n");
                    for (ExperienceContextResponse.ContextItem exp : experiences) {
                        sb.append("• 경험: ").append(exp.getExperienceTitle()).append("\n");
                        if (exp.getRelevantPart() != null && !exp.getRelevantPart().isBlank()) {
                            sb.append("  내용: ").append(exp.getRelevantPart().strip()).append("\n");
                        }
                        sb.append("\n");
                    }
                    log.debug("RAG context injected: questionId={}, action={}, experiences={}",
                            q.getId(), request.actionKey(), experiences.size());
                }
            } catch (Exception e) {
                // RAG 실패 시 컨텍스트 없이 진행 (graceful degradation)
                log.warn("RAG retrieval failed for questionId={}, action={}. Proceeding without experience context.",
                        q.getId(), request.actionKey());
            }
        }

        // ── 4. 입력 길이 (AI 길이 제약 기준) ────────────────────────────
        sb.append("[입력 길이]\n")
          .append(request.selectedText().length())
          .append("자\n\n");

        // ── 5. 편집할 텍스트 ─────────────────────────────────────────────
        sb.append("[편집할 텍스트]\n")
          .append(request.selectedText());

        return sb.toString();
    }

    // ── 문항 네비게이터 목록 ──────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<QuestionNavItem> getSiblingQuestions(Long questionId) {
        WorkspaceQuestion current = findQuestion(questionId);
        if (current.getApplication() == null) {
            // Application이 없는 독립 문항이면 자기 자신만 반환
            return List.of(new QuestionNavItem(
                    current.getId(), 1, current.getTitle(),
                    current.getWashedKr() != null && !current.getWashedKr().isBlank()
            ));
        }

        Long applicationId = current.getApplication().getId();
        List<WorkspaceQuestion> siblings = questionRepository.findByApplicationId(applicationId);

        // DB 삽입 순서(id ASC) 기준으로 정렬 후 1-based index 부여
        siblings.sort(java.util.Comparator.comparingLong(WorkspaceQuestion::getId));

        List<QuestionNavItem> result = new java.util.ArrayList<>();
        for (int i = 0; i < siblings.size(); i++) {
            WorkspaceQuestion q = siblings.get(i);
            result.add(new QuestionNavItem(
                    q.getId(),
                    i + 1,
                    q.getTitle(),
                    q.getWashedKr() != null && !q.getWashedKr().isBlank()
            ));
        }
        return result;
    }

    // ── 선택 텍스트 세탁 (번역 왕복) ─────────────────────────────────────────

    public String rewashSelection(String selectedText) {
        String en = translationService.translateToEnglish(selectedText);
        return translationService.translateToKorean(en);
    }

    // ── 내부 유틸 ─────────────────────────────────────────────────────────────

    private WorkspaceQuestion findQuestion(Long questionId) {
        return questionRepository.findById(questionId)
                .orElseThrow(() -> new IllegalArgumentException("Question not found: " + questionId));
    }
}
