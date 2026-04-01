export type QuestionCategory =
  | "MOTIVATION"
  | "EXPERIENCE"
  | "PROBLEM_SOLVING"
  | "COLLABORATION"
  | "GROWTH"
  | "CULTURE_FIT"
  | "TREND_INSIGHT"
  | "DEFAULT"

export const QUESTION_CATEGORY_LABELS: Record<QuestionCategory, string> = {
  MOTIVATION: "지원동기 및 목표",
  EXPERIENCE: "직무 경험 및 성과",
  PROBLEM_SOLVING: "문제 해결 및 도전",
  COLLABORATION: "협업 및 리더십",
  GROWTH: "CS/딥다이브 기반 성장",
  CULTURE_FIT: "조직문화 및 실행력",
  TREND_INSIGHT: "기술/산업 인사이트",
  DEFAULT: "기타",
}

export interface Mistranslation {
  id: string;
  issueType?: string;
  original: string;
  originalSentence?: string;
  translated: string;
  suggestion: string;
  severity?: "CRITICAL" | "WARNING";
  translatedSentence?: string;
  suggestedSentence?: string;
  reason?: string;
  originalStartIndex?: number | null;
  originalEndIndex?: number | null;
  startIndex?: number | null;
  endIndex?: number | null;
}

export interface AiReviewReport {
  summary: string;
  taggedOriginalText?: string;
  taggedWashedText?: string;
  overallScore?: number;
  technicalAccuracy?: number;
  readability?: number;
}

export interface WorkspaceCompletionPayload {
  draft: string;
  humanPatched?: string;
  sourceDraft?: string;
  usedFallbackDraft?: boolean;
  fallbackDraft?: string | null;
  warningMessage?: string | null;
  mistranslations?: Mistranslation[];
  aiReviewReport?: AiReviewReport | null;
}

export interface WorkspaceQuestion {
  id: string;
  dbId?: number;
  title: string;
  maxLength: number;
  lengthTarget?: number | null;
  userDirective: string;
  batchStrategyDirective?: string;
  content: string;
  washedKr: string;
  mistranslations: Mistranslation[];
  aiReviewReport: AiReviewReport | null;
  isCompleted: boolean;
  /** 유저가 직접 지정한 카테고리. null이면 AI 자동 분류 사용 */
  category: QuestionCategory | null;
}

export interface BatchPlanAssignment {
  questionId: number;
  questionTitle: string;
  primaryExperiences: string[];
  angle: string;
  focusDetails: string[];
  learningPoints: string[];
  avoidDetails: string[];
  reasoning: string;
  directivePrefix: string;
}

export interface BatchPlanResponse {
  coverageSummary: string;
  globalGuardrails: string[];
  model: string;
  assignments: BatchPlanAssignment[];
}

export interface TitleSuggestion {
  title: string;
  score: number;
  reason: string;
  recommended: boolean;
}

export interface TitleSuggestionResponse {
  currentTitle: string;
  candidates: TitleSuggestion[];
}

export interface ContextItem {
  id: string;
  experienceTitle: string;
  relevantPart: string;
  relevanceScore: number;
}

export type PipelineStage = "IDLE" | "RAG" | "DRAFT" | "WASH" | "PATCH" | "DONE";
