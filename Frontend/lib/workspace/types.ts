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
}

export interface AiReviewReport {
  summary: string;
  overallScore?: number;
  technicalAccuracy?: number;
  readability?: number;
  qualityReport?: DraftAuthenticityReport | null;
}

export interface DraftAuthenticityReport {
  experienceDensityScore: number;
  authenticityRiskScore: number;
  interviewDefensibilityScore: number;
  riskFlags: string[];
  factGaps: string[];
  verificationQuestions: string[];
  rewriteDirective?: string;
  summary: string;
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
  qualityReport?: DraftAuthenticityReport | null;
  lengthOk?: boolean;
  finalLength?: number;
  minTarget?: number;
  maxLength?: number;
  preferredMax?: number;
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
  category: QuestionCategory | null;
}

export interface BatchPlanResponse {
  coverageSummary: string;
  globalGuardrails: string[];
  model: string;
  assignments: BatchPlanAssignment[];
}

export interface StrategyCardText {
  title: string;
  detail: string;
}

export interface StrategyCardExperiencePlan {
  primaryExperiences: string[];
  facets: string[];
  proofPoints: string[];
  learningPoints: string[];
}

export interface StrategyCardFitConnection {
  companyAnchor: string;
  domainBridge: string;
  lexicon: string[];
  evidence: string[];
}

export interface StrategyCardParagraphPlan {
  paragraph: number;
  role: string;
  contents: string[];
}

export interface QuestionStrategyCard {
  questionId: number;
  questionTitle: string;
  category: QuestionCategory;
  summary: string;
  intent: StrategyCardText;
  primaryClaim: string;
  experiencePlan: StrategyCardExperiencePlan;
  fitConnection: StrategyCardFitConnection;
  paragraphPlan: StrategyCardParagraphPlan[];
  warnings: string[];
  draftDirective: string;
  confidenceNotes: string[];
}

export interface QuestionStrategyCardRecord {
  id: number | null;
  applicationId: number;
  questionId: number;
  card: QuestionStrategyCard;
  directivePrefix: string;
  reviewNote?: string | null;
  sourceType: "SINGLE" | "BATCH" | string;
  modelName?: string | null;
  fitProfileId?: number | null;
  createdAt?: string | null;
  updatedAt?: string | null;
}

export interface QuestionStrategyCardCandidate {
  uuid: string;
  applicationId: number;
  sourceType: "SINGLE" | "BATCH" | string;
  modelName?: string | null;
  expiresAt?: string | null;
  cards: QuestionStrategyCardRecord[];
}

export interface QuestionStrategyCardBatchResponse {
  applicationId: number;
  sourceType: "BATCH" | string;
  modelName?: string | null;
  cards: QuestionStrategyCardRecord[];
}

export interface TitleSuggestion {
  title: string;
  score: number;
  reason: string;
  pattern?: string | null;
  risk?: string | null;
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

export type PipelineStage = "IDLE" | "ANALYSIS" | "RAG" | "DRAFT" | "WASH" | "PATCH" | "DONE";

export interface PersonalStory {
  id: number;
  content: string;
}

export interface StoryUpsertRequest {
  content: string;
}
