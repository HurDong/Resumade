"use client"

import { useState, useEffect, useRef } from "react"
import { motion, useMotionValue, useMotionTemplate } from "framer-motion"
import confetti from "canvas-confetti"
import {
  Settings,
  Building2,
  Plus,
  FileText,
  Sparkles,
  Loader2,
  MessageSquare,
  Wand2,
  AlertTriangle,
  Lightbulb,
  Copy,
  ArrowRight,
  TrendingUp,
  Check,
  Trash2,
  CheckCircle2,
  ChevronDown,
  Zap,
  X,
  CheckCheck,
  Calendar,
  User,
  Code2,
  BarChart3,
} from "lucide-react"
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from "@/components/ui/card"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Textarea } from "@/components/ui/textarea"
import { Progress } from "@/components/ui/progress"
import { ScrollArea } from "@/components/ui/scroll-area"
import { Separator } from "@/components/ui/separator"
import { Input } from "@/components/ui/input"
import { Tabs, TabsList, TabsTrigger } from "@/components/ui/tabs"
import { type PipelineStage, useWorkspaceStore, type QuestionBatchState } from "@/lib/store/workspace-store"
import { parseCompanyResearch } from "@/lib/application-intelligence"
import { countResumeCharacters } from "@/lib/text/resume-character-count"
import { Tooltip, TooltipContent, TooltipProvider, TooltipTrigger } from "@/components/ui/tooltip"
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog"
import { ApplicationInfoQuickCopyDialog } from "@/components/application-info/application-info-quick-copy-dialog"
import { BatchPlanDialog } from "@/components/workspace-editor/batch-plan-dialog"
import type { TitleSuggestion } from "@/lib/workspace/types"
import { getDisplayedWashedText } from "@/lib/workspace/translation-panel-helpers"

// Helper for standard character length (including spaces)
const getLength = (str: string) => {
  return countResumeCharacters(str);
};

type DirectiveFieldKey = "tone" | "focus" | "keep" | "avoid" | "note";

interface DirectiveFields {
  tone: string;
  focus: string;
  keep: string;
  avoid: string;
  note: string;
}

const STRUCTURED_DIRECTIVE_CONFIG: { key: Exclude<DirectiveFieldKey, "note">; label: string; placeholder: string }[] = [
  { key: "tone", label: "톤 / 어조", placeholder: "자신감 있게, 차분하게, 단호하게" },
  { key: "focus", label: "강조 포인트", placeholder: "리더십, 기여도, 수치 강조" },
  { key: "keep", label: "반드시 유지", placeholder: "특정 수치, 용어, 핵심 스토리 보존" },
  { key: "avoid", label: "제외", placeholder: "열정, 팀플레이어 같은 추상어 제거" },
];

const DEFAULT_DIRECTIVE_FIELDS: DirectiveFields = {
  tone: "",
  focus: "",
  keep: "",
  avoid: "",
  note: "",
};

const DIRECTIVE_PRESETS: Array<{ key: DirectiveFieldKey; label: string; value: string }> = [
  { key: "tone", label: "담백하게", value: "More grounded and restrained" },
  { key: "tone", label: "자신감 있게", value: "More confident without sounding exaggerated" },
  { key: "focus", label: "성과 강조", value: "Emphasize measurable outcomes and impact" },
  { key: "focus", label: "문제 해결", value: "Emphasize problem solving and decision making" },
  { key: "keep", label: "수치 유지", value: "Preserve all metrics and concrete technical details" },
  { key: "avoid", label: "추상어 줄이기", value: "Remove vague buzzwords and generic passion statements" },
];

const SECTION_MATCHERS: Record<DirectiveFieldKey, RegExp> = {
  tone: /tone/i,
  focus: /(focus|angle|goal)/i,
  keep: /(keep|maintain|retain)/i,
  avoid: /(avoid|skip|remove)/i,
  note: /(note|additional|comment)/i,
};

// ── 경험 컨텍스트 relevantPart 파서 ─────────────────────────────────────────
interface ParsedRelevantPart {
  role?: string
  period?: string
  stack?: string[]
  outcome?: string
  detail?: string
}

function parseRelevantPart(raw: string): ParsedRelevantPart {
  const result: ParsedRelevantPart = {}
  // "Role: X | Period: Y | Stack: X | Outcome: X | Relevant detail: X"
  const segments = raw.split(/\s*\|\s*/)
  for (const seg of segments) {
    const colonIdx = seg.indexOf(": ")
    if (colonIdx === -1) continue
    const key = seg.slice(0, colonIdx).trim().toLowerCase()
    const val = seg.slice(colonIdx + 2).trim()
    if (!val) continue
    if (key === "role") result.role = val
    else if (key === "period") result.period = val
    else if (key === "stack") result.stack = val.split(/,\s*/).filter(Boolean)
    else if (key === "outcome") result.outcome = val
    else if (key === "relevant detail") result.detail = cleanDetailText(val)
  }
  // fallback: if nothing parsed, treat whole string as detail
  if (!result.role && !result.period && !result.stack && !result.outcome) {
    result.detail = cleanDetailText(raw)
  }
  return result
}

