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
