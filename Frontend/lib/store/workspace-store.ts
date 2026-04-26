import { create } from "zustand";
import { toApiUrl } from "@/lib/network/api-base";
import { streamSse } from "@/lib/network/stream-sse";
import { useBackgroundTaskStore, type BackgroundTaskType } from "@/lib/store/background-task-store";
import {
  playCompletionSound,
  prepareCompletionSound,
} from "../audio/completion-sound";
import type {
  BatchPlanResponse,
  ContextItem,
  PipelineStage,
  TitleSuggestionResponse,
  WorkspaceCompletionPayload,
  WorkspaceQuestion,
} from "@/lib/workspace/types";
import {
  applySuggestionToQuestion,
  createQuestionRequestBody,
  mapStoredQuestion,
  normalizeCompletionMistranslations,
  normalizeLengthTarget,
  shouldSyncQuestion,
} from "@/lib/workspace/store-helpers";

export type {
  AiReviewReport,
  ContextItem,
  Mistranslation,
  PipelineStage,
  BatchPlanResponse,
  TitleSuggestion,
  TitleSuggestionResponse,
  WorkspaceQuestion,
  QuestionCategory,
} from "@/lib/workspace/types";
export { QUESTION_CATEGORY_LABELS } from "@/lib/workspace/types";

export interface QuestionBatchState {
  isProcessing: boolean;
  stage: PipelineStage;
  message: string;
  error?: string;
}

// Module-level registry — avoids Zustand re-renders on controller changes
const batchControllerRegistry: Record<string, AbortController> = {};

type SaveTimeoutHandle = ReturnType<typeof setTimeout> | null;

interface WorkspaceState {
  // Global context
  applicationId: string | null;
  company: string;
  position: string;
  aiInsight: string;
  companyResearch: string;

  // Questions management
  questions: WorkspaceQuestion[];
  activeQuestionId: string | null;
  extractedContext: ContextItem[];

  // UI State
  leftPanelTab: "context" | "report";
  hoveredMistranslationId: string | null;

  // Transient processing state (single-question)
  isProcessing: boolean;
  pipelineStage: PipelineStage;
  progressMessage: string;
  progressHistory: string[];
  processingIssue: string | null;
  processingIssueSeverity: "warning" | "error" | null;
  activeStreamController: AbortController | null;
  saveTimeout: SaveTimeoutHandle;

  // Batch processing state
  isBatchRunning: boolean;
  batchState: Record<string, QuestionBatchState>;
  batchPlan: BatchPlanResponse | null;
  isBatchPlanLoading: boolean;
  batchPlanError: string | null;

  // Actions
  setCompany: (company: string) => void;
  setPosition: (position: string) => void;

  setQuestions: (questions: WorkspaceQuestion[]) => void;
  setActiveQuestionId: (id: string) => void;
  setLeftPanelTab: (tab: "context" | "report") => void;
  setHoveredMistranslationId: (id: string | null) => void;
  addQuestion: () => void;
  updateActiveQuestion: (updates: Partial<WorkspaceQuestion>) => void;
  applySuggestion: (mistranslationId: string, replaceFullSentence?: boolean) => void;
  updateMistranslationSuggestion: (mistranslationId: string, suggestion: string) => void;
  dismissMistranslation: (mistranslationId: string) => void;

  fetchApplicationData: (id: string, initialDbId?: number) => Promise<void>;
  fetchRAGContext: (questionId: number, query?: string) => Promise<void>;

  deleteActiveQuestion: () => Promise<void>;
  toggleActiveQuestionCompletion: () => Promise<void>;

  startProcessing: () => void;
  setPipelineStage: (stage: PipelineStage) => void;
  updateProgress: (message: string) => void;
  updateDraftIntermediate: (draft: string) => void;
  updateWashedIntermediate: (washed: string) => void;
  completeProcessing: (data: WorkspaceCompletionPayload) => void;
  setError: (error: string) => void;
  setWarning: (warning: string) => void;
  generateDraft: (options?: { useDirective?: boolean; lengthTarget?: number | null }) => Promise<void>;
  refineDraft: (directive: string, options?: { lengthTarget?: number | null }) => Promise<void>;
  fetchTitleSuggestions: () => Promise<TitleSuggestionResponse>;
  applyTitleSuggestion: (title: string) => Promise<void>;
  rerunWash: () => Promise<void>;
  rerunPatch: () => Promise<void>;
  fetchBatchPlan: () => Promise<BatchPlanResponse>;
  applyBatchPlan: (plan: BatchPlanResponse) => void;
  clearBatchPlan: () => void;
  batchGenerate: (options?: { useDirective?: boolean }) => Promise<void>;
  cancelBatch: () => void;
  cancelSingle: () => void;
  updateQuestionCategory: (questionDbId: number, category: import("@/lib/workspace/types").QuestionCategory | null) => Promise<void>;
}

