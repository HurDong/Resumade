export type QuestionCategory =
  | "MOTIVATION"
  | "EXPERIENCE"
  | "PROBLEM_SOLVING"
  | "COLLABORATION"
  | "PERSONAL_GROWTH"
  | "CULTURE_FIT"
  | "TREND_INSIGHT"
  | "DEFAULT"

export const QUESTION_CATEGORY_LABELS: Record<QuestionCategory, string> = {
  MOTIVATION: "지원동기 및 목표",
  EXPERIENCE: "직무 경험 및 성과",
  PROBLEM_SOLVING: "문제 해결 및 도전",
  COLLABORATION: "협업 및 리더십",
  PERSONAL_GROWTH: "성장과정 및 가치관",
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
  /** 성장과정 문항에서 선택된 인생 서사 ID 목록 */
  selectedStoryIds?: number[];
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
  category: QuestionCategory | null;
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
  experienceId?: number;
  facetId?: number;
  experienceTitle: string;
  facetTitle?: string;
  relevantPart: string;
  relevanceScore: number;
}

export type PipelineStage = "IDLE" | "RAG" | "DRAFT" | "WASH" | "PATCH" | "DONE";

export type StoryType =
  | "TURNING_POINT"
  | "VALUE"
  | "ENVIRONMENT"
  | "INFLUENCE"
  | "FAILURE_RECOVERY"
  | "MILESTONE";

export const STORY_TYPE_LABELS: Record<StoryType, string> = {
  TURNING_POINT: "전환점",
  VALUE: "가치관",
  ENVIRONMENT: "성장 환경",
  INFLUENCE: "영향받은 인물/경험",
  FAILURE_RECOVERY: "실패와 극복",
  MILESTONE: "인생 이정표",
};

export interface PersonalStory {
  id: number;
  type: StoryType;
  period: string;
  content: string;
  keywords: string[];
}

export interface StoryUpsertRequest {
  type: StoryType;
  period: string;
  content: string;
  keywords: string[];
}