function cleanDetailText(text: string): string {
  return text
    .replace(/##\s*[^\n]+\n?/g, "")          // ## 마크다운 헤더 제거
    .replace(/제목:\s*[^\n]+\n?/g, "")         // "제목: X" 제거
    .replace(/역할:\s*[^\n]+\n?/g, "")         // "역할: X" 제거
    .replace(/기간:\s*[^\n]+\n?/g, "")         // "기간: X" 제거
    .replace(/상세내용:\s*/g, "")               // "상세내용:" 레이블 제거
    .replace(/\n+/g, " ")
    .trim()
}

function RelevantPartCard({ relevantPart, relevanceScore }: { relevantPart: string; relevanceScore: number }) {
  const parsed = parseRelevantPart(relevantPart)
  const scoreColor =
    relevanceScore >= 60
      ? "text-emerald-600 bg-emerald-50 border-emerald-200 dark:text-emerald-400 dark:bg-emerald-900/20 dark:border-emerald-800"
      : relevanceScore >= 35
      ? "text-amber-600 bg-amber-50 border-amber-200 dark:text-amber-400 dark:bg-amber-900/20 dark:border-amber-800"
      : "text-muted-foreground bg-muted/50 border-border"

  return (
    <div className="space-y-2.5">
      {/* Role + Period row */}
      {(parsed.role || parsed.period) && (
        <div className="flex flex-wrap gap-x-4 gap-y-1">
          {parsed.role && (
            <span className="flex items-center gap-1 text-[11px] text-muted-foreground">
              <User className="size-3 shrink-0" />
              {parsed.role}
            </span>
          )}
          {parsed.period && (
            <span className="flex items-center gap-1 text-[11px] text-muted-foreground">
              <Calendar className="size-3 shrink-0" />
              {parsed.period}
            </span>
          )}
        </div>
      )}

      {/* Tech Stack */}
      {parsed.stack && parsed.stack.length > 0 && (
        <div className="flex items-start gap-1.5">
          <Code2 className="mt-0.5 size-3 shrink-0 text-primary/60" />
          <div className="flex flex-wrap gap-1">
            {parsed.stack.slice(0, 6).map((tech) => (
              <span
                key={tech}
                className="rounded-md bg-primary/8 px-1.5 py-0.5 text-[10px] font-semibold text-primary/80"
              >
                {tech}
              </span>
            ))}
            {parsed.stack.length > 6 && (
              <span className="rounded-md bg-muted px-1.5 py-0.5 text-[10px] text-muted-foreground">
                +{parsed.stack.length - 6}
              </span>
            )}
          </div>
        </div>
      )}

      {/* Outcome */}
      {parsed.outcome && (
        <div className="flex items-start gap-1.5">
          <BarChart3 className="mt-0.5 size-3 shrink-0 text-emerald-500" />
          <p className="text-[11px] font-semibold leading-relaxed text-emerald-700 dark:text-emerald-400">
            {parsed.outcome}
          </p>
        </div>
      )}

      {/* Detail */}
      {parsed.detail && (
        <p className="text-[11px] leading-relaxed text-foreground/70">
          {parsed.detail}
        </p>
      )}

      {/* Relevance score bar */}
      <div className="flex items-center gap-2 pt-1">
        <div className="h-1 flex-1 overflow-hidden rounded-full bg-border">
          <div
            className="h-full rounded-full bg-primary/60 transition-all"
            style={{ width: `${relevanceScore}%` }}
          />
        </div>
        <span className={`rounded-md border px-1.5 py-0.5 text-[10px] font-bold ${scoreColor}`}>
          {relevanceScore}% 일치
        </span>
      </div>
    </div>
  )
}

const parseRefineDirective = (raw?: string): DirectiveFields => {
  if (!raw) {
    return { ...DEFAULT_DIRECTIVE_FIELDS };
  }

  const fields: DirectiveFields = { ...DEFAULT_DIRECTIVE_FIELDS };
  const remainder: string[] = [];

  raw.split(/\r?\n/).map((line) => line.trim()).filter((line) => line).forEach((line) => {
    const colonIndex = line.indexOf(":");
    if (colonIndex > 0) {
      const key = line.slice(0, colonIndex).trim();
      const value = line.slice(colonIndex + 1).trim();
      if (!value) {
        return;
      }

      const matchedKey = (Object.keys(SECTION_MATCHERS) as DirectiveFieldKey[]).find((fieldKey) =>
        SECTION_MATCHERS[fieldKey].test(key)
      );
      if (matchedKey && matchedKey !== "note") {
        fields[matchedKey] = value;
        return;
      }

      if (SECTION_MATCHERS.note.test(key)) {
        fields.note = fields.note ? `${fields.note}\n${value}` : value;
        return;
      }
    }

    remainder.push(line);
  });

  if (!fields.note && remainder.length) {
    fields.note = remainder.join("\n");
  }

  return fields;
};

const buildRefineDirective = (fields: DirectiveFields): string => {
  const entries: string[] = [];

  if (fields.tone) entries.push(`Tone: ${fields.tone}`);
  if (fields.focus) entries.push(`Focus: ${fields.focus}`);
  if (fields.keep) entries.push(`Keep: ${fields.keep}`);
  if (fields.avoid) entries.push(`Avoid: ${fields.avoid}`);
  if (fields.note) entries.push(`Note: ${fields.note}`);

  return entries.join("\n");
};

const NATURAL_DIRECTIVE_PRESETS = [
  { label: "담백하게", value: "전체 톤을 과장 없이 담백하고 차분하게 정리해줘.", category: "톤" },
  { label: "자신감 있게", value: "근거는 유지하되 더 자신감 있고 단단한 문장으로 다듬어줘.", category: "톤" },
  { label: "두괄식으로", value: "핵심 결론과 성과를 먼저 쓰고, 이유와 과정을 뒤에 배치해줘.", category: "구조" },
  { label: "STAR 구조", value: "상황(Situation) → 과제(Task) → 행동(Action) → 결과(Result) 순서로 구조화해줘.", category: "구조" },
  { label: "성과 강조", value: "수치, 개선 결과, 기여도가 더 명확하게 보이게 해줘.", category: "강조" },
  { label: "추상어 줄이기", value: "열정, 노력 같은 추상어는 줄이고 구체적인 행동과 근거 중심으로 써줘.", category: "필터" },
  { label: "수치 유지", value: "들어간 수치, 기술명, 산출물은 최대한 유지해줘.", category: "필터" },
];

const PLACEHOLDER_FOCUS_VALUES = new Set(["미정", "N/A", "n/a", "-", ""]);

const normalizeDirectiveText = (raw?: string) => {
  if (!raw) {
    return ""
  }

  return raw
    .split(/\r?\n/)
    .map((line) => line.replace(/^(Tone|Focus|Keep|Avoid|Note)\s*:\s*/i, "").trim())
    .filter(Boolean)
    .join("\n")
}

const parseLengthTarget = (raw: string, maxLength: number): number | null => {
  const parsed = Number.parseInt(raw.trim(), 10)
  if (!Number.isFinite(parsed) || parsed <= 0) {
    return null
  }
  if (maxLength > 0) {
    return Math.min(parsed, maxLength)
  }
  return parsed
}

const PIPELINE_STEPS = [
  { id: "RAG", label: "경험 매칭" },
  { id: "DRAFT", label: "초안 생성" },
  { id: "WASH", label: "세탁 번역" },
  { id: "PATCH", label: "휴먼 패치" },
] as const

const getPipelineState = (
  stage: PipelineStage,
  message: string,
  isProcessing: boolean
): { activeStepId: (typeof PIPELINE_STEPS)[number]["id"]; description: string; isComplete: boolean } => {
  if (stage === "DONE" || (!isProcessing && stage === "PATCH")) {
    return {
      activeStepId: "PATCH",
      description: "모든 파이프라인 단계가 완료되었습니다.",
      isComplete: true,
    }
  }

  if (stage === "RAG") {
    return {
      activeStepId: "RAG",
      description: "이 문항에 맞는 경험을 매칭하고 있습니다.",
      isComplete: false,
    }
  }

  if (stage === "DRAFT") {
    return {
      activeStepId: "DRAFT",
      description: "경험 근거를 바탕으로 초안을 생성하고 있습니다.",
      isComplete: false,
    }
  }

  if (stage === "WASH") {
    return {
      activeStepId: "WASH",
      description: "영어 번역 후 역번역으로 세탁본을 만들고 있습니다.",
      isComplete: false,
    }
  }

  if (stage === "PATCH") {
    return {
      activeStepId: "PATCH",
      description: "세탁본의 의미 손실과 어색한 표현을 검토하고 있습니다.",
      isComplete: false,
    }
  }

  const normalized = (message || "").toLowerCase()
  if (normalized.includes("patch") || normalized.includes("review") || normalized.includes("analysis")) {
    return {
      activeStepId: "PATCH",
      description: "세탁본의 의미 손실과 어색한 표현을 검토하고 있습니다.",
      isComplete: false,
    }
  }
  if (normalized.includes("wash") || normalized.includes("translate") || normalized.includes("translation")) {
    return {
      activeStepId: "WASH",
      description: "영어 번역 후 역번역으로 세탁본을 만들고 있습니다.",
      isComplete: false,
    }
  }
  if (normalized.includes("draft") || normalized.includes("writing")) {
    return {
      activeStepId: "DRAFT",
      description: "경험 근거를 바탕으로 초안을 생성하고 있습니다.",
      isComplete: false,
    }
  }

  return {
    activeStepId: "RAG",
    description: "파이프라인을 준비하고 있습니다.",
    isComplete: false,
  }
}
// ── Batch progress card ───────────────────────────────────────────────────────
const BATCH_STAGE_LABELS: Record<string, string> = {
  RAG:   "경험 매칭 중",
  DRAFT: "초안 생성 중",
  WASH:  "번역 세탁 중",
  PATCH: "휴먼 패치 중",
  DONE:  "완료",
  IDLE:  "—",
}

function BatchProgressCard({
  batchItem,
  totalCount,
  doneCount,
  errorCount,
}: {
  batchItem: QuestionBatchState
  totalCount: number
  doneCount: number
  errorCount: number
}) {
  const progressPct = totalCount > 0 ? Math.round((doneCount / totalCount) * 100) : 0

  return (
    <Card className="border-violet-300/50 bg-gradient-to-br from-violet-50/80 to-blue-50/40 shadow-lg shadow-violet-100/30 animate-in fade-in slide-in-from-top-2 border-r-4 border-r-violet-500">
      <CardContent className="p-5">
        <div className="flex items-center gap-3 mb-4">
          <div className="relative">
            <div className="size-9 rounded-xl bg-violet-100 flex items-center justify-center">
              <Zap className="size-4 text-violet-600" />
            </div>
            <div className="absolute -inset-1 rounded-xl bg-violet-200/50 animate-ping opacity-40" />
          </div>
          <div className="flex-1 min-w-0">
            <h4 className="text-sm font-black text-violet-700 uppercase tracking-tighter">전체 문항 병렬 생성</h4>
            <p className="text-xs font-semibold text-foreground/70 italic truncate">
              {batchItem.isProcessing ? BATCH_STAGE_LABELS[batchItem.stage] ?? "처리 중..." : batchItem.message}
            </p>
          </div>
          <div className="shrink-0 text-right">
            <span className="text-lg font-black text-violet-700 tabular-nums">{doneCount}</span>
            <span className="text-xs font-bold text-muted-foreground/60"> / {totalCount}</span>
            {errorCount > 0 && (
              <p className="text-[10px] font-black text-destructive">{errorCount}건 오류</p>
            )}
          </div>
        </div>
        <div className="space-y-1.5">
          <Progress
            value={progressPct}
            className="h-2 bg-violet-100"
            indicatorClassName="bg-gradient-to-r from-violet-500 to-blue-500 shadow-[0_0_6px_rgba(139,92,246,0.4)]"
          />
          <p className="text-[10px] font-bold text-violet-600/60 text-right tabular-nums">{progressPct}%</p>
        </div>
      </CardContent>
    </Card>
  )
}

// ── Batch stage badge ────────────────────────────────────────────────────────
const BATCH_STAGE_CONFIG: Record<string, { label: string; className: string }> = {
  RAG:   { label: "매칭 중", className: "bg-blue-100 text-blue-700 border-blue-200" },
  DRAFT: { label: "초안 생성", className: "bg-violet-100 text-violet-700 border-violet-200" },
  WASH:  { label: "번역 세탁", className: "bg-amber-100 text-amber-700 border-amber-200" },
  PATCH: { label: "휴먼 패치", className: "bg-orange-100 text-orange-700 border-orange-200" },
  DONE:  { label: "완료", className: "bg-emerald-100 text-emerald-700 border-emerald-200" },
  IDLE:  { label: "오류", className: "bg-red-100 text-red-700 border-red-200" },
}

function QuestionBatchBadge({ state }: { state: QuestionBatchState | undefined }) {
  if (!state) return null
  if (state.stage === "DONE" && !state.isProcessing) {
    return (
      <span className="inline-flex items-center gap-1 rounded-full border px-1.5 py-0.5 text-[9px] font-black tracking-tight bg-emerald-100 text-emerald-700 border-emerald-200">
        <CheckCheck className="size-2.5" />
        완료
      </span>
    )
  }
  if (state.error && !state.isProcessing) {
    return (
      <span className="inline-flex items-center gap-1 rounded-full border px-1.5 py-0.5 text-[9px] font-black tracking-tight bg-red-100 text-red-700 border-red-200">
        <X className="size-2.5" />
        오류
      </span>
    )
  }
  if (state.isProcessing) {
    const cfg = BATCH_STAGE_CONFIG[state.stage] ?? BATCH_STAGE_CONFIG.RAG
    return (
      <span className={`inline-flex items-center gap-1 rounded-full border px-1.5 py-0.5 text-[9px] font-black tracking-tight ${cfg.className}`}>
        <Loader2 className="size-2.5 animate-spin" />
        {cfg.label}
      </span>
    )
  }
  return null
}

export function ContextPanel() {
  const {
    company,
    setCompany,
    position,
    setPosition,
    companyResearch,
    questions,
    activeQuestionId,
    setActiveQuestionId,
    addQuestion,
    updateActiveQuestion,
    isProcessing,
    pipelineStage,
    progressMessage,
    processingIssue,
    processingIssueSeverity,
    refineDraft,
    generateDraft,
    fetchTitleSuggestions,
    applyTitleSuggestion,
    applySuggestion,
    updateMistranslationSuggestion,
    dismissMistranslation,
    extractedContext,
    leftPanelTab,
    setLeftPanelTab,
    setHoveredMistranslationId,
    hoveredMistranslationId,
    deleteActiveQuestion,
    toggleActiveQuestionCompletion,
    isBatchRunning,
    batchState,
    batchPlan,
    isBatchPlanLoading,
    batchPlanError,
    fetchBatchPlan,
    applyBatchPlan,
    clearBatchPlan,
    batchGenerate,
    cancelBatch,
    cancelSingle,
  } = useWorkspaceStore()

  const activeQuestion = questions.find(q => q.id === activeQuestionId) || questions[0]
  const [directiveDraft, setDirectiveDraft] = useState(normalizeDirectiveText(activeQuestion.userDirective))
  const [lengthTargetDraft, setLengthTargetDraft] = useState(
    activeQuestion.lengthTarget ? String(activeQuestion.lengthTarget) : ""
  )
  const [isTitleSuggestionLoading, setIsTitleSuggestionLoading] = useState(false)
  const [isApplyingTitleSuggestion, setIsApplyingTitleSuggestion] = useState(false)
  const [isTitleSuggestionDialogOpen, setIsTitleSuggestionDialogOpen] = useState(false)
  const [isBatchPlanDialogOpen, setIsBatchPlanDialogOpen] = useState(false)
  const [isApplyingBatchPlan, setIsApplyingBatchPlan] = useState(false)
  const [titleSuggestions, setTitleSuggestions] = useState<TitleSuggestion[]>([])
  const [selectedTitleSuggestion, setSelectedTitleSuggestion] = useState("")
  const [currentTitleLine, setCurrentTitleLine] = useState("")
  const [isCompanyInsightOpen, setIsCompanyInsightOpen] = useState(false)
  const [isRagContextOpen, setIsRagContextOpen] = useState(false)
  const [justCompleted, setJustCompleted] = useState(false)
  const completionBtnRef = useRef<HTMLDivElement>(null)
  const batchBtnRef = useRef<HTMLDivElement>(null)
  const batchMouseX = useMotionValue(0)
  const batchMouseY = useMotionValue(0)
  const batchSpotlight = useMotionTemplate`radial-gradient(circle at ${batchMouseX}px ${batchMouseY}px, rgba(255,255,255,0.28) 0%, transparent 65%)`
  const topRef = useRef<HTMLDivElement>(null)
  const isDirectiveComposingRef = useRef(false)

  const scrollToTop = () => {
    topRef.current?.scrollIntoView({ behavior: "smooth", block: "start" })
  }

  useEffect(() => {
    setDirectiveDraft(normalizeDirectiveText(activeQuestion.userDirective))
    setLengthTargetDraft(
      activeQuestion.lengthTarget && activeQuestion.lengthTarget > 0
        ? String(activeQuestion.lengthTarget)
        : ""
    )
    setIsTitleSuggestionDialogOpen(false)
    setIsBatchPlanDialogOpen(false)
    setTitleSuggestions([])
    setSelectedTitleSuggestion("")
    setCurrentTitleLine("")
  }, [activeQuestion.id])

  const { mistranslations, aiReviewReport, washedKr } = activeQuestion
  const companyResearchData = parseCompanyResearch(companyResearch)
  const pipelineState = getPipelineState(pipelineStage, progressMessage, isProcessing)
  const activeStepIndex = PIPELINE_STEPS.findIndex((step) => step.id === pipelineState.activeStepId)

  const currentCount = getLength(
    getDisplayedWashedText(washedKr || "", aiReviewReport?.taggedWashedText) ||
      activeQuestion.content
  )
  const hasDraft = !!activeQuestion.content

  const handleDirectiveChange = (value: string) => {
    setDirectiveDraft(value)
  }

  const commitDirectiveDraft = () => {
    const normalizedDraft = directiveDraft.trim()
    const normalizedStored = normalizeDirectiveText(activeQuestion.userDirective).trim()

    if (normalizedDraft !== normalizedStored) {
      updateActiveQuestion({ userDirective: directiveDraft })
    }
  }

  const handleDirectivePreset = (value: string) => {
    setDirectiveDraft((prev) => {
      if (!prev.trim()) {
        return value
      }

      if (prev.includes(value)) {
        return prev
      }

      return `${prev.trim()}\n${value}`
    })
  }

  const commitLengthTargetDraft = () => {
    const parsed = parseLengthTarget(lengthTargetDraft, activeQuestion.maxLength)
    const stored = activeQuestion.lengthTarget ?? null
    if (parsed !== stored) {
      updateActiveQuestion({ lengthTarget: parsed })
    }
  }

  const handleInitialGenerate = () => {
    commitDirectiveDraft()
    commitLengthTargetDraft()
    scrollToTop()
    generateDraft({
      useDirective: directiveDraft.trim().length > 0,
      lengthTarget: parseLengthTarget(lengthTargetDraft, activeQuestion.maxLength),
    })
  }

  const handleRegenerate = () => {
    commitDirectiveDraft()
    commitLengthTargetDraft()
    scrollToTop()
    refineDraft(directiveDraft, {
      lengthTarget: parseLengthTarget(lengthTargetDraft, activeQuestion.maxLength),
    })
  }

  const handleOpenBatchPlan = async () => {
    commitDirectiveDraft()
    commitLengthTargetDraft()
    scrollToTop()

    try {
      await fetchBatchPlan()
      setIsBatchPlanDialogOpen(true)
    } catch {
      setIsBatchPlanDialogOpen(false)
    }
  }

  const handleConfirmBatchPlan = async () => {
    if (!batchPlan) {
      return
    }

    setIsApplyingBatchPlan(true)
    try {
      applyBatchPlan(batchPlan)
      setIsBatchPlanDialogOpen(false)
      await batchGenerate({ useDirective: true })
    } finally {
      setIsApplyingBatchPlan(false)
    }
  }

  const handleRewriteTitle = async () => {
    setIsTitleSuggestionLoading(true)
    try {
      const response = await fetchTitleSuggestions()
      const candidates = response.candidates ?? []
      setTitleSuggestions(candidates)
      setCurrentTitleLine(response.currentTitle ?? "")
      setSelectedTitleSuggestion(
        candidates.find((candidate) => candidate.recommended)?.title ?? candidates[0]?.title ?? ""
      )
      setIsTitleSuggestionDialogOpen(true)
    } finally {
      setIsTitleSuggestionLoading(false)
    }
  }

  const handleApplyTitleSuggestion = async () => {
    if (!selectedTitleSuggestion) {
      return
    }

    setIsApplyingTitleSuggestion(true)
    try {
      await applyTitleSuggestion(selectedTitleSuggestion)
      setIsTitleSuggestionDialogOpen(false)
    } finally {
      setIsApplyingTitleSuggestion(false)
    }
  }

  return (
    <div className="flex h-full min-w-0 flex-col border-r border-border bg-background min-h-0 overflow-hidden">
      {/* Header Context */}
      <div className="shrink-0 border-b border-border bg-muted/20 p-6">
        <div className="flex items-center gap-2 text-sm text-muted-foreground mb-6 min-w-0">
          <Settings className="size-4 shrink-0 text-muted-foreground/60" />
          {!company && isProcessing ? (
            <div className="h-5 w-24 bg-muted animate-pulse rounded-md shrink-0" />
          ) : (
            <input
              value={company}
              onChange={(e) => setCompany(e.target.value)}
              className="bg-transparent border-none p-0 focus:ring-0 font-bold text-foreground min-w-0 max-w-[45%] truncate"
              placeholder="회사명"
            />
          )}
          <span className="text-muted-foreground/40 shrink-0">|</span>
          {!company && isProcessing ? (
            <div className="h-5 w-32 bg-muted animate-pulse rounded-md" />
          ) : (
            <input
              value={position}
              onChange={(e) => setPosition(e.target.value)}
              className="bg-transparent border-none p-0 focus:ring-0 flex-1 min-w-0 text-muted-foreground/80"
              placeholder="지원 직무"
            />
          )}
        </div>

        {/* Question Tabs/List */}
        <div className="flex items-center gap-2 mb-4 overflow-x-auto no-scrollbar">
          {!company && isProcessing ? (
            <>
              <div className="h-8 w-20 bg-muted animate-pulse rounded-md" />
              <div className="h-8 w-20 bg-muted animate-pulse rounded-md" />
              <div className="h-8 w-8 bg-muted animate-pulse rounded-full" />
            </>
          ) : (
            <>
              {questions.map((q, idx) => {
                const qBatch = batchState[q.id]
                return (
                  <Button
                    key={q.id}
                    variant={activeQuestionId === q.id ? "default" : "outline"}
                    size="sm"
                    onClick={() => setActiveQuestionId(q.id)}
                    disabled={isBatchRunning && !!qBatch?.isProcessing && activeQuestionId !== q.id}
                    className="shrink-0 gap-1.5 h-8 relative"
                  >
                    문항 {idx + 1}
                    {isBatchRunning && qBatch?.isProcessing && (
                      <Loader2 className="size-3 animate-spin" />
                    )}
                    {isBatchRunning && qBatch?.stage === "DONE" && !qBatch.isProcessing && (
                      <CheckCheck className="size-3 text-emerald-500" />
                    )}
                    {isBatchRunning && qBatch?.error && !qBatch.isProcessing && (
                      <X className="size-3 text-destructive" />
                    )}
                    {!isBatchRunning && activeQuestionId === q.id && (
                      <div className="size-1.5 rounded-full bg-primary-foreground" />
                    )}
                  </Button>
                )
              })}
              <Button
                variant="ghost"
                size="icon"
                className="size-8 shrink-0 rounded-full hover:bg-primary/10 hover:text-primary transition-colors"
                onClick={addQuestion}
                disabled={isBatchRunning}
                title="문항 추가"
              >
                <Plus className="size-4" />
              </Button>
            </>
          )}
          {/* Batch Generate / Cancel button */}
          {questions.some((q) => q.dbId && q.title.trim().length > 0) && !(!company && isProcessing) && (
            isBatchRunning ? (
              <Button
                variant="outline"
                size="sm"
                className="shrink-0 gap-1.5 h-8 rounded-full border-destructive/30 text-destructive hover:bg-destructive/10 transition-all ml-auto text-[11px] font-black"
                onClick={cancelBatch}
              >
                <X className="size-3.5" />
                생성 중지
              </Button>
            ) : (
              <motion.div
                ref={batchBtnRef}
                className="relative ml-auto shrink-0 overflow-hidden rounded-full"
                onMouseMove={(e) => {
                  const rect = batchBtnRef.current?.getBoundingClientRect()
                  if (rect) {
                    batchMouseX.set(e.clientX - rect.left)
                    batchMouseY.set(e.clientY - rect.top)
                  }
                }}
                whileHover={{ scale: 1.06 }}
                whileTap={{ scale: 0.94 }}
                transition={{ type: "spring", stiffness: 420, damping: 22 }}
              >
                <motion.div
                  className="pointer-events-none absolute inset-0 rounded-full"
                  style={{ background: batchSpotlight }}
                />
                <Button
                  size="sm"
                  className="relative gap-1.5 h-8 rounded-full px-4 font-black text-[11px] bg-gradient-to-r from-primary to-primary/80 border-0 shadow-md"
                  onClick={handleOpenBatchPlan}
                  disabled={isProcessing || isBatchPlanLoading}
                  title="모든 문항 동시 생성"
                >
                  {isBatchPlanLoading ? (
                    <Loader2 className="size-3.5 animate-spin" />
                  ) : (
                    <motion.div
                      animate={{ rotate: [0, -28, 24, -18, 12, -6, 0], y: [0, -2, 1, -1, 0] }}
                      transition={{ duration: 0.55, repeat: Infinity, repeatDelay: 2.5, ease: "easeInOut" }}
                    >
                      <Zap className="size-3.5 fill-current" />
                    </motion.div>
                  )}
                  {isBatchPlanLoading ? "전략 수립 중" : "전체 생성"}
                </Button>
              </motion.div>
            )
          )}
          {questions.length > 1 && !(!company && isProcessing) && !isBatchRunning && (
            <Button
              variant="ghost"
              size="icon"
              className="size-8 shrink-0 rounded-full hover:bg-destructive/10 hover:text-destructive transition-colors"
              onClick={() => deleteActiveQuestion()}
              title="현재 문항 삭제"
            >
              <Trash2 className="size-4" />
            </Button>
          )}
        </div>

        <div className="space-y-4">
          {batchPlanError ? (
            <div className="rounded-2xl border border-destructive/20 bg-destructive/5 px-4 py-3 text-sm text-destructive">
              {batchPlanError}
            </div>
          ) : null}

          <div className="flex items-start gap-3">
            <div className="relative shrink-0" ref={completionBtnRef}>
              <motion.div
                animate={justCompleted
                  ? { scale: [1, 1.55, 0.78, 1.18, 0.96, 1], rotate: [0, -14, 8, -5, 2, 0] }
                  : { scale: 1, rotate: 0 }
                }
                transition={{ duration: 0.52, ease: [0.34, 1.56, 0.64, 1] }}
              >
                <Button
                  variant="ghost"
                  size="icon"
                  className={`size-10 rounded-xl transition-colors duration-300 border ${
                    activeQuestion.isCompleted
                      ? 'bg-emerald-50 text-emerald-600 border-emerald-200 shadow-sm shadow-emerald-100'
                      : 'bg-background text-muted-foreground border-border hover:border-emerald-300 hover:text-emerald-500'
                  }`}
                  onClick={() => {
                    if (!activeQuestion.isCompleted) {
                      const rect = completionBtnRef.current?.getBoundingClientRect()
                      if (rect) {
                        void confetti({
                          particleCount: 65,
                          spread: 75,
                          origin: {
                            x: (rect.left + rect.width / 2) / window.innerWidth,
                            y: (rect.top + rect.height / 2) / window.innerHeight,
                          },
                          colors: ['#34d399', '#6ee7b7', '#a7f3d0', '#ffffff', '#059669', '#fbbf24'],
                          ticks: 90,
                          gravity: 1.1,
                          scalar: 0.72,
                          startVelocity: 22,
                        })
                      }
                      setJustCompleted(true)
                      setTimeout(() => setJustCompleted(false), 600)
                    }
                    toggleActiveQuestionCompletion()
                  }}
                  disabled={!company && isProcessing}
                  title={activeQuestion.isCompleted ? "작성 완료 취소" : "작성 완료로 표시"}
                >
                  <motion.div
                    animate={activeQuestion.isCompleted ? { scale: 1.12, rotate: 0 } : { scale: 1 }}
                    transition={{ type: "spring", stiffness: 500, damping: 18 }}
                  >
                    <CheckCircle2 className={`size-5 ${activeQuestion.isCompleted ? 'fill-emerald-600/15 text-emerald-600' : ''}`} />
                  </motion.div>
                </Button>
              </motion.div>
              {justCompleted && (
                <motion.span
                  className="pointer-events-none absolute inset-0 rounded-xl border-2 border-emerald-400"
                  initial={{ scale: 1, opacity: 0.9 }}
                  animate={{ scale: 2.8, opacity: 0 }}
                  transition={{ duration: 0.5, ease: "easeOut" }}
                />
              )}
            </div>
            {!company && isProcessing ? (
              <div className="flex-1 space-y-2 py-2">
                <div className="h-4 w-full bg-muted animate-pulse rounded-md" />
                <div className="h-4 w-3/4 bg-muted animate-pulse rounded-md" />
              </div>
            ) : (
              <Textarea
                value={activeQuestion.title}
                onChange={(e) => updateActiveQuestion({ title: e.target.value })}
                onFocus={(e) => {
                  if (activeQuestion.title === "새로운 문항을 입력하세요") {
                    updateActiveQuestion({ title: "" });
                  }
                }}
                className={`text-lg font-bold leading-tight bg-transparent border-none focus-visible:ring-0 p-0 min-h-[50px] resize-none overflow-hidden placeholder:text-muted-foreground/30 transition-all ${activeQuestion.isCompleted ? 'text-emerald-700/80 line-through decoration-emerald-500/30' : ''}`}
                placeholder="새로운 문항을 입력해 주세요..."
                rows={2}
              />
            )}
          </div>

          {!company && isProcessing ? (
            <div className="p-4 bg-muted/20 rounded-2xl border border-muted-foreground/10 space-y-4">
              <div className="h-3 w-32 bg-muted animate-pulse rounded-md" />
              <div className="flex items-center gap-4">
                <div className="flex-1 h-3 bg-muted animate-pulse rounded-full" />
                <div className="h-6 w-20 bg-muted animate-pulse rounded-md" />
              </div>
            </div>
          ) : (
            <div className="flex flex-col gap-2 p-4 bg-muted/20 rounded-2xl border border-muted-foreground/10 group hover:bg-muted/30 transition-all">
              <div className="flex items-center justify-between text-xs text-muted-foreground mb-1 px-1">
                <div className="flex items-center gap-2">
                  <TrendingUp className={`size-3.5 transition-colors ${currentCount > activeQuestion.maxLength ? 'text-destructive animate-pulse' : 'text-primary'}`} />
                  <span className="font-bold text-muted-foreground/80 lowercase tracking-tight">글자수 진행도 (공백 포함)</span>
                </div>
                <div className="flex flex-col gap-2 min-w-[140px]">
                  <div className="relative">
                    <Input
                      type="text"
                      inputMode="numeric"
                      value={activeQuestion.maxLength}
                      onChange={(e) => {
                        const val = e.target.value.replace(/[^0-9]/g, "")
                        updateActiveQuestion({ maxLength: parseInt(val) || 0 })
                      }}
                      className="w-full h-8 pl-3 pr-8 text-[11px] font-black bg-background border-primary/10 focus-visible:ring-primary/20 rounded-lg"
                    />
                    <span className="absolute right-2.5 top-1/2 -translate-y-1/2 font-black text-[10px] text-muted-foreground/40">자</span>
                  </div>
                  <div className="grid grid-cols-4 gap-1.5">
                    {[-100, -50, 50, 100].map((val) => (
                      <button
                        key={val}
                        onClick={() => {
                          const newLimit = Math.max(0, activeQuestion.maxLength + val);
                          updateActiveQuestion({ maxLength: newLimit });
                        }}
                        className="py-1.5 text-[10px] font-black bg-background border border-primary/10 rounded-md hover:bg-primary/20 hover:border-primary/20 transition-all shadow-sm"
                      >
                        {val > 0 ? `+${val}` : val}
                      </button>
                    ))}
                  </div>
                </div>
              </div>
              
              <div className="flex items-center gap-4 px-1">
                <div className="flex-1">
                  <Progress 
                    value={Math.min((currentCount / (activeQuestion.maxLength || 1)) * 100, 100)} 
                    className={`h-2 shadow-inner transition-all ${currentCount > activeQuestion.maxLength ? 'bg-destructive/10' : ''}`}
                    indicatorClassName={currentCount > activeQuestion.maxLength ? 'bg-destructive' : 'bg-primary shadow-[0_0_8px_rgba(var(--primary),0.5)]'}
                  />
                </div>
                <div className="flex flex-col items-end min-w-[80px]">
                  <div className="flex items-baseline gap-1">
                    <span className={`text-sm tabular-nums font-black tracking-tighter transition-all ${currentCount > activeQuestion.maxLength ? 'text-destructive scale-110 drop-shadow-[0_0_4px_rgba(239,68,68,0.3)]' : 'text-primary'}`}>
                      {currentCount.toLocaleString()}
                    </span>
                    <span className="text-[10px] font-bold text-muted-foreground/40 italic"> / {activeQuestion.maxLength.toLocaleString()} 자</span>
                  </div>
                  {currentCount > activeQuestion.maxLength && (
                    <span className="text-[9px] font-black text-destructive uppercase tracking-widest animate-in fade-in slide-in-from-right-1">한도 초과!</span>
                  )}
                </div>
              </div>
            </div>
          )}
        </div>
      </div>

      {/* Mode Switcher + Action Bar (merged) */}
      <div className="shrink-0 border-b border-border bg-background/95 px-4 py-2 backdrop-blur supports-[backdrop-filter]:bg-background/80">
        <div className="flex items-center gap-2">
          <Tabs value={leftPanelTab} onValueChange={(v) => setLeftPanelTab(v as any)}>
            <TabsList className="h-8 bg-muted/30 gap-0.5">
              <TabsTrigger value="context" className="h-7 px-3 text-[11px] font-bold gap-1.5 rounded-md">
                <FileText className="size-3" />
                작성 가이드
              </TabsTrigger>
              <TabsTrigger value="report" className="h-7 px-3 text-[11px] font-bold gap-1.5 rounded-md" disabled={!aiReviewReport}>
                <Sparkles className="size-3" />
                AI 분석
                {aiReviewReport && <div className="size-1.5 rounded-full bg-primary" />}
              </TabsTrigger>
            </TabsList>
          </Tabs>

          <div className="ml-auto flex items-center gap-1">
            {isProcessing ? (
              <Button size="sm" variant="destructive" onClick={cancelSingle} className="h-8 gap-1.5 rounded-lg px-3 text-xs font-bold animate-in fade-in">
                <X className="size-3.5" />취소
              </Button>
            ) : (
              <Button size="sm" onClick={handleInitialGenerate} disabled={isBatchRunning} className="h-8 gap-1.5 rounded-lg px-3 text-xs font-bold">
                <Sparkles className="size-3.5" />새로 생성
              </Button>
            )}
            <TooltipProvider>
              <Tooltip>
                <TooltipTrigger asChild>
                  <Button size="sm" variant="outline" onClick={handleRegenerate} disabled={isProcessing || isBatchRunning || !hasDraft} className="size-8 rounded-lg p-0">
                    {isProcessing ? <Loader2 className="size-3.5 animate-spin" /> : <Wand2 className="size-3.5" />}
                  </Button>
                </TooltipTrigger>
                <TooltipContent side="bottom"><p className="text-[11px]">다듬기</p></TooltipContent>
              </Tooltip>
            </TooltipProvider>
            <TooltipProvider>
              <Tooltip>
                <TooltipTrigger asChild>
                  <Button size="sm" variant="outline" onClick={() => void handleRewriteTitle()} disabled={isProcessing || isBatchRunning || isTitleSuggestionLoading || !hasDraft} className="size-8 rounded-lg p-0">
                    {isTitleSuggestionLoading ? <Loader2 className="size-3.5 animate-spin" /> : <Lightbulb className="size-3.5" />}
                  </Button>
                </TooltipTrigger>
                <TooltipContent side="bottom"><p className="text-[11px]">제목 다듬기</p></TooltipContent>
              </Tooltip>
            </TooltipProvider>
          </div>
        </div>
      </div>

      <ScrollArea className="flex-1 h-full min-h-0 overflow-x-hidden px-6 py-4">
        <div ref={topRef} />
        <div className="min-w-0 space-y-3 overflow-x-hidden pb-6">
          {leftPanelTab === "context" ? (
            <>
          {/* Batch progress overlay for active question */}
          {isBatchRunning && activeQuestion && batchState[activeQuestion.id] && (
            <BatchProgressCard
              batchItem={batchState[activeQuestion.id]}
              totalCount={questions.filter((q) => batchState[q.id]).length}
              doneCount={Object.values(batchState).filter((s) => s.stage === "DONE" && !s.isProcessing).length}
              errorCount={Object.values(batchState).filter((s) => !!s.error && !s.isProcessing).length}
            />
          )}

          {/* Progress Overlay during processing */}
          {isProcessing && (
            <Card className="border-primary/30 bg-primary/5 shadow-2xl shadow-primary/10 animate-in fade-in slide-in-from-top-2 border-r-4 border-r-primary">
              <CardContent className="p-6">
                <div className="flex items-center gap-4 mb-6">
                  <div className="relative">
                    <div className="size-10 rounded-2xl bg-primary/20 flex items-center justify-center">
                      <Loader2 className="size-5 text-primary animate-spin" />
                    </div>
                    <div className="absolute -inset-1 rounded-2xl bg-primary/20 animate-ping opacity-30" />
                  </div>
                  <div>
                    <h4 className="text-sm font-black text-primary uppercase tracking-tighter">휴먼 패치 파이프라인</h4>
                    <p className="text-xs font-semibold text-foreground/80 italic">{pipelineState.description}</p>
                  </div>
                </div>

                <div className="space-y-4">
                  {PIPELINE_STEPS.map((step, idx, arr) => {
                    const isCompleted = pipelineState.isComplete ? idx <= activeStepIndex : idx < activeStepIndex
                    const isActive = !pipelineState.isComplete && idx === activeStepIndex
                    
                    return (
                      <div key={idx} className="flex items-center gap-3 relative">
                        <div className={`relative z-10 size-6 rounded-full flex items-center justify-center text-[10px] font-black border-2 transition-all duration-500 ${
                          isCompleted ? 'bg-primary border-primary text-white' : 
                          isActive ? 'bg-background border-primary text-primary animate-pulse shadow-[0_0_8px_rgba(var(--primary),0.4)]' : 
                          'bg-muted border-muted-foreground/20 text-muted-foreground/40'
                        }`}>
                          {isCompleted ? <Check className="size-3" strokeWidth={4} /> : idx + 1}
                        </div>
                        <div className="flex-1">
                          <p className={`text-xs font-bold transition-colors ${
                            isCompleted ? 'text-primary/80' : 
                            isActive ? 'text-primary' : 
                            'text-foreground/55'
                          }`}>
                            {step.label}
                          </p>
                        </div>
                        {isActive && (
                          <div className="flex items-center gap-1">
                            <div className="size-1 bg-primary rounded-full animate-bounce [animation-delay:-0.3s]" />
                            <div className="size-1 bg-primary rounded-full animate-bounce [animation-delay:-0.15s]" />
                            <div className="size-1 bg-primary rounded-full animate-bounce" />
                          </div>
                        )}
                        {/* Connecting line */}
                        {idx < arr.length - 1 && (
                          <div className={`absolute left-3 top-6 w-0.5 h-4 -translate-x-1/2 transition-colors duration-500 ${isCompleted ? 'bg-primary' : 'bg-muted'}`} />
                        )}
                      </div>
                    )
                  })}
                </div>

                <div className="mt-6 pt-4 border-t border-primary/10">
                  <p className="text-[13px] font-medium text-foreground/80 break-words leading-relaxed">
                    <span className="text-[10px] font-black text-primary/70 mr-2">로그</span>
                    {progressMessage}
                  </p>
                </div>
              </CardContent>
            </Card>
          )}

          {processingIssue && !isProcessing && (
            <Card
              className={
                processingIssueSeverity === "warning"
                  ? "border-amber-300/70 bg-amber-50/80"
                  : "border-destructive/30 bg-destructive/5"
              }
            >
              <CardContent className="p-4 flex items-start gap-3">
                <AlertTriangle
                  className={`size-4 mt-0.5 shrink-0 ${
                    processingIssueSeverity === "warning"
                      ? "text-amber-600"
                      : "text-destructive"
                  }`}
                />
                <div className="space-y-1">
                  <p
                    className={`text-xs font-black uppercase tracking-wider ${
                      processingIssueSeverity === "warning"
                        ? "text-amber-700"
                        : "text-destructive"
                    }`}
                  >
                    {processingIssueSeverity === "warning" ? "파이프라인 안내" : "파이프라인 오류"}
                  </p>
                  <p className="text-sm text-foreground/80 break-words">{processingIssue}</p>
                </div>
              </CardContent>
            </Card>
          )}

          {companyResearchData && (
            <section className="min-w-0 overflow-hidden">
              <button
                type="button"
                onClick={() => setIsCompanyInsightOpen((prev) => !prev)}
                className="mb-3 flex w-full items-center justify-between rounded-2xl border border-primary/15 bg-gradient-to-r from-primary/[0.08] via-background to-background px-4 py-2.5 text-left shadow-sm transition-all hover:border-primary/30 hover:bg-primary/[0.06]"
              >
                <div className="flex min-w-0 items-center gap-3">
                  <div className="flex size-10 shrink-0 items-center justify-center rounded-2xl bg-primary/10 text-primary">
                    <Building2 className="size-4" />
                  </div>
                  <div className="min-w-0">
                    <p className="text-[11px] font-black uppercase tracking-[0.24em] text-primary/70">기업 분석</p>
                    <h3 className="truncate text-sm font-black tracking-tight text-foreground">기업 분석 인사이트</h3>
                  </div>
                </div>
                <div className="flex items-center gap-3 pl-4">
                  <span className="hidden text-[11px] font-bold text-muted-foreground sm:inline">
                    {isCompanyInsightOpen ? "접기" : "펼치기"}
                  </span>
                  <div className="flex size-8 items-center justify-center rounded-full border border-primary/15 bg-background/80">
                    <ChevronDown className={`size-4 text-primary transition-transform duration-200 ${isCompanyInsightOpen ? "rotate-180" : ""}`} />
                  </div>
                </div>
              </button>

              {isCompanyInsightOpen && (
                <Card className="min-w-0 overflow-hidden border-primary/10 bg-primary/[0.03] shadow-none animate-in fade-in-50 slide-in-from-top-1">
                  <CardContent className="space-y-4 p-4">
                    <p className="break-words text-sm leading-7 text-foreground/90">
                      {companyResearchData.executiveSummary}
                    </p>

                    <div className="flex flex-wrap gap-2">
                      {Array.from(
                        new Set(
                          [
                            companyResearchData.focus.businessUnit,
                            companyResearchData.focus.targetService,
                            companyResearchData.focus.focusRole,
                            companyResearchData.focus.techFocus,
                          ].filter(
                            (item): item is string =>
                              typeof item === "string" &&
                              item.trim().length > 0 &&
                              !PLACEHOLDER_FOCUS_VALUES.has(item.trim())
                          )
                        )
                      ).map((item) => (
                        <Badge key={item} variant="outline" className="max-w-full whitespace-normal break-words border-primary/20 bg-background text-primary font-bold">
                          {item}
                        </Badge>
                      ))}
                    </div>

                    <div className="grid gap-3 md:grid-cols-2">
                      {companyResearchData.motivationHooks.slice(0, 2).map((item, index) => (
                        <div key={`motivation-${index}`} className="min-w-0 overflow-hidden break-words rounded-2xl bg-background p-3 text-xs leading-6 text-foreground/90 shadow-sm">
                          {item}
                        </div>
                      ))}
                      {companyResearchData.resumeAngles.slice(0, 2).map((item, index) => (
                        <div key={`resume-${index}`} className="min-w-0 overflow-hidden break-words rounded-2xl bg-background p-3 text-xs leading-6 text-foreground/90 shadow-sm">
                          {item}
                        </div>
                      ))}
                    </div>
                  </CardContent>
                </Card>
              )}
            </section>
          )}

          <section className="min-w-0 overflow-hidden">
            <button
              type="button"
              onClick={() => setIsRagContextOpen((prev) => !prev)}
              className="mb-3 flex w-full items-center justify-between rounded-2xl border border-primary/15 bg-gradient-to-r from-background via-primary/[0.03] to-background px-4 py-2.5 text-left shadow-sm transition-all hover:border-primary/30 hover:bg-primary/[0.04]"
            >
              <div className="flex min-w-0 items-center gap-3">
                <div className="flex size-10 shrink-0 items-center justify-center rounded-2xl bg-primary/10 text-primary">
                  <TrendingUp className="size-4" />
                </div>
                <div className="min-w-0">
                  <p className="text-[11px] font-black uppercase tracking-[0.24em] text-primary/70">경험 컨텍스트</p>
                  <h3 className="truncate text-sm font-black tracking-tight text-foreground">연결된 경험 컨텍스트</h3>
                </div>
              </div>
              <div className="flex items-center gap-2 pl-4 shrink-0">
                {!isRagContextOpen && extractedContext.length > 0 && (
                  <Badge variant="secondary" className="text-[10px] font-bold px-2 py-0.5 rounded-full border border-primary/15 bg-primary/[0.06] text-primary/80">
                    {extractedContext.length}개 연결됨
                  </Badge>
                )}
                <span className="hidden text-[11px] font-bold text-muted-foreground sm:inline">
                  {isRagContextOpen ? "접기" : "펼치기"}
                </span>
                <div className="flex size-8 items-center justify-center rounded-full border border-primary/15 bg-background/80">
                  <ChevronDown className={`size-4 text-primary transition-transform duration-200 ${isRagContextOpen ? "rotate-180" : ""}`} />
                </div>
              </div>
            </button>
            {isRagContextOpen && (
              <div className="space-y-3 animate-in fade-in-50 slide-in-from-top-1">
                {extractedContext.map((ctx, index) => (
                  <Card
                    key={`${ctx.id ?? "ctx"}-${ctx.experienceTitle}-${index}`}
                    className="min-w-0 overflow-hidden border border-border/60 bg-background shadow-sm group hover:border-primary/30 hover:shadow-md transition-all"
                  >
                    <CardContent className="p-4">
                      <p className="mb-3 min-w-0 break-words text-sm font-black group-hover:text-primary transition-colors text-foreground">
                        {ctx.experienceTitle}
                      </p>
                      <RelevantPartCard
                        relevantPart={ctx.relevantPart}
                        relevanceScore={ctx.relevanceScore}
                      />
                    </CardContent>
                  </Card>
                ))}
              </div>
            )}
          </section>

          <section className="min-w-0 overflow-hidden">
            {/* AI 작성 가이드 (Directives) */}
            <div className="mb-3 flex items-center gap-3">
              <div className="flex size-10 shrink-0 items-center justify-center rounded-2xl bg-primary/10 text-primary">
                <Wand2 className="size-4" />
              </div>
              <div className="min-w-0">
                <p className="text-[11px] font-black uppercase tracking-[0.24em] text-primary/70">초안 생성</p>
                <h3 className="truncate text-sm font-black tracking-tight text-foreground">생성 설정</h3>
              </div>
            </div>
            <div className="min-w-0 space-y-4 overflow-hidden">
              {/* 희망 글자수 */}
              <div className="rounded-2xl border border-border/60 bg-muted/30 p-4 space-y-3">
                <div className="flex items-center justify-between gap-3">
                  <label className="text-sm font-bold text-foreground">희망 글자수</label>
                  <div className="flex items-center gap-1.5 shrink-0">
                    <Input
                      type="text"
                      inputMode="numeric"
                      value={lengthTargetDraft}
                      onChange={(e) => setLengthTargetDraft(e.target.value.replace(/[^0-9]/g, ""))}
                      onBlur={commitLengthTargetDraft}
                      placeholder={`${Math.floor((activeQuestion.maxLength || 1000) * 0.8)}`}
                      className="h-9 w-[88px] text-sm text-center font-bold border-primary/30 bg-background focus:border-primary"
                    />
                    <span className="text-sm font-bold text-muted-foreground whitespace-nowrap">
                      / {activeQuestion.maxLength.toLocaleString()}자
                    </span>
                  </div>
                </div>
                {/* progress bar */}
                <div className="space-y-1">
                  <div className="h-2 w-full rounded-full bg-border/50 overflow-hidden">
                    <div
                      className="h-full rounded-full bg-primary transition-all duration-300"
                      style={{
                        width: `${Math.min(
                          100,
                          ((Number(lengthTargetDraft) || Math.floor((activeQuestion.maxLength || 1000) * 0.8)) /
                            Math.max(1, activeQuestion.maxLength)) * 100
                        )}%`,
                      }}
                    />
                  </div>
                  <p className="text-[11px] text-muted-foreground/70">
                    {lengthTargetDraft ? `목표 ${Number(lengthTargetDraft).toLocaleString()}자` : "미입력 시 최대의 80% 자동 적용"}
                  </p>
                </div>
              </div>
              {/* 추가 요청 */}
              <div className="space-y-3">
                {/* 헤더 */}
                <div className="flex items-center justify-between">
                  <label className="text-sm font-bold text-foreground flex items-center gap-2">
                    <MessageSquare className="size-4 text-primary" />
                    AI에게 추가 지시
                  </label>
                  <span className={`text-[11px] font-bold tabular-nums transition-colors ${directiveDraft.length > 200 ? 'text-amber-500' : 'text-muted-foreground/50'}`}>
                    {directiveDraft.length}자
                  </span>
                </div>
                {/* 카테고리별 프리셋 */}
                <div className="space-y-2">
                  {(["톤", "구조", "강조", "필터"] as const).map((cat) => {
                    const items = NATURAL_DIRECTIVE_PRESETS.filter(p => p.category === cat)
                    if (!items.length) return null
                    const catLabels: Record<string, string> = { 톤: "톤", 구조: "구조", 강조: "강조", 필터: "필터" }
                    return (
                      <div key={cat} className="flex items-center gap-2 flex-wrap">
                        <span className="text-[10px] font-black uppercase tracking-wider text-muted-foreground/40 w-8 shrink-0">{catLabels[cat]}</span>
                        {items.map((preset) => (
                          <Button
                            key={preset.label}
                            type="button"
                            size="sm"
                            variant={directiveDraft.includes(preset.value.slice(0, 8)) ? "default" : "outline"}
                            className="h-7 rounded-full px-3 text-[11px] font-bold transition-all"
                            onClick={() => handleDirectivePreset(preset.value)}
                          >
                            {preset.label}
                          </Button>
                        ))}
                      </div>
                    )
                  })}
                </div>
                {/* 텍스트 에리아 */}
                <div className="relative">
                  <Textarea
                    value={directiveDraft}
                    onChange={(e) => handleDirectiveChange(e.target.value)}
                    onBlur={() => {
                      if (!isDirectiveComposingRef.current) {
                        commitDirectiveDraft()
                      }
                    }}
                    onCompositionStart={() => {
                      isDirectiveComposingRef.current = true
                    }}
                    onCompositionEnd={(e) => {
                      isDirectiveComposingRef.current = false
                      handleDirectiveChange(e.currentTarget.value)
                    }}
                    placeholder={`자유롭게 지시사항을 입력하세요.\n\n예) B 프로젝트만 다룰 것. A 프로젝트 언급 금지.\n\n구조 지정 시 각 줄에 하나씩:\n1단락: 문제 상황과 배경\n2단락: 내가 취한 기술적 판단\n3단락: 성과와 수치`}
                    className="min-h-[120px] w-full resize-none text-sm leading-7 bg-muted/20 border border-border/50 focus:border-primary/40 focus:bg-background rounded-2xl transition-all duration-200"
                  />
                  {directiveDraft && (
                    <button
                      type="button"
                      onClick={() => { handleDirectiveChange(""); commitDirectiveDraft() }}
                      className="absolute top-3 right-3 size-5 rounded-full bg-muted hover:bg-muted-foreground/20 flex items-center justify-center transition-colors"
                    >
                      <X className="size-3 text-muted-foreground" />
                    </button>
                  )}
                </div>
                <p className="text-[11px] text-muted-foreground/60 leading-relaxed">
                  각 줄을 독립 지시로 인식 · 사실성·글자수 규칙 우선
                </p>
              </div>
            </div>

          </section>
        </>
      ) : (
        /* Analysis Report Tab */
        <div className="space-y-6 animate-in fade-in slide-in-from-left-2">
          <Card className="border-none bg-primary/5 shadow-none overflow-hidden border border-primary/20">
            <CardContent className="p-6 flex gap-4">
              <div className="p-3 rounded-2xl bg-primary/10 text-primary h-fit">
                <Sparkles className="size-6" />
              </div>
              <div className="flex-1">
                <h4 className="text-sm font-black text-primary uppercase tracking-tighter mb-1">AI 검토 요약</h4>
                <p className="text-[13px] text-foreground/80 leading-relaxed font-bold italic">
                  {aiReviewReport?.summary}
                </p>
              </div>
            </CardContent>
          </Card>

            <div className="space-y-4">
              <div className="flex items-center gap-2 mb-2">
                <AlertTriangle className="size-4 text-destructive" />
              <h5 className="text-xs font-black uppercase tracking-tighter">감지된 주요 오역 ({mistranslations.length})</h5>
            </div>
            
            <div className="space-y-4">
              {mistranslations.map((mis, idx) => {
                const isHovered = hoveredMistranslationId === mis.id
                return (
                  <div
                    key={`${mis.id}-${idx}`}
                    onMouseEnter={() => setHoveredMistranslationId(mis.id)}
                    onMouseLeave={() => setHoveredMistranslationId(null)}
                    className={`p-5 rounded-3xl border-l-[8px] transition-all duration-300 cursor-pointer relative overflow-hidden group ${
                      isHovered 
                        ? "bg-primary/10 border-primary shadow-2xl translate-x-1 ring-1 ring-primary/20" 
                        : "bg-muted/10 border-transparent hover:bg-muted/20"
                    }`}
                  >
                    {/* Background decoration for focus */}
                    {isHovered && (
                      <div className="absolute inset-0 bg-gradient-to-br from-primary/5 to-transparent pointer-events-none" />
                    )}
                    
                    <div className="flex items-center justify-between mb-4">
                      <div className="flex items-center gap-3">
                        {isHovered ? (
                          <div className="flex items-center gap-2 animate-in zoom-in-95">
                            <Badge variant="default" className="text-[9px] font-black uppercase px-2 py-0.5">
                              검토 포인트
                            </Badge>
                            {mis.reason && (
                              <div className="flex items-center gap-1.5 px-2 py-0.5 bg-background/50 rounded-md border border-primary/20 shadow-sm">
                                <span className="text-[9px] font-black text-primary uppercase tracking-tighter">판단 근거:</span>
                                <span className="text-[10px] font-bold text-foreground/80">{mis.reason}</span>
                              </div>
                            )}
                          </div>
                        ) : (
                          <div className="flex items-center gap-2">
                            <div className="p-1.5 rounded-full bg-muted-foreground/10 text-muted-foreground">
                              <AlertTriangle className="size-3" />
                            </div>
                            {mis.reason && (
                              <span className="text-[10px] font-bold text-muted-foreground/60 italic truncate max-w-[150px]">{mis.reason}</span>
                            )}
                          </div>
                        )}
                      </div>
                      <div className="flex items-center gap-2">
                        <Button 
                          size="sm" 
                          variant="secondary" 
                          className="h-7 px-2.5 text-[10px] font-bold gap-1.5 rounded-lg border shadow-sm hover:bg-primary hover:text-primary-foreground transition-all"
                          onClick={(e) => {
                            e.stopPropagation();
                            applySuggestion(mis.id);
                          }}
                        >
                          <Check className="size-3" />
                          반영하기
                        </Button>
                        <TooltipProvider>
                          <Tooltip>
                            <TooltipTrigger asChild>
                              <Button 
                                size="sm" 
                                variant="ghost" 
                                className="h-7 px-2 text-[10px] font-bold text-muted-foreground/40 hover:text-destructive hover:bg-destructive/10 rounded-lg transition-all"
                                onClick={(e) => {
                                  e.stopPropagation();
                                  dismissMistranslation(mis.id);
                                }}
                              >
                                숨기기
                              </Button>
                            </TooltipTrigger>
                            <TooltipContent>
                              <p className="text-[10px]">이 항목을 무시하고 목록에서 제거합니다.</p>
                            </TooltipContent>
                          </Tooltip>
                        </TooltipProvider>
                        <span className="text-[10px] font-bold text-muted-foreground/40 italic">#{idx + 1}</span>
                      </div>
                    </div>
                  <div className="text-[13px] font-bold text-foreground/90 leading-relaxed bg-primary/5 p-4 rounded-2xl border border-primary/20 shadow-sm transition-all group-hover:bg-primary/10">
                    <div className="flex items-center gap-2 mb-2">
                      <Lightbulb className="size-3.5 text-primary" />
                      <span className="text-[10px] uppercase tracking-wider font-black text-primary">직접 수정</span>
                    </div>
                    <Textarea
                      value={mis.suggestion}
                      onChange={(e) => updateMistranslationSuggestion(mis.id, e.target.value)}
                      onMouseEnter={(e) => e.stopPropagation()} 
                      placeholder="수정 없이 '반영하기'를 누르면 '초안' 원문으로 복구됩니다."
                      className="min-h-[60px] p-0 bg-transparent border-none focus-visible:ring-0 text-sm leading-relaxed resize-none font-medium h-auto placeholder:text-muted-foreground/30 placeholder:italic"
                    />
                  </div>
                  </div>
                )
              })}
            </div>

          </div>
        </div>
      )}
        </div>
      </ScrollArea>

      <Dialog open={isTitleSuggestionDialogOpen} onOpenChange={setIsTitleSuggestionDialogOpen}>
        <DialogContent className="flex max-h-[88vh] w-[min(92vw,72rem)] max-w-5xl flex-col gap-0 overflow-hidden p-0">
          <DialogHeader className="border-b border-border/60 px-6 py-5 sm:px-7">
            <DialogTitle>제목 후보 추천</DialogTitle>
            <DialogDescription className="max-w-3xl leading-relaxed">
              AI가 문항 적합도 기준으로 후보를 정렬했습니다. 추천 1개를 기본 선택해두었고, 직접 골라 적용할 수 있습니다.
            </DialogDescription>
          </DialogHeader>

          <div className="flex min-h-0 flex-1 flex-col px-6 py-5 sm:px-7">
            {currentTitleLine ? (
              <div className="rounded-3xl border border-border/70 bg-muted/35 px-5 py-4 shadow-sm">
                <p className="text-[11px] font-black uppercase tracking-[0.22em] text-muted-foreground">
                  현재 제목
                </p>
                <p className="mt-2 text-[15px] font-semibold leading-7 text-foreground sm:text-base">
                  {currentTitleLine}
                </p>
              </div>
            ) : null}

            <ScrollArea className="mt-4 min-h-0 flex-1 pr-2">
              {titleSuggestions.length > 0 ? (
                <div className="grid gap-4 pb-1 xl:grid-cols-2">
                  {titleSuggestions.map((candidate, index) => {
                    const isSelected = selectedTitleSuggestion === candidate.title

                    return (
                      <button
                        key={`${candidate.title}-${index}`}
                        type="button"
                        onClick={() => setSelectedTitleSuggestion(candidate.title)}
                        className={`group flex min-h-[196px] w-full flex-col rounded-3xl border px-5 py-5 text-left transition-all ${
                          isSelected
                            ? "border-primary/70 bg-primary/[0.07] shadow-[0_10px_30px_rgba(15,118,110,0.12)]"
                            : "border-border/70 bg-background hover:border-primary/35 hover:bg-muted/20"
                        }`}
                      >
                        <div className="flex flex-wrap items-center justify-between gap-3">
                          <div className="flex flex-wrap items-center gap-2">
                            <Badge variant={candidate.recommended ? "default" : "secondary"}>
                              {candidate.recommended ? "AI 추천" : `${index + 1}순위`}
                            </Badge>
                            <Badge variant="outline">적합도 {candidate.score}</Badge>
                          </div>
                          <span
                            className={`text-xs font-semibold transition-colors ${
                              isSelected ? "text-primary" : "text-muted-foreground group-hover:text-foreground/80"
                            }`}
                          >
                            {isSelected ? "선택됨" : "클릭해서 선택"}
                          </span>
                        </div>
                        <p className="mt-4 text-lg font-semibold leading-8 text-foreground">
                          {candidate.title}
                        </p>
                        <p className="mt-3 text-sm leading-7 text-muted-foreground">
                          {candidate.reason}
                        </p>
                      </button>
                    )
                  })}
                </div>
              ) : (
                <div className="rounded-3xl border border-dashed border-border/70 px-4 py-10 text-center text-sm text-muted-foreground">
                  표시할 제목 후보를 만들지 못했습니다. 잠시 후 다시 시도해 주세요.
                </div>
              )}
            </ScrollArea>
          </div>

          <DialogFooter className="border-t border-border/60 px-6 py-4 sm:px-7">
            <Button
              variant="outline"
              onClick={() => setIsTitleSuggestionDialogOpen(false)}
              disabled={isApplyingTitleSuggestion}
            >
              닫기
            </Button>
            <Button
              onClick={() => void handleApplyTitleSuggestion()}
              disabled={!selectedTitleSuggestion || isApplyingTitleSuggestion || titleSuggestions.length === 0}
            >
              {isApplyingTitleSuggestion ? <Loader2 className="size-4 animate-spin" /> : null}
              선택한 제목 적용
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      <BatchPlanDialog
        open={isBatchPlanDialogOpen}
        plan={batchPlan}
        onOpenChange={(open) => {
          setIsBatchPlanDialogOpen(open)
          if (!open) {
            clearBatchPlan()
          }
        }}
        onConfirm={handleConfirmBatchPlan}
        isSubmitting={isApplyingBatchPlan || isBatchRunning}
      />
    </div>
  )
}