async function ensureQuestionPersisted(state: WorkspaceState) {
  const activeQ = state.questions.find((q) => q.id === state.activeQuestionId);
  if (!activeQ) {
    return null;
  }

  if (activeQ.dbId) {
    return activeQ;
  }

  if (!state.applicationId) {
    return null;
  }

  const response = await fetch(`/api/applications/${state.applicationId}/questions`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({
      title: activeQ.title,
      maxLength: activeQ.maxLength,
      isCompleted: activeQ.isCompleted,
    }),
  });

  if (!response.ok) {
    throw new Error("Failed to create question");
  }

  const savedQuestion = await response.json();
  const persistedQuestion = { ...activeQ, dbId: savedQuestion.id };

  useWorkspaceStore.setState((current) => ({
    questions: current.questions.map((question) =>
      question.id === activeQ.id ? persistedQuestion : question
    ),
  }));

  return persistedQuestion;
}

function findActiveQuestion(state: WorkspaceState) {
  return state.questions.find((question) => question.id === state.activeQuestionId) ?? null;
}

async function persistQuestionUpdate(question: WorkspaceQuestion) {
  const response = await fetch(`/api/applications/questions/${question.dbId}`, {
    method: "PUT",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(createQuestionRequestBody(question)),
  });

  if (!response.ok) {
    throw new Error("Failed to persist question update");
  }
}

const defaultQuestion: WorkspaceQuestion = {
  id: "q-default",
  title: "Describe a project you joined and explain how you solved a technical challenge.",
  maxLength: 1000,
  lengthTarget: null,
  content: "",
  washedKr: "",
  mistranslations: [],
  aiReviewReport: null,
  userDirective: "",
  batchStrategyDirective: "",
  isCompleted: false,
  category: null,
};

