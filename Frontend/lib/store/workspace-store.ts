import { create } from "zustand";
import { toApiUrl } from "@/lib/network/api-base";
import { streamSse } from "@/lib/network/stream-sse";
import {
  playCompletionSound,
  prepareCompletionSound,
} from "@/lib/audio/completion-sound";

export interface Mistranslation {
  id: string;
  original: string;
  translated: string;
  suggestion: string;
  reason?: string;
  startIndex?: number | null;
  endIndex?: number | null;
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
  lengthTarget?: number | null;
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

export type PipelineStage = "IDLE" | "RAG" | "DRAFT" | "WASH" | "PATCH" | "DONE";

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
  
  // Transient processing state
  isProcessing: boolean;
  pipelineStage: PipelineStage;
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
  setPipelineStage: (stage: PipelineStage) => void;
  updateProgress: (message: string) => void;
  updateDraftIntermediate: (draft: string) => void;
  updateWashedIntermediate: (washed: string) => void;
  completeProcessing: (data: any) => void;
  setError: (error: string) => void;
  generateDraft: (options?: { useDirective?: boolean; lengthTarget?: number | null }) => Promise<void>;
  refineDraft: (directive: string, options?: { lengthTarget?: number | null }) => Promise<void>;
  rerunWash: () => Promise<void>;
  rerunPatch: () => Promise<void>;
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
  isCompleted: false,
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
    const loadingMessage = "Loading application data...";
    set({
      isProcessing: true,
      pipelineStage: "IDLE",
      progressMessage: loadingMessage,
      progressHistory: [loadingMessage],
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
        lengthTarget: null,
        userDirective: q.userDirective || "",
        content: q.content || "",
        washedKr: q.washedKr || "",
        mistranslations: q.mistranslations
          ? JSON.parse(q.mistranslations).map((m: any, idx: number) => ({
              ...m,
              id: m.id || `mis-${idx}`,
            }))
          : [],
        aiReviewReport: q.aiReview ? JSON.parse(q.aiReview) : null,
        isCompleted: !!q.completed || !!q.isCompleted,
      }));

      set({
        applicationId: id,
        company: data.companyName,
        position: data.position,
        aiInsight: data.aiInsight || "",
        companyResearch: data.companyResearch || "",
        questions: mappedQuestions.length > 0 ? mappedQuestions : [defaultQuestion],
        activeQuestionId: mappedQuestions.length > 0 ? mappedQuestions[0].id : "q-default",
        isProcessing: false,
        pipelineStage: "IDLE",
        progressMessage: "",
        progressHistory: [],
        processingError: null,
      });

      if (mappedQuestions.length > 0 && mappedQuestions[0].dbId) {
        get().fetchRAGContext(mappedQuestions[0].dbId, mappedQuestions[0].title);
      }
    } catch (err) {
      console.error(err);
      set({
        isProcessing: false,
        pipelineStage: "IDLE",
        progressMessage: "Error",
        processingError: "Failed to load application data.",
      });
    }
  },

  deleteActiveQuestion: async () => {
    const state = get();
    const activeQ = await ensureQuestionPersisted(state);
    if (!activeQ?.dbId) {
      state.setError("Could not find a valid question ID.");
      return;
    }

    if (!confirm("Delete this question?")) return;

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
      alert("Failed to delete the question.");
    }
  },

  toggleActiveQuestionCompletion: async () => {
    const state = get();
    const activeQ = state.questions.find((q) => q.id === state.activeQuestionId);
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
      set({ extractedContext: [] });
    }
  },

  startProcessing: () => {
    get().activeStreamController?.abort();

    const initialMessage = "Waiting for server response...";
    set({
      isProcessing: true,
      pipelineStage: "RAG",
      progressMessage: initialMessage,
      progressHistory: [initialMessage],
      processingError: null,
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
    const activeQ = state.questions.find((q) => q.id === state.activeQuestionId);
    if (!activeQ) return;

    const updatedMistranslations = data.mistranslations.map((m: any, idx: number) => ({
      ...m,
      id: `mis-${idx}`,
      suggestion: m.translated,
    }));

    set((state) => ({
      isProcessing: false,
      pipelineStage: "DONE",
      progressMessage: "Completed.",
      progressHistory: appendProgressHistory(state.progressHistory, "Completed."),
      processingError: null,
      activeStreamController: null,
      leftPanelTab: "report",
      questions: state.questions.map((q) =>
        q.id === state.activeQuestionId
          ? {
              ...q,
              washedKr: data.draft,
              mistranslations: updatedMistranslations,
              aiReviewReport: data.aiReviewReport,
            }
          : q
      ),
    }));

    void playCompletionSound();

    if (activeQ.dbId) {
      fetch(`/api/applications/questions/${activeQ.dbId}`, {
        method: "PUT",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          title: activeQ.title,
          maxLength: activeQ.maxLength,
          userDirective: activeQ.userDirective,
          content: activeQ.content,
          washedKr: data.draft,
          mistranslations: JSON.stringify(updatedMistranslations),
          aiReview: JSON.stringify(data.aiReviewReport),
        }),
      }).catch((err) => console.error("Initial wash save failed", err));
    }
  },

  setError: (error) =>
    set((state) => {
      const message = error || "An error occurred.";
      return {
        isProcessing: false,
        pipelineStage: "IDLE",
        progressMessage: message,
        progressHistory: appendProgressHistory(state.progressHistory, message),
        processingError: message,
        activeStreamController: null,
        leftPanelTab: "context",
      };
    }),

  generateDraft: async (options) => {
    const state = get();
    let activeQ;
    try {
      activeQ = await ensureQuestionPersisted(state);
    } catch (error) {
      console.error("Failed to persist question before draft generation", error);
      state.setError("An error occurred while saving the question.");
      return;
    }
    if (!activeQ?.dbId) {
      state.setError("Could not find a valid question ID.");
      return;
    }

    await prepareCompletionSound();
    state.startProcessing();
    const useDirective = options?.useDirective ?? true;
    const requestedLengthTarget = options?.lengthTarget ?? activeQ.lengthTarget;
    const normalizedTarget =
      requestedLengthTarget && requestedLengthTarget > 0
        ? Math.min(
            Math.max(1, Math.floor(requestedLengthTarget)),
            Math.max(1, activeQ.maxLength || requestedLengthTarget)
          )
        : null;
    const targetQuery = normalizedTarget ? `&targetChars=${normalizedTarget}` : "";

    await runWorkspaceSse({
      url: toApiUrl(
        `/api/workspace/stream/${activeQ.dbId}?useDirective=${useDirective}${targetQuery}`
      ),
      set,
      get,
      errorFallbackMessage: "An error occurred while generating the draft.",
    });
  },

  refineDraft: async (directive: string, options?: { lengthTarget?: number | null }) => {
    const state = get();
    let activeQ;
    try {
      activeQ = await ensureQuestionPersisted(state);
    } catch (error) {
      console.error("Failed to persist question before refinement", error);
      state.setError("An error occurred while saving the question.");
      return;
    }
    if (!activeQ?.dbId) {
      state.setError("Could not find a valid question ID.");
      return;
    }

    await prepareCompletionSound();
    state.startProcessing();
    state.updateActiveQuestion({ userDirective: directive });
    const requestedLengthTarget = options?.lengthTarget ?? activeQ.lengthTarget;
    const normalizedTarget =
      requestedLengthTarget && requestedLengthTarget > 0
        ? Math.min(
            Math.max(1, Math.floor(requestedLengthTarget)),
            Math.max(1, activeQ.maxLength || requestedLengthTarget)
          )
        : null;
    const targetQuery = normalizedTarget ? `&targetChars=${normalizedTarget}` : "";

    await runWorkspaceSse({
      url: toApiUrl(
        `/api/workspace/refine-stream/${activeQ.dbId}?directive=${encodeURIComponent(
          directive
        )}${targetQuery}`
      ),
      set,
      get,
      errorFallbackMessage: "An error occurred while refining the draft.",
    });
  },

  rerunWash: async () => {
    const state = get();
    const activeQ = state.questions.find((q) => q.id === state.activeQuestionId);
    if (!activeQ?.dbId) {
      state.setError("Could not find a valid question ID.");
      return;
    }

    await prepareCompletionSound();
    state.startProcessing();

    await runWorkspaceSse({
      url: toApiUrl(`/api/workspace/rewash-stream/${activeQ.dbId}`),
      set,
      get,
      errorFallbackMessage: "An error occurred while rerunning wash.",
    });
  },

  rerunPatch: async () => {
    const state = get();
    const activeQ = state.questions.find((q) => q.id === state.activeQuestionId);
    if (!activeQ?.dbId) {
      state.setError("Could not find a valid question ID.");
      return;
    }

    await prepareCompletionSound();
    state.startProcessing();

    await runWorkspaceSse({
      url: toApiUrl(`/api/workspace/repatch-stream/${activeQ.dbId}`),
      set,
      get,
      errorFallbackMessage: "An error occurred while rerunning patch.",
    });
  }}));

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

function parsePipelineStage(raw: string): PipelineStage | null {
  const stage = parseSseMessage(raw).trim().toUpperCase();
  if (stage === "RAG" || stage === "DRAFT" || stage === "WASH" || stage === "PATCH" || stage === "DONE") {
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
    normalized.includes("analysis")
  ) {
    return "PATCH";
  }

  if (
    normalized.includes("wash") ||
    normalized.includes("translate") ||
    normalized.includes("translation")
  ) {
    return current === "DONE" ? current : "WASH";
  }

  if (normalized.includes("draft") || normalized.includes("writing")) {
    if (current === "PATCH" || current === "DONE") {
      return current;
    }
    return "DRAFT";
  }

  if (normalized.includes("rag") || normalized.includes("context")) {
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
        }
      },
    });

    const state = get();
    if (state.isProcessing && !state.processingError) {
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

function normalizeSseError(error: unknown, fallbackMessage: string) {
  if (error instanceof DOMException && error.name === "AbortError") {
    return fallbackMessage;
  }

  if (error instanceof Error && error.message) {
    return "An error occurred.";
  }

  return "An error occurred.";
}


