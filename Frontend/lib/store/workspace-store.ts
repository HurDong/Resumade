import { create } from "zustand";

export interface Mistranslation {
  id: string;
  original: string;
  translated: string;
  suggestion: string;
  severity: "low" | "high";
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
  dismissMistranslation: (mistranslationId: string) => void;
  
  fetchApplicationData: (id: string) => Promise<void>;
  fetchRAGContext: (questionId: number) => Promise<void>;
  
  startProcessing: () => void;
  updateProgress: (message: string) => void;
  updateDraftIntermediate: (draft: string) => void;
  updateWashedIntermediate: (washed: string) => void;
  completeProcessing: (data: any) => void;
  setError: (error: string) => void;
}

const defaultQuestion: WorkspaceQuestion = {
  id: "q-default",
  title: "참여했던 도전적인 프로젝트를 설명하고 기술적 어려움을 어떻게 해결했는지 서술하세요.",
  maxLength: 1000,
  content: "",
  washedKr: "",
  mistranslations: [],
  aiReviewReport: null,
};

export const useWorkspaceStore = create<WorkspaceState>((set, get) => ({
  applicationId: null,
  company: "로딩 중...",
  position: "",
  questions: [defaultQuestion],
  activeQuestionId: "q-default",
  extractedContext: [],
  leftPanelTab: "context",
  hoveredMistranslationId: null,
  isProcessing: false,
  progressMessage: "",

  setCompany: (company) => set({ company }),
  setPosition: (position) => set({ position }),

  setQuestions: (questions) => set({ questions }),
  setActiveQuestionId: (activeQuestionId) => {
    set({ activeQuestionId });
    // Trigger RAG fetch when changing question
    const state = get();
    const activeQ = state.questions.find(q => q.id === activeQuestionId);
    if (activeQ?.dbId) {
      state.fetchRAGContext(activeQ.dbId);
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
      title: "새로운 문항을 입력하세요.",
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
          maxLength: newQuestion.maxLength
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
    if (activeQuestion?.dbId && (updates.content !== undefined || updates.washedKr !== undefined || updates.mistranslations !== undefined || updates.userDirective !== undefined)) {
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
          aiReview: JSON.stringify(activeQuestion.aiReviewReport)
        })
      }).catch(err => console.error("Auto-save failed", err));
    }
  },
  
  applySuggestion: (mistranslationId) => {
    const state = get();
    const activeQ = state.questions.find(q => q.id === state.activeQuestionId);
    if (!activeQ) return;
    
    const mistranslation = activeQ.mistranslations.find(m => m.id === mistranslationId);
    if (!mistranslation) return;
    
    // Replace the translated text with the suggestion
    let searchTerms = [mistranslation.translated];
    
    // If the search term contains slashes or common separators, add variants as fallbacks
    if (mistranslation.translated.includes("/")) {
      const variants = mistranslation.translated.split("/").map(v => v.trim()).filter(v => v.length > 0);
      searchTerms = [...searchTerms, ...variants];
    }
    
    // Smart extraction for suggestion if it contains AI reasoning (e.g., "Use 'Word' instead")
    let finalSuggestion = mistranslation.suggestion;
    if (finalSuggestion.length > 30) {
      const quoted = finalSuggestion.match(/['"“](.*?)['"”]/);
      if (quoted && quoted[1].length < 30) {
        finalSuggestion = quoted[1];
      }
    }
    
    let newWashedKr = activeQ.washedKr;
    let found = false;

    // Try each search term variant
    for (const term of searchTerms) {
      if (newWashedKr.includes(term)) {
        // Simple literal replacement for all occurrences
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

  dismissMistranslation: (mistranslationId) => {
    const state = get();
    const activeQ = state.questions.find(q => q.id === state.activeQuestionId);
    if (!activeQ) return;
    
    const newMistranslations = activeQ.mistranslations.filter(m => m.id !== mistranslationId);
    state.updateActiveQuestion({ mistranslations: newMistranslations });
  },

  fetchApplicationData: async (id) => {
    set({ isProcessing: true, progressMessage: "데이터를 불러오는 중..." });
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
      }));

      set({
        applicationId: id,
        company: data.companyName,
        position: data.position,
        questions: mappedQuestions.length > 0 ? mappedQuestions : [defaultQuestion],
        activeQuestionId: mappedQuestions.length > 0 ? mappedQuestions[0].id : "q-default",
        isProcessing: false,
        progressMessage: ""
      });

      // Fetch RAG context for the first question
      if (mappedQuestions.length > 0 && mappedQuestions[0].dbId) {
        get().fetchRAGContext(mappedQuestions[0].dbId);
      }
    } catch (err) {
      console.error(err);
      set({ isProcessing: false, progressMessage: "오류 발생" });
    }
  },

  fetchRAGContext: async (questionId) => {
    try {
      const response = await fetch(`/api/applications/questions/${questionId}/rag`);
      if (!response.ok) throw new Error("Failed to fetch RAG context");
      const data = await response.json();
      set({ extractedContext: data.extractedContext || [] });
    } catch (err) {
      console.error("RAG fetch failed", err);
      // Keep existing context or clear it? Clearing is safer to avoid confusion
      set({ extractedContext: [] });
    }
  },

  startProcessing: () =>
    set({
      isProcessing: true,
      progressMessage: "준비 중...",
    }),
    
  updateProgress: (progressMessage) => set({ progressMessage }),
  
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
    }));

    // Update frontend state
    set((state) => ({
      isProcessing: false,
      progressMessage: "완료!",
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
    set({
      isProcessing: false,
      progressMessage: `오류: ${error}`,
    }),
}));