export const useWorkspaceStore = create<WorkspaceState>((set, get) => ({
  applicationId: null,
  company: "",
  position: "",
  aiInsight: "",
  companyResearch: "",
  questions: [defaultQuestion],
  activeQuestionId: "q-default",
  extractedContext: [],
  leftPanelTab: "context",
  hoveredMistranslationId: null,
  isProcessing: false,
  pipelineStage: "IDLE",
  progressMessage: "",
  progressHistory: [],
  processingIssue: null,
  processingIssueSeverity: null,
  activeStreamController: null,
  saveTimeout: null,
  isBatchRunning: false,
  batchState: {},
  batchPlan: null,
  isBatchPlanLoading: false,
  batchPlanError: null,

  setCompany: (company) => set({ company }),
  setPosition: (position) => set({ position }),

  setQuestions: (questions) => set({ questions }),
  setActiveQuestionId: (activeQuestionId) => {
    set({ activeQuestionId });
    // Trigger RAG fetch when changing question
    const state = get();
    const activeQ = state.questions.find(q => q.id === activeQuestionId);
    if (activeQ?.dbId) {
      state.fetchRAGContext(activeQ.dbId, activeQ.title);
    }
  },
  
  setLeftPanelTab: (leftPanelTab) => set({ leftPanelTab }),
  setHoveredMistranslationId: (hoveredMistranslationId) => set({ hoveredMistranslationId }),
  
  addQuestion: async () => {
    const state = get();
    if (!state.applicationId) return;

    const newId = `q-${Math.random().toString(36).substr(2, 9)}`;
    const newQuestion: WorkspaceQuestion = {
      ...defaultQuestion,
      id: newId,
      title: "",
      content: "",
      washedKr: "",
    };

    // Update local state first for responsiveness
    set((state) => ({
      questions: [...state.questions, newQuestion],
      activeQuestionId: newId,
    }));

    try {
      const response = await fetch(`/api/applications/${state.applicationId}/questions`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          title: newQuestion.title,
          maxLength: newQuestion.maxLength,
          isCompleted: false
        })
      });
      if (!response.ok) throw new Error("Failed to sync new question");
      const savedQuestion = await response.json();
      
      // Update the local question with the real dbId
      set((state) => ({
        questions: state.questions.map(q => 
          q.id === newId ? { ...q, dbId: savedQuestion.id } : q
        )
      }));
    } catch (err) {
      console.error("Failed to persist new question", err);
    }
  },

  updateActiveQuestion: (updates) => {
    const state = get();
    const updatedQuestions = state.questions.map(q => 
      q.id === state.activeQuestionId ? { ...q, ...updates } : q
    );
    set({ questions: updatedQuestions });

    const activeQuestion = updatedQuestions.find(q => q.id === state.activeQuestionId);

    if (activeQuestion?.dbId && shouldSyncQuestion(updates)) {
      const existingSaveTimeout = get().saveTimeout;
      if (existingSaveTimeout) {
        clearTimeout(existingSaveTimeout);
      }

      const timeout = setTimeout(() => {
        persistQuestionUpdate(activeQuestion)
        .then(() => {
          if (updates.title !== undefined) {
             get().fetchRAGContext(activeQuestion.dbId!, activeQuestion.title);
          }
        })
        .catch(err => console.error("Auto-save failed", err));
      }, 300);

      set({ saveTimeout: timeout });
    }
  },
  
  applySuggestion: (mistranslationId, replaceFullSentence = true) => {
    const state = get();
    const activeQ = findActiveQuestion(state);
    if (!activeQ) return;
    const nextQuestionState = applySuggestionToQuestion(activeQ, mistranslationId, replaceFullSentence);
    if (!nextQuestionState) return;

    state.updateActiveQuestion(nextQuestionState);
  },

  updateMistranslationSuggestion: (mistranslationId, suggestion) => {
    const state = get();
    const activeQ = findActiveQuestion(state);
    if (!activeQ) return;

    const updatedMistranslations = activeQ.mistranslations.map(m => 
      m.id === mistranslationId ? { ...m, suggestion } : m
    );

    state.updateActiveQuestion({ mistranslations: updatedMistranslations });
  },

  dismissMistranslation: (mistranslationId) => {
    const state = get();
    const activeQ = findActiveQuestion(state);
    if (!activeQ) return;
    
    const newMistranslations = activeQ.mistranslations.filter(m => m.id !== mistranslationId);
    state.updateActiveQuestion({ mistranslations: newMistranslations });
  },

  fetchApplicationData: async (id, initialDbId) => {
    const loadingMessage = "Loading application data...";
    set({
      isProcessing: true,
      pipelineStage: "IDLE",
      progressMessage: loadingMessage,
      progressHistory: [loadingMessage],
      processingIssue: null,
      processingIssueSeverity: null,
    });

    try {
      const response = await fetch(`/api/applications/${id}`);
      if (!response.ok) throw new Error("Failed to fetch application");
      const data = await response.json();

      const mappedQuestions: WorkspaceQuestion[] = data.questions.map(mapStoredQuestion);

      // initialDbId가 있으면 해당 문항을 활성화, 없으면 첫 번째 문항
      const targetQuestion = initialDbId
        ? mappedQuestions.find((q) => q.dbId === initialDbId)
        : null;
      const initialActiveId = targetQuestion?.id
        ?? (mappedQuestions.length > 0 ? mappedQuestions[0].id : "q-default");

      set({
        applicationId: id,
        company: data.companyName,
        position: data.position,
        aiInsight: data.aiInsight || "",
        companyResearch: data.companyResearch || "",
        questions: mappedQuestions.length > 0 ? mappedQuestions : [defaultQuestion],
        activeQuestionId: initialActiveId,
        batchPlan: null,
        batchPlanError: null,
        isBatchPlanLoading: false,
        isProcessing: false,
        pipelineStage: "IDLE",
        progressMessage: "",
        progressHistory: [],
        processingIssue: null,
        processingIssueSeverity: null,
      });

      const activeForRag = targetQuestion ?? (mappedQuestions.length > 0 ? mappedQuestions[0] : null);
      if (activeForRag?.dbId) {
        get().fetchRAGContext(activeForRag.dbId, activeForRag.title);
      }
    } catch (err) {
      console.error(err);
      set({
        isProcessing: false,
        pipelineStage: "IDLE",
        progressMessage: "오류",
        processingIssue: "지원 정보를 불러오는 데 실패했습니다.",
        processingIssueSeverity: "error",
      });
    }
  },

  deleteActiveQuestion: async () => {
    const state = get();
    const activeQ = await ensureQuestionPersisted(state);
    if (!activeQ?.dbId) {
      state.setError("유효한 문항 ID를 찾을 수 없습니다.");
      return;
    }

    if (!confirm("이 문항을 삭제하시겠습니까?")) return;

    try {
      const response = await fetch(`/api/applications/questions/${activeQ.dbId}`, {
        method: "DELETE",
      });
      if (!response.ok) throw new Error("Failed to delete question");

      const remainingQuestions = state.questions.filter((q) => q.id !== state.activeQuestionId);
      set({
        questions: remainingQuestions,
        activeQuestionId: remainingQuestions.length > 0 ? remainingQuestions[0].id : null,
      });
    } catch (err) {
      console.error(err);
      alert("문항 삭제에 실패했습니다.");
    }
  },

  toggleActiveQuestionCompletion: async () => {
    const state = get();
    const activeQ = findActiveQuestion(state);
    if (!activeQ) return;

    const newStatus = !activeQ.isCompleted;
    state.updateActiveQuestion({ isCompleted: newStatus });
  },

  fetchRAGContext: async (questionId, query) => {
    try {
      const pathname = query
        ? `/api/applications/questions/${questionId}/rag?query=${encodeURIComponent(query)}`
        : `/api/applications/questions/${questionId}/rag`;

      const response = await fetch(toApiUrl(pathname), { cache: "no-store" });
      if (!response.ok) {
        console.warn(`RAG context unavailable: ${response.status} ${response.statusText}`);
        set({ extractedContext: [] });
        return;
      }
      const data = await response.json();
      set({ extractedContext: data.extractedContext || [] });
    } catch (err) {
      console.warn("RAG context unavailable", err);
      set({ extractedContext: [] });
    }
  },

  startProcessing: () => {
    get().activeStreamController?.abort();

    const initialMessage = "서버 응답을 기다리는 중...";
    set({
      isProcessing: true,
      pipelineStage: "RAG",
      progressMessage: initialMessage,
      progressHistory: [initialMessage],
      processingIssue: null,
      processingIssueSeverity: null,
      leftPanelTab: "context",
      activeStreamController: null,
    });
  },

  setPipelineStage: (pipelineStage) => set({ pipelineStage }),

  updateProgress: (progressMessage) =>
    set((state) => ({
      progressMessage,
      progressHistory: appendProgressHistory(state.progressHistory, progressMessage),
      pipelineStage: inferStageFromProgress(state.pipelineStage, progressMessage),
    })),

  updateDraftIntermediate: (draft) =>
    set((state) => ({
      pipelineStage: state.pipelineStage === "RAG" ? "DRAFT" : state.pipelineStage,
      questions: state.questions.map((q) =>
        q.id === state.activeQuestionId ? { ...q, content: draft } : q
      ),
    })),

  updateWashedIntermediate: (washedKr) =>
    set((state) => ({
      pipelineStage: state.pipelineStage === "PATCH" ? "PATCH" : "WASH",
      questions: state.questions.map((q) =>
        q.id === state.activeQuestionId ? { ...q, washedKr } : q
      ),
    })),

  completeProcessing: (data) => {
    const state = get();
    const activeQ = findActiveQuestion(state);
    if (!activeQ) return;

    const updatedMistranslations = normalizeCompletionMistranslations(data.mistranslations);
    const finalWashedDraft = data.draft || "";
    const sourceDraft = data.sourceDraft || activeQ.content;
    const warningMessage = data.warningMessage || null;

    set((state) => ({
      isProcessing: false,
      pipelineStage: "DONE",
      progressMessage: "Completed.",
      progressHistory: appendProgressHistory(state.progressHistory, "Completed."),
      processingIssue:
        warningMessage ||
        (state.processingIssueSeverity === "warning" ? state.processingIssue : null),
      processingIssueSeverity:
        warningMessage || state.processingIssueSeverity === "warning" ? "warning" : null,
      activeStreamController: null,
      leftPanelTab: "report",
      questions: state.questions.map((q) =>
        q.id === state.activeQuestionId
          ? {
              ...q,
              content: sourceDraft,
              washedKr: finalWashedDraft,
              mistranslations: updatedMistranslations,
              aiReviewReport: data.aiReviewReport ?? null,
            }
          : q
      ),
    }));

    void playCompletionSound();

    if (activeQ.dbId) {
      useBackgroundTaskStore.getState().unregister(activeQ.dbId);
      persistQuestionUpdate({
        ...activeQ,
        content: sourceDraft,
        washedKr: finalWashedDraft,
        mistranslations: updatedMistranslations,
        aiReviewReport: data.aiReviewReport ?? null,
      }).catch((err) => console.error("Initial wash save failed", err));
    }
  },

  setError: (error) =>
    set((state) => {
      const message = error || "오류가 발생했습니다.";
      return {
        isProcessing: false,
        pipelineStage: "IDLE",
        progressMessage: message,
        progressHistory: appendProgressHistory(state.progressHistory, message),
        processingIssue: message,
        processingIssueSeverity: "error",
        activeStreamController: null,
        leftPanelTab: "context",
      };
    }),

  setWarning: (warning) =>
    set((state) => {
      const message = warning || "경고가 발생했습니다.";
      return {
        progressMessage: message,
        progressHistory: appendProgressHistory(state.progressHistory, message),
        processingIssue: message,
        processingIssueSeverity: "warning",
      };
    }),

  generateDraft: async (options) => {
    const state = get();

    // Flush any pending debounced save so the backend reads the latest directive
    const existingSaveTimeout = get().saveTimeout;
    if (existingSaveTimeout) {
      clearTimeout(existingSaveTimeout);
      set({ saveTimeout: null });
    }

    let activeQ;
    try {
      activeQ = await ensureQuestionPersisted(state);
    } catch (error) {
      console.error("Failed to persist question before draft generation", error);
      state.setError("문항 저장 중 오류가 발생했습니다.");
      return;
    }
    if (!activeQ?.dbId) {
      state.setError("유효한 문항 ID를 찾을 수 없습니다.");
      return;
    }

    // Immediately persist to DB so the backend sees the latest userDirective
    try {
      await persistQuestionUpdate(activeQ);
    } catch (err) {
      console.warn("Pre-generation flush save failed, proceeding with current DB state", err);
    }

    await prepareCompletionSound();
    state.startProcessing();
    const useDirective = options?.useDirective ?? true;
    const requestedLengthTarget = options?.lengthTarget ?? activeQ.lengthTarget;
    const normalizedTarget = normalizeLengthTarget(requestedLengthTarget, activeQ.maxLength);
    const targetQuery = normalizedTarget ? `&targetChars=${normalizedTarget}` : "";
    registerBackgroundTask(activeQ.dbId, get().applicationId, "generate");
    await runWorkspaceSse({
      url: toApiUrl(
        `/api/workspace/v2/stream/${activeQ.dbId}?useDirective=${useDirective}${targetQuery}`
      ),
      set,
      get,
      errorFallbackMessage: "초안 생성 중 오류가 발생했습니다.",
    });
  },

  refineDraft: async (directive: string, options?: { lengthTarget?: number | null }) => {
    const state = get();
    let activeQ;
    try {
      activeQ = await ensureQuestionPersisted(state);
    } catch (error) {
      console.error("Failed to persist question before refinement", error);
      state.setError("문항 저장 중 오류가 발생했습니다.");
      return;
    }
    if (!activeQ?.dbId) {
      state.setError("유효한 문항 ID를 찾을 수 없습니다.");
      return;
    }

    await prepareCompletionSound();
    state.startProcessing();
    state.updateActiveQuestion({ userDirective: directive });
    const requestedLengthTarget = options?.lengthTarget ?? activeQ.lengthTarget;
    const normalizedTarget = normalizeLengthTarget(requestedLengthTarget, activeQ.maxLength);
    const targetQuery = normalizedTarget ? `&targetChars=${normalizedTarget}` : "";
    registerBackgroundTask(activeQ.dbId, get().applicationId, "refine");
    await runWorkspaceSse({
      url: toApiUrl(
        `/api/workspace/refine-stream/${activeQ.dbId}?directive=${encodeURIComponent(
          directive
        )}${targetQuery}`
      ),
      set,
      get,
      errorFallbackMessage: "초안 다듬기 중 오류가 발생했습니다.",
    });
  },

  fetchTitleSuggestions: async () => {
    const state = get();
    const emptyResponse: TitleSuggestionResponse = { currentTitle: "", candidates: [] };
    let activeQ;
    try {
      activeQ = await ensureQuestionPersisted(state);
    } catch (error) {
      console.error("Failed to persist question before title rewrite", error);
      state.setError("문항 저장 중 오류가 발생했습니다.");
      return emptyResponse;
    }

    if (!activeQ?.dbId) {
      state.setError("유효한 문항 ID를 찾을 수 없습니다.");
      return emptyResponse;
    }

    try {
      const response = await fetch(toApiUrl(`/api/workspace/title-suggestions/${activeQ.dbId}`));
      if (!response.ok) {
        throw new Error("Failed to fetch title suggestions");
      }

      return (await response.json()) as TitleSuggestionResponse;
    } catch (error) {
      console.error("Title suggestion fetch failed", error);
      state.setError("제목 추천을 불러오는 중 오류가 발생했습니다.");
      return emptyResponse;
    }
  },

  applyTitleSuggestion: async (title: string) => {
    const state = get();
    let activeQ;
    try {
      activeQ = await ensureQuestionPersisted(state);
    } catch (error) {
      console.error("Failed to persist question before title apply", error);
      state.setError("문항 저장 중 오류가 발생했습니다.");
      return;
    }

    if (!activeQ?.dbId) {
      state.setError("유효한 문항 ID를 찾을 수 없습니다.");
      return;
    }

    try {
      const response = await fetch(toApiUrl(`/api/workspace/title/${activeQ.dbId}`), {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ title }),
      });
      if (!response.ok) {
        throw new Error("Failed to apply title suggestion");
      }

      const updatedQuestion = mapStoredQuestion(await response.json());
      set((current) => ({
        processingIssue: null,
        processingIssueSeverity: null,
        questions: current.questions.map((question) =>
          question.id === activeQ.id
            ? {
                ...question,
                ...updatedQuestion,
                id: question.id,
                lengthTarget: question.lengthTarget,
              }
            : question
        ),
      }));
    } catch (error) {
      console.error("Title suggestion apply failed", error);
      state.setError("An error occurred while applying the title.");
    }
  },

  rerunWash: async () => {
    const state = get();
    const activeQ = findActiveQuestion(state);
    if (!activeQ?.dbId) {
      state.setError("유효한 문항 ID를 찾을 수 없습니다.");
      return;
    }

    await prepareCompletionSound();
    state.startProcessing();

    registerBackgroundTask(activeQ.dbId, get().applicationId, "rewash");
    await runWorkspaceSse({
      url: toApiUrl(`/api/workspace/rewash-stream/${activeQ.dbId}`),
      set,
      get,
      errorFallbackMessage: "An error occurred while rerunning wash.",
    });
  },

  rerunPatch: async () => {
    const state = get();
    const activeQ = findActiveQuestion(state);
    if (!activeQ?.dbId) {
      state.setError("유효한 문항 ID를 찾을 수 없습니다.");
      return;
    }

    await prepareCompletionSound();
    state.startProcessing();

    registerBackgroundTask(activeQ.dbId, get().applicationId, "repatch");
    await runWorkspaceSse({
      url: toApiUrl(`/api/workspace/repatch-stream/${activeQ.dbId}`),
      set,
      get,
      errorFallbackMessage: "An error occurred while rerunning patch.",
    });
  },

  fetchBatchPlan: async () => {
    const state = get();
    const targets = state.questions.filter((q) => q.dbId && q.title.trim().length > 0);
    if (!state.applicationId || targets.length === 0) {
      throw new Error("No eligible questions found for planning.");
    }

    set({ isBatchPlanLoading: true, batchPlanError: null, batchPlan: null });

    try {
      const response = await fetch(toApiUrl("/api/workspace/batch-plan"), {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          applicationId: Number(state.applicationId),
          questions: targets.map((question) => ({
            questionId: question.dbId,
            title: question.title,
            maxLength: question.maxLength,
            userDirective: question.userDirective,
            batchStrategyDirective: "",
            content: "",
            washedKr: "",
            category: question.category ?? null,
          })),
        }),
      });

      if (!response.ok) {
        throw new Error("Failed to fetch batch plan");
      }

      const plan = (await response.json()) as BatchPlanResponse;
      set({ batchPlan: plan, isBatchPlanLoading: false, batchPlanError: null });
      return plan;
    } catch (error) {
      console.error("Failed to fetch batch plan", error);
      set({
        isBatchPlanLoading: false,
        batchPlanError: "전략 생성에 실패했습니다. 잠시 후 다시 시도해 주세요.",
      });
      throw error;
    }
  },

  applyBatchPlan: (plan) => {
    const assignmentMap = new Map(
      (plan.assignments || []).map((assignment) => [assignment.questionId, assignment])
    );

    set((state) => ({
      batchPlan: plan,
      questions: state.questions.map((question) => {
        if (!question.dbId) {
          return question;
        }

        const assignment = assignmentMap.get(question.dbId);
        if (!assignment) {
          return question;
        }

        return {
          ...question,
          batchStrategyDirective: assignment.directivePrefix,
        };
      }),
    }));
  },

  clearBatchPlan: () => set({ batchPlan: null, batchPlanError: null, isBatchPlanLoading: false }),

  batchGenerate: async (options) => {
    const state = get();

    // Abort any active single-question stream
    state.activeStreamController?.abort();

    // Target: all questions that have a dbId and a non-empty title
    const targets = state.questions.filter((q) => q.dbId && q.title.trim().length > 0);
    if (targets.length === 0) return;

    await prepareCompletionSound();

    // Initialise per-question batch state and register controllers
    const initialBatchState: Record<string, QuestionBatchState> = {};
    for (const q of targets) {
      initialBatchState[q.id] = { isProcessing: true, stage: "RAG", message: "대기 중..." };
      batchControllerRegistry[q.id] = new AbortController();
    }

    // Flush pending saves for all target questions so backend sees latest directives
    await Promise.allSettled(targets.map((q) => persistQuestionUpdate(q)));

    set({ isBatchRunning: true, batchState: initialBatchState, isProcessing: false });

    const useDirective = options?.useDirective ?? true;

    const promises = targets.map((q) =>
      runBatchQuestionSse(q.id, q.dbId!, useDirective, set, get)
    );

    await Promise.allSettled(promises);

    // If cancelBatch() was called while we were awaiting, registry is already empty
    // and isBatchRunning is already false — skip cleanup and sound.
    if (!get().isBatchRunning) return;

    // Clean up controllers
    for (const q of targets) {
      delete batchControllerRegistry[q.id];
    }

    set({ isBatchRunning: false });
    void playCompletionSound();
  },

  cancelBatch: () => {
    // Abort all active batch streams
    for (const controller of Object.values(batchControllerRegistry)) {
      controller.abort();
    }
    // Clear registry
    for (const key of Object.keys(batchControllerRegistry)) {
      delete batchControllerRegistry[key];
    }
    set((state) => {
      const cancelledState: Record<string, QuestionBatchState> = {};
      for (const [id, qs] of Object.entries(state.batchState)) {
        cancelledState[id] = qs.isProcessing
          ? { isProcessing: false, stage: "IDLE", message: "취소됨" }
          : qs;
      }
      return { isBatchRunning: false, batchState: cancelledState };
    });
  },

  cancelSingle: () => {
    const { activeStreamController } = get();
    // SSE 스트림이 활성 중이면 abort
    if (activeStreamController) {
      activeStreamController.abort();
    }
    const activeQ = findActiveQuestion(get());
    if (activeQ?.dbId) {
      useBackgroundTaskStore.getState().unregister(activeQ.dbId);
      // Redis의 RUNNING 상태도 제거 (백그라운드 폴링 중단)
      fetch(toApiUrl(`/api/workspace/task-status/${activeQ.dbId}`), { method: "DELETE" }).catch(() => {});
    }
    set((state) => ({
      isProcessing: false,
      pipelineStage: "IDLE",
      activeStreamController: null,
      progressMessage: "생성이 취소되었습니다.",
      progressHistory: appendProgressHistory(state.progressHistory, "생성이 취소되었습니다."),
      processingIssue: null,
      processingIssueSeverity: null,
    }));
  },

  updateQuestionCategory: async (questionDbId, category) => {
    const response = await fetch(toApiUrl(`/api/workspace/questions/${questionDbId}/category`), {
      method: "PATCH",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ category }),
    });
    if (!response.ok) throw new Error("카테고리 업데이트에 실패했습니다.");
    set((state) => ({
      questions: state.questions.map((q) =>
        q.dbId === questionDbId ? { ...q, category } : q
      ),
    }));
  },
}));

