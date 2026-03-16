import { create } from "zustand";
import { toApiUrl } from "@/lib/network/api-base";
import { streamSse } from "@/lib/network/stream-sse";

export interface Mistranslation {
  id: string;
  original: string;
  translated: string;
  suggestion: string;
  severity: "low" | "high";
  reason?: string;
}

export interface AiReviewReport {
  summary: string;
  overallScore: number;
  technicalAccuracy: number;
  readability: number;
}

export interface WorkspaceQuestion {
  id: string;
  dbId?: number; // Backend DB ID
  title: string;
  maxLength: number;
  userDirective: string;
  content: string;
  washedKr: string;
  mistranslations: Mistranslation[];
  aiReviewReport: AiReviewReport | null;
  isCompleted: boolean;
}

export interface ContextItem {
  id: string;
  experienceTitle: string;
  relevantPart: string;
  relevanceScore: number;
}

interface WorkspaceState {
  // Global context
  applicationId: string | null;
  company: string;
  position: string;
  
  // Questions management
  questions: WorkspaceQuestion[];
  activeQuestionId: string | null;
  extractedContext: ContextItem[];
  
  // UI State
  leftPanelTab: "context" | "report";
  hoveredMistranslationId: string | null;
  
  // Transient processing state
  isProcessing: boolean;
  progressMessage: string;
  progressHistory: string[];
  processingError: string | null;
  activeStreamController: AbortController | null;
  saveTimeout: any;

  // Actions
  setCompany: (company: string) => void;
  setPosition: (position: string) => void;
  
  setQuestions: (questions: WorkspaceQuestion[]) => void;
  setActiveQuestionId: (id: string) => void;
  setLeftPanelTab: (tab: "context" | "report") => void;
  setHoveredMistranslationId: (id: string | null) => void;
  addQuestion: () => void;
  updateActiveQuestion: (updates: Partial<WorkspaceQuestion>) => void;
  applySuggestion: (mistranslationId: string) => void;
  updateMistranslationSuggestion: (mistranslationId: string, suggestion: string) => void;
  dismissMistranslation: (mistranslationId: string) => void;
  
  fetchApplicationData: (id: string) => Promise<void>;
  fetchRAGContext: (questionId: number, query?: string) => Promise<void>;
  
  deleteActiveQuestion: () => Promise<void>;
  toggleActiveQuestionCompletion: () => Promise<void>;
  
  startProcessing: () => void;
  updateProgress: (message: string) => void;
  updateDraftIntermediate: (draft: string) => void;
  updateWashedIntermediate: (washed: string) => void;
  completeProcessing: (data: any) => void;
  setError: (error: string) => void;
  generateDraft: () => Promise<void>;
  refineDraft: (directive: string) => Promise<void>;
}

const defaultQuestion: WorkspaceQuestion = {
  id: "q-default",
  title: "참여했던 도전적인 프로젝트를 설명하고 기술적 어려움을 어떻게 해결했는지 서술하세요.",
  maxLength: 1000,
  content: "",
  washedKr: "",
  mistranslations: [],
  aiReviewReport: null,
  userDirective: "",
  isCompleted: false,
};

