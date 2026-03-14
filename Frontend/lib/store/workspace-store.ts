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
  content: string;
  washedKr: string;
  mistranslations: Mistranslation[];
  aiReviewReport: AiReviewReport | null;
}

interface WorkspaceState {
  // Global context
  applicationId: string | null;
  company: string;
  position: string;
  
  // Questions management
  questions: WorkspaceQuestion[];
  activeQuestionId: string | null;
  
  // Transient processing state
  isProcessing: boolean;
  progressMessage: string;

  // Actions
  setCompany: (company: string) => void;
  setPosition: (position: string) => void;
  
  setQuestions: (questions: WorkspaceQuestion[]) => void;
  setActiveQuestionId: (id: string) => void;
  addQuestion: () => void;
  updateActiveQuestion: (updates: Partial<WorkspaceQuestion>) => void;
  
  fetchApplicationData: (id: string) => Promise<void>;
  
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
  isProcessing: false,
  progressMessage: "",

  setCompany: (company) => set({ company }),
  setPosition: (position) => set({ position }),

  setQuestions: (questions) => set({ questions }),
  setActiveQuestionId: (activeQuestionId) => set({ activeQuestionId }),
  
  addQuestion: () => set((state) => {
    const newId = `q-${Math.random().toString(36).substr(2, 9)}`;
    const newQuestion: WorkspaceQuestion = {
      ...defaultQuestion,
      id: newId,
      title: "새로운 문항을 입력하세요.",
      content: "",
      washedKr: "",
    };
    return {
      questions: [...state.questions, newQuestion],
      activeQuestionId: newId,
    };
  }),

  updateActiveQuestion: (updates) => {
    const state = get();
    const updatedQuestions = state.questions.map(q => 
      q.id === state.activeQuestionId ? { ...q, ...updates } : q
    );
    set({ questions: updatedQuestions });

    // Sync to backend if dbId exists and content changed
    const activeQuestion = updatedQuestions.find(q => q.id === state.activeQuestionId);
    if (activeQuestion?.dbId && updates.content !== undefined) {
      fetch(`/api/applications/questions/${activeQuestion.dbId}`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          title: activeQuestion.title,
          maxLength: activeQuestion.maxLength,
          content: activeQuestion.content
        })
      }).catch(err => console.error("Auto-save failed", err));
    }
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
        content: q.content || "",
        washedKr: q.washedKr || "",
        mistranslations: q.mistranslations ? JSON.parse(q.mistranslations) : [],
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
    } catch (err) {
      console.error(err);
      set({ isProcessing: false, progressMessage: "오류 발생" });
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

  completeProcessing: (data) =>
    set((state) => ({
      isProcessing: false,
      progressMessage: "완료!",
      questions: state.questions.map(q => 
        q.id === state.activeQuestionId ? { 
          ...q, 
          washedKr: data.draft,
          mistranslations: data.mistranslations.map((m: any, idx: number) => ({
            ...m,
            id: `mis-${idx}`,
          })),
          aiReviewReport: data.aiReviewReport
        } : q
      )
    })),

  setError: (error) =>
    set({
      isProcessing: false,
      progressMessage: `오류: ${error}`,
    }),
}));