type ParsedSseObject = {
  stage?: unknown;
  message?: unknown;
  data?: unknown;
};

function parseSsePayload(raw: string): string | ParsedSseObject | null {
  if (!raw) {
    return "";
  }

  try {
    const parsed: unknown = JSON.parse(raw);
    if (typeof parsed === "string") {
      return parsed;
    }
    if (parsed && typeof parsed === "object") {
      return parsed as ParsedSseObject;
    }
    return parsed == null ? "" : String(parsed);
  } catch {
    return raw;
  }
}

function parseSseMessage(raw: string) {
  const parsed = parseSsePayload(raw);
  if (!parsed) {
    return "";
  }
  if (typeof parsed === "string") {
    return parsed;
  }
  if (typeof parsed.message === "string") {
    return parsed.message;
  }
  if (typeof parsed.data === "string") {
    return parsed.data;
  }
  if (typeof parsed.stage === "string") {
    return parsed.stage;
  }
  return "";
}

function parseSseJson(raw: string) {
  try {
    return JSON.parse(raw);
  } catch {
    return null;
  }
}

function parsePipelineStage(raw: string): PipelineStage | null {
  const parsed = parseSsePayload(raw);
  const rawStage = typeof parsed === "object" && parsed && typeof parsed.stage === "string"
    ? parsed.stage
    : parseSseMessage(raw);
  const stage = rawStage.trim().toUpperCase();
  if (
    stage === "ANALYSIS" ||
    stage === "RAG" ||
    stage === "DRAFT" ||
    stage === "WASH" ||
    stage === "PATCH" ||
    stage === "DONE"
  ) {
    return stage;
  }
  return null;
}

