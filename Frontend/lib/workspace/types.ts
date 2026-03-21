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

export interface WorkspaceQuestion {
  id: string;
  dbId?: number;
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