export const useWorkspaceStore = create<WorkspaceState>((set, get) => ({
  applicationId: null,
  company: "",
  position: "",
  questions: [defaultQuestion],
  activeQuestionId: "q-default",
  extractedContext: [],
  leftPanelTab: "context",
  hoveredMistranslationId: null,
  isProcessing: false,
  progressMessage: "",
  progressHistory: [],
  processingError: null,
  activeStreamController: null,
  saveTimeout: null,

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

    // Sync to backend if dbId exists and relevant fields changed
    const activeQuestion = updatedQuestions.find(q => q.id === state.activeQuestionId);
    
    if (activeQuestion?.dbId && (
      updates.title !== undefined || 
      updates.maxLength !== undefined ||
      updates.content !== undefined || 
      updates.washedKr !== undefined || 
      updates.mistranslations !== undefined || 
      updates.userDirective !== undefined ||
      updates.isCompleted !== undefined
    )) {
      // Debounce sync and RAG fetch
      if (get().saveTimeout) {
        clearTimeout(get().saveTimeout);
      }

      const timeout = setTimeout(() => {
        fetch(`/api/applications/questions/${activeQuestion.dbId}`, {
          method: 'PUT',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({
            title: activeQuestion.title,
            maxLength: activeQuestion.maxLength,
            userDirective: activeQuestion.userDirective,
            content: activeQuestion.content,
            washedKr: activeQuestion.washedKr,
            mistranslations: JSON.stringify(activeQuestion.mistranslations),
            aiReview: JSON.stringify(activeQuestion.aiReviewReport),
            isCompleted: activeQuestion.isCompleted
          })
        })
        .then(() => {
          // If title was updated, refresh RAG context using the NEW title for real-time relevance
          if (updates.title !== undefined) {
             get().fetchRAGContext(activeQuestion.dbId!, activeQuestion.title);
          }
        })
        .catch(err => console.error("Auto-save failed", err));
      }, 300); // 300ms debounce for title/RAG sync

      set({ saveTimeout: timeout });
    }
  },
  
  applySuggestion: (mistranslationId) => {
    const state = get();
    const activeQ = state.questions.find(q => q.id === state.activeQuestionId);
    if (!activeQ) return;
    const mistranslation = activeQ.mistranslations.find(m => m.id === mistranslationId);
    if (!mistranslation) return;

    const normalizedSuggestion = mistranslation.suggestion?.trim() || "";
    const normalizedTranslated = mistranslation.translated?.trim() || "";
    
    // If suggestion is identical to the one that was flagged (user didn't change it),
    // it means the user wants to revert to the original draft version.
    const finalSuggestion = normalizedSuggestion === normalizedTranslated
      ? mistranslation.original 
      : normalizedSuggestion;
    
    // Replace the translated text with the final suggestion
    let searchTerms = [mistranslation.translated];
    
    // Also try variant without spaces for robustness in matching
    if (mistranslation.translated.includes(" ")) {
       searchTerms.push(mistranslation.translated.replace(/\s+/g, ""));
    }
    
    let newWashedKr = activeQ.washedKr;
    let found = false;

    // Try each search term variant
    for (const term of searchTerms) {
      if (newWashedKr.includes(term)) {
        newWashedKr = newWashedKr.split(term).join(finalSuggestion);
        found = true;
        break;
      }
    }
    
    // Remove the mistranslation from the list after applying
    const newMistranslations = activeQ.mistranslations.filter(m => m.id !== mistranslationId);
    
    state.updateActiveQuestion({ 
      washedKr: newWashedKr,
      mistranslations: newMistranslations
    });
  },

  updateMistranslationSuggestion: (mistranslationId, suggestion) => {
    const state = get();
    const activeQ = state.questions.find(q => q.id === state.activeQuestionId);
    if (!activeQ) return;

    const updatedMistranslations = activeQ.mistranslations.map(m => 
      m.id === mistranslationId ? { ...m, suggestion } : m
    );

    state.updateActiveQuestion({ mistranslations: updatedMistranslations });
  },

  dismissMistranslation: (mistranslationId) => {
    const state = get();
    const activeQ = state.questions.find(q => q.id === state.activeQuestionId);
    if (!activeQ) return;
    
    const newMistranslations = activeQ.mistranslations.filter(m => m.id !== mistranslationId);
    state.updateActiveQuestion({ mistranslations: newMistranslations });
  },

  fetchApplicationData: async (id) => {
    set({
      isProcessing: true,
      progressMessage: "데이터를 불러오는 중...",
      progressHistory: ["데이터를 불러오는 중..."],
      processingError: null,
    });
    try {
      const response = await fetch(`/api/applications/${id}`);
      if (!response.ok) throw new Error("Failed to fetch application");
      const data = await response.json();
      
      const mappedQuestions: WorkspaceQuestion[] = data.questions.map((q: any) => ({
        id: `q-${q.id}`,
        dbId: q.id,
        title: q.title,
        maxLength: q.maxLength,
        userDirective: q.userDirective || "",
        content: q.content || "",
        washedKr: q.washedKr || "",
        mistranslations: q.mistranslations ? JSON.parse(q.mistranslations).map((m: any, idx: number) => ({
          ...m,
          id: m.id || `mis-${idx}`
        })) : [],
        aiReviewReport: q.aiReview ? JSON.parse(q.aiReview) : null,
        isCompleted: !!q.completed || !!q.isCompleted
      }));

      set({
        applicationId: id,
        company: data.companyName,
        position: data.position,
        questions: mappedQuestions.length > 0 ? mappedQuestions : [defaultQuestion],
        activeQuestionId: mappedQuestions.length > 0 ? mappedQuestions[0].id : "q-default",
        isProcessing: false,
        progressMessage: "",
        progressHistory: [],
        processingError: null,
      });

      // Fetch RAG context for the first question
      if (mappedQuestions.length > 0 && mappedQuestions[0].dbId) {
        get().fetchRAGContext(mappedQuestions[0].dbId, mappedQuestions[0].title);
      }
    } catch (err) {
      console.error(err);
      set({ isProcessing: false, progressMessage: "오류 발생" });
    }
  },

  deleteActiveQuestion: async () => {
    const state = get();
    const activeQ = state.questions.find((q) => q.id === state.activeQuestionId);
    if (!activeQ?.dbId) {
      state.setError("저장된 문항 ID를 찾지 못했습니다.");
      return;
    }

    if (!confirm("정말 이 문항을 삭제하시겠습니까?")) return;

    try {
      const response = await fetch(`/api/applications/questions/${activeQ.dbId}`, {
        method: "DELETE"
      });
      if (!response.ok) throw new Error("Failed to delete question");

      const remainingQuestions = state.questions.filter(q => q.id !== state.activeQuestionId);
      set({ 
        questions: remainingQuestions,
        activeQuestionId: remainingQuestions.length > 0 ? remainingQuestions[0].id : null
      });
    } catch (err) {
      console.error(err);
      alert("문항 삭제에 실패했습니다.");
    }
  },

  toggleActiveQuestionCompletion: async () => {
    const state = get();
    const activeQ = state.questions.find(q => q.id === state.activeQuestionId);
    if (!activeQ) return;

    const newStatus = !activeQ.isCompleted;
    state.updateActiveQuestion({ isCompleted: newStatus });
  },

  fetchRAGContext: async (questionId, query) => {
    try {
      const url = query 
        ? `/api/applications/questions/${questionId}/rag?query=${encodeURIComponent(query)}`
        : `/api/applications/questions/${questionId}/rag`;
        
      const response = await fetch(url);
      if (!response.ok) throw new Error("Failed to fetch RAG context");
      const data = await response.json();
      set({ extractedContext: data.extractedContext || [] });
    } catch (err) {
      console.error("RAG fetch failed", err);
      // Keep existing context or clear it? Clearing is safer to avoid confusion
      set({ extractedContext: [] });
    }
  },

  startProcessing: () => {
    get().activeStreamController?.abort();

    const initialMessage = "서버 연결 대기 중...";
    set({
      isProcessing: true,
      progressMessage: "서버 연결 대기 중...",
      progressHistory: [initialMessage],
      processingError: null,
      leftPanelTab: "context",
      activeStreamController: null,
      progressMessage: initialMessage,
    });
  },
    
  updateProgress: (progressMessage) =>
    set((state) => ({
      progressMessage,
      progressHistory: appendProgressHistory(state.progressHistory, progressMessage),
    })),
  
  updateDraftIntermediate: (draft) => set((state) => ({
    questions: state.questions.map(q => 
      q.id === state.activeQuestionId ? { ...q, content: draft } : q
    )
  })),

  updateWashedIntermediate: (washedKr) => set((state) => ({
    questions: state.questions.map(q => 
      q.id === state.activeQuestionId ? { ...q, washedKr } : q
    )
  })),

  completeProcessing: (data) => {
    const state = get();
    const activeQ = state.questions.find(q => q.id === state.activeQuestionId);
    if (!activeQ) return;

    const updatedMistranslations = data.mistranslations.map((m: any, idx: number) => ({
      ...m,
      id: `mis-${idx}`,
      suggestion: m.translated, // Pre-fill with translation for manual editing
    }));

    // Update frontend state
    set((state) => ({
      isProcessing: false,
      progressMessage: "완료!",
      progressHistory: appendProgressHistory(state.progressHistory, "?꾨즺!"),
      processingError: null,
      activeStreamController: null,
      leftPanelTab: "report",
      questions: state.questions.map(q => 
        q.id === state.activeQuestionId ? { 
          ...q, 
          washedKr: data.draft,
          mistranslations: updatedMistranslations,
          aiReviewReport: data.aiReviewReport
        } : q
      )
    }));

    // IMMEDIATELY sync to backend
    if (activeQ.dbId) {
      fetch(`/api/applications/questions/${activeQ.dbId}`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          title: activeQ.title,
          maxLength: activeQ.maxLength,
          userDirective: activeQ.userDirective,
          content: activeQ.content,
          washedKr: data.draft,
          mistranslations: JSON.stringify(updatedMistranslations),
          aiReview: JSON.stringify(data.aiReviewReport)
        })
      }).catch(err => console.error("Initial wash save failed", err));
    }
  },

  setError: (error) =>
    set((state) => ({
      isProcessing: false,
      progressMessage: `오류: ${error}`,
      progressHistory: appendProgressHistory(
        state.progressHistory,
        `오류: ${error}`
      ),
      processingError: error,
      activeStreamController: null,
      leftPanelTab: "context",
      progressMessage: `오류: ${error}`,
    })),

  generateDraft: async () => {
    const state = get();
    const activeQ = state.questions.find((q) => q.id === state.activeQuestionId);
    if (!activeQ?.dbId) {
      state.setError("저장된 문항 ID를 찾지 못했습니다.");
      return;
    }

    state.startProcessing();

    await runWorkspaceSse({
      url: toApiUrl(`/api/workspace/stream/${activeQ.dbId}`),
      set,
      get,
      errorFallbackMessage: "초안 생성 중 오류가 발생했습니다.",
    });
    return;

    const eventSource = new EventSource(
      `/api/workspace/stream/${activeQ.dbId}`
    );

    eventSource.onopen = () => {
      console.log("🟢 SSE Connection Opened");
      get().updateProgress("📡 파이프라인 연결됨...");
    };

    eventSource.addEventListener("progress", (event) => {
      console.log("SSE Progress:", event.data);
      get().updateProgress(parseSseMessage(event.data));
    });

    eventSource.addEventListener("draft_intermediate", (event) => {
      get().updateDraftIntermediate(parseSseMessage(event.data));
    });

    eventSource.addEventListener("washed_intermediate", (event) => {
      get().updateWashedIntermediate(parseSseMessage(event.data));
    });

    eventSource.addEventListener("complete", (event) => {
      const data = parseSseJson(event.data);
      if (!data) {
        state.setError("SSE 완료 응답을 해석하지 못했습니다.");
        eventSource.close();
        return;
      }
      get().completeProcessing(data);
      eventSource.close();
    });

    eventSource.addEventListener("error", (event: MessageEvent<string>) => {
      const data = parseSseJson(event.data);
      state.setError(
        data?.message ||
          parseSseMessage(event.data) ||
          "초안 생성 중 오류가 발생했습니다."
      );
      eventSource.close();
    });

    eventSource.onerror = () => {
      state.setError("서버와의 연결이 끊어졌습니다.");
      eventSource.close();
    };
  },

  refineDraft: async (directive: string) => {
    const state = get();
    const activeQ = state.questions.find(q => q.id === state.activeQuestionId);
    if (!activeQ || !activeQ.dbId) return;

    state.startProcessing();
    state.updateActiveQuestion({ userDirective: directive });

    await runWorkspaceSse({
      url: toApiUrl(
        `/api/workspace/refine-stream/${activeQ.dbId}?directive=${encodeURIComponent(
          directive
        )}`
      ),
      set,
      get,
      errorFallbackMessage: "수정 중 오류가 발생했습니다.",
    });
    return;

    const eventSource = new EventSource(
      `/api/workspace/refine-stream/${activeQ.dbId}?directive=${encodeURIComponent(directive)}`
    );

    eventSource.onopen = () => {
      console.log("SSE Refine Connection Opened");
      get().updateProgress("리터칭 파이프라인 연결됨...");
    };

    eventSource.addEventListener("progress", (event) => {
      console.log("SSE Refine Progress:", event.data);
      get().updateProgress(parseSseMessage(event.data));
    });

    eventSource.addEventListener("draft_intermediate", (event) => {
      get().updateDraftIntermediate(parseSseMessage(event.data));
    });

    eventSource.addEventListener("washed_intermediate", (event) => {
      get().updateWashedIntermediate(parseSseMessage(event.data));
    });

    eventSource.addEventListener("complete", (event) => {
      const data = parseSseJson(event.data);
      if (!data) {
        state.setError("SSE 완료 응답을 해석하지 못했습니다.");
        eventSource.close();
        return;
      }
      get().completeProcessing(data);
      eventSource.close();
    });

    eventSource.addEventListener("error", (event: MessageEvent<string>) => {
      const data = parseSseJson(event.data);
      state.setError(
        data?.message ||
          parseSseMessage(event.data) ||
          "수정 중 오류가 발생했습니다."
      );
      eventSource.close();
    });

    eventSource.onerror = () => {
      state.setError("서버와의 연결이 끊어졌습니다.");
      eventSource.close();
    };
  }
}));

function parseSseMessage(raw: string) {
  if (!raw) {
    return "";
  }

  try {
    const parsed = JSON.parse(raw);
    return typeof parsed === "string" ? parsed : raw;
  } catch {
    return raw;
  }
}

function parseSseJson(raw: string) {
  try {
    return JSON.parse(raw);
  } catch {
    return null;
  }
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
  set: Parameters<typeof useWorkspaceStore.setState>[0];
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
        get().updateProgress("파이프라인 연결됨...");
      },
      onEvent: ({ event, data }) => {
        const eventName = event.toLowerCase();

        if (eventName === "progress") {
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
            get().setError("SSE 완료 응답을 해석하지 못했습니다.");
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
        }
      },
    });

    const state = get();
    if (state.isProcessing && !state.processingError) {
      state.setError("스트림이 예기치 않게 종료되었습니다.");
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

function normalizeSseError(error: unknown, fallbackMessage: string) {
  if (error instanceof DOMException && error.name === "AbortError") {
    return fallbackMessage;
  }

  if (error instanceof Error && error.message) {
    return error.message;
  }

  return fallbackMessage;
}