function inferStageFromProgress(current: PipelineStage, message: string): PipelineStage {
  const normalized = (message || "").toLowerCase();

  if (
    normalized.includes("done") ||
    normalized.includes("complete") ||
    normalized.includes("finished")
  ) {
    return "DONE";
  }

  if (
    normalized.includes("patch") ||
    normalized.includes("review") ||
    normalized.includes("휴먼 패치") ||
    normalized.includes("오역") ||
    normalized.includes("검수")
  ) {
    return "PATCH";
  }

  if (
    normalized.includes("wash") ||
    normalized.includes("translate") ||
    normalized.includes("translation") ||
    normalized.includes("세탁") ||
    normalized.includes("번역")
  ) {
    return current === "DONE" ? current : "WASH";
  }

  if (normalized.includes("draft") || normalized.includes("writing") || normalized.includes("초안")) {
    if (current === "PATCH" || current === "DONE") {
      return current;
    }
    return "DRAFT";
  }

  if (
    normalized.includes("analysis") ||
    normalized.includes("문항") ||
    normalized.includes("분석")
  ) {
    if (current === "IDLE" || current === "RAG") {
      return "ANALYSIS";
    }
  }

  if (
    normalized.includes("rag") ||
    normalized.includes("context") ||
    normalized.includes("경험") ||
    normalized.includes("컨텍스트")
  ) {
    if (current === "IDLE") {
      return "RAG";
    }
  }

  return current;
}

function appendProgressHistory(history: string[], message: string) {
  if (!message) {
    return history;
  }

  if (history[history.length - 1] === message) {
    return history;
  }

  return [...history, message].slice(-8);
}

async function runWorkspaceSse({
  url,
  set,
  get,
  errorFallbackMessage,
}: {
  url: string;
  set: typeof useWorkspaceStore.setState;
  get: () => WorkspaceState;
  errorFallbackMessage: string;
}) {
  const controller = new AbortController();
  set({ activeStreamController: controller });

  try {
    await streamSse({
      url,
      signal: controller.signal,
      onOpen: () => {
        get().updateProgress("Pipeline connected.");
      },
      onEvent: ({ event, data }) => {
        const eventName = event.toLowerCase();

        if (eventName === "stage") {
          const parsedStage = parsePipelineStage(data);
          if (parsedStage) {
            get().setPipelineStage(parsedStage);
          }
          return;
        }

        if (eventName === "progress") {
          const parsedStage = parsePipelineStage(data);
          if (parsedStage) {
            get().setPipelineStage(parsedStage);
          }
          get().updateProgress(parseSseMessage(data));
          return;
        }

        if (eventName === "draft_intermediate") {
          get().updateDraftIntermediate(parseSseMessage(data));
          return;
        }

        if (eventName === "washed_intermediate") {
          get().updateWashedIntermediate(parseSseMessage(data));
          return;
        }

        if (eventName === "complete") {
          const parsed = parseSseJson(data);
          if (!parsed) {
            get().setError("Could not parse SSE completion payload.");
            controller.abort();
            return;
          }

          get().completeProcessing(parsed);
          controller.abort();
          return;
        }

        if (eventName === "error") {
          const parsed = parseSseJson(data);
          get().setError(
            parsed?.message || parseSseMessage(data) || errorFallbackMessage
          );
          controller.abort();
          return;
        }

        if (eventName === "warning") {
          const parsed = parseSseJson(data);
          get().setWarning(
            parsed?.message || parseSseMessage(data) || "A warning occurred."
          );
        }
      },
    });

    const state = get();
    if (state.isProcessing && state.processingIssueSeverity !== "error") {
      state.setError("The stream ended unexpectedly.");
    }
  } catch (error) {
    if (controller.signal.aborted) {
      return;
    }

    get().setError(normalizeSseError(error, errorFallbackMessage));
  } finally {
    if (get().activeStreamController === controller) {
      set({ activeStreamController: null });
    }
  }
}

function registerBackgroundTask(
  questionId: number | undefined,
  applicationId: string | null,
  taskType: BackgroundTaskType
) {
  if (!questionId || !applicationId) return;
  useBackgroundTaskStore.getState().register({
    questionId,
    applicationId,
    taskType,
    startedAt: Date.now(),
  });
}

function normalizeSseError(error: unknown, fallbackMessage: string) {
  if (error instanceof DOMException && error.name === "AbortError") {
    return fallbackMessage;
  }

  if (error instanceof Error && error.message) {
    return "An error occurred.";
  }

  return "An error occurred.";
}

// ── Batch processing helper ─────────────────────────────────────────────────

function setBatchQuestion(
  set: typeof useWorkspaceStore.setState,
  questionId: string,
  patch: Partial<QuestionBatchState>
) {
  set((state) => ({
    batchState: {
      ...state.batchState,
      [questionId]: { ...state.batchState[questionId], ...patch },
    },
  }));
}

async function runBatchQuestionSse(
  questionId: string,
  dbId: number,
  useDirective: boolean,
  set: typeof useWorkspaceStore.setState,
  get: () => WorkspaceState
) {
  const controller = batchControllerRegistry[questionId];
  if (!controller) return;

  try {
    await streamSse({
      url: toApiUrl(`/api/workspace/v2/stream/${dbId}?useDirective=${useDirective}`),
      signal: controller.signal,
      onOpen: () => {
        setBatchQuestion(set, questionId, { message: "파이프라인 연결됨" });
      },
      onEvent: ({ event, data }) => {
        const eventName = event.toLowerCase();

        if (eventName === "stage") {
          const stage = parsePipelineStage(data);
          if (stage) setBatchQuestion(set, questionId, { stage });
          return;
        }

        if (eventName === "progress") {
          const stage = parsePipelineStage(data);
          setBatchQuestion(set, questionId, {
            ...(stage ? { stage } : {}),
            message: parseSseMessage(data),
          });
          return;
        }

        if (eventName === "draft_intermediate") {
          const draft = parseSseMessage(data);
          set((state) => ({
            questions: state.questions.map((q) =>
              q.id === questionId ? { ...q, content: draft } : q
            ),
          }));
          return;
        }

        if (eventName === "washed_intermediate") {
          const washed = parseSseMessage(data);
          set((state) => ({
            questions: state.questions.map((q) =>
              q.id === questionId ? { ...q, washedKr: washed } : q
            ),
          }));
          return;
        }

        if (eventName === "complete") {
          const parsed = parseSseJson(data);
          if (!parsed) {
            setBatchQuestion(set, questionId, {
              isProcessing: false,
              stage: "IDLE",
              error: "Could not parse completion data.",
            });
            controller.abort();
            return;
          }

          const updatedMistranslations = normalizeCompletionMistranslations(parsed.mistranslations);
          const finalWashedDraft = parsed.draft || "";
          const sourceDraft = parsed.sourceDraft || "";

          set((state) => {
            const question = state.questions.find((q) => q.id === questionId);
            if (question?.dbId) {
              persistQuestionUpdate({
                ...question,
                content: sourceDraft || question.content,
                washedKr: finalWashedDraft,
                mistranslations: updatedMistranslations,
                aiReviewReport: parsed.aiReviewReport ?? null,
              }).catch((err) => console.error("Batch question save failed", err));
            }
            return {
              batchState: {
                ...state.batchState,
                [questionId]: { isProcessing: false, stage: "DONE", message: "완료" },
              },
              questions: state.questions.map((q) =>
                q.id === questionId
                  ? {
                      ...q,
                      content: sourceDraft || q.content,
                      washedKr: finalWashedDraft,
                      mistranslations: updatedMistranslations,
                      aiReviewReport: parsed.aiReviewReport ?? null,
                    }
                  : q
              ),
            };
          });
          controller.abort();
          return;
        }

        if (eventName === "error") {
          const parsed = parseSseJson(data);
          const message = parsed?.message || parseSseMessage(data) || "오류 발생";
          setBatchQuestion(set, questionId, {
            isProcessing: false,
            stage: "IDLE",
            message,
            error: message,
          });
          controller.abort();
        }
      },
    });

    // Stream ended without explicit complete/error
    const currentState = get().batchState[questionId];
    if (currentState?.isProcessing) {
      setBatchQuestion(set, questionId, {
        isProcessing: false,
        stage: "IDLE",
        message: "스트림이 예기치 않게 종료되었습니다.",
        error: "Unexpected stream end.",
      });
    }
  } catch (error) {
    if (controller.signal.aborted) return;
    setBatchQuestion(set, questionId, {
      isProcessing: false,
      stage: "IDLE",
      message: "오류가 발생했습니다.",
      error: error instanceof Error ? error.message : "Unknown error",
    });
  }
}
