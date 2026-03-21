"use client"

import { useState, useEffect, useRef } from "react"
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
  ArrowRight,
  TrendingUp,
  Check,
  Trash2,
  CheckCircle2,
  ChevronDown,
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
import { type PipelineStage, useWorkspaceStore } from "@/lib/store/workspace-store"
import { parseCompanyResearch } from "@/lib/application-intelligence"
import { countResumeCharacters } from "@/lib/text/resume-character-count"
import { Tooltip, TooltipContent, TooltipProvider, TooltipTrigger } from "@/components/ui/tooltip"

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
  { label: "담백하게", value: "전체 톤을 과장 없이 담백하고 차분하게 정리해줘." },
  { label: "자신감 있게", value: "근거는 유지하되 더 자신감 있고 단단한 문장으로 다듬어줘." },
  { label: "성과 강조", value: "수치, 개선 결과, 기여도가 더 명확하게 보이게 해줘." },
  { label: "문제 해결", value: "어떤 문제를 어떻게 판단하고 해결했는지 흐름이 드러나게 해줘." },
  { label: "수치 유지", value: "들어간 수치, 기술명, 산출물은 최대한 유지해줘." },
  { label: "추상어 줄이기", value: "열정, 노력 같은 추상어는 줄이고 구체적인 행동과 근거 중심으로 써줘." },
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
    processingError,
    refineDraft,
    generateDraft,
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
  } = useWorkspaceStore()

  const activeQuestion = questions.find(q => q.id === activeQuestionId) || questions[0]
  const [directiveDraft, setDirectiveDraft] = useState(normalizeDirectiveText(activeQuestion.userDirective))
  const [lengthTargetDraft, setLengthTargetDraft] = useState(
    activeQuestion.lengthTarget ? String(activeQuestion.lengthTarget) : ""
  )
  const [isCompanyInsightOpen, setIsCompanyInsightOpen] = useState(true)
  const [isRagContextOpen, setIsRagContextOpen] = useState(true)
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
  }, [activeQuestion.id])

  const { mistranslations, aiReviewReport, washedKr } = activeQuestion
  const companyResearchData = parseCompanyResearch(companyResearch)
  const pipelineState = getPipelineState(pipelineStage, progressMessage, isProcessing)
  const activeStepIndex = PIPELINE_STEPS.findIndex((step) => step.id === pipelineState.activeStepId)

  const currentCount = getLength(washedKr || activeQuestion.content)
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

  return (
    <div className="flex h-full min-w-0 flex-col border-r border-border bg-background min-h-0 overflow-hidden">
      {/* Header Context */}
      <div className="shrink-0 border-b border-border bg-muted/20 p-6">
        <div className="flex items-center gap-2 text-sm text-muted-foreground mb-6">
          <Settings className="size-4 shrink-0 text-muted-foreground/60" />
          {!company && isProcessing ? (
            <div className="h-5 w-24 bg-muted animate-pulse rounded-md" />
          ) : (
            <input
              value={company}
              onChange={(e) => setCompany(e.target.value)}
              className="bg-transparent border-none p-0 focus:ring-0 w-24 font-bold text-foreground"
              placeholder="회사명"
            />
          )}
          <span className="text-muted-foreground/40">|</span>
          {!company && isProcessing ? (
            <div className="h-5 w-32 bg-muted animate-pulse rounded-md" />
          ) : (
            <input
              value={position}
              onChange={(e) => setPosition(e.target.value)}
              className="bg-transparent border-none p-0 focus:ring-0 flex-1 text-muted-foreground/80"
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
              {questions.map((q, idx) => (
                <Button
                  key={q.id}
                  variant={activeQuestionId === q.id ? "default" : "outline"}
                  size="sm"
                  onClick={() => setActiveQuestionId(q.id)}
                  className="shrink-0 gap-2 h-8"
                >
                  문항 {idx + 1}
                  {activeQuestionId === q.id && <div className="size-1.5 rounded-full bg-primary-foreground" />}
                </Button>
              ))}
              <Button variant="ghost" size="icon" className="size-8 shrink-0 rounded-full hover:bg-primary/10 hover:text-primary transition-colors" onClick={addQuestion} title="문항 추가">
                <Plus className="size-4" />
              </Button>
            </>
          )}
          {questions.length > 1 && !(!company && isProcessing) && (
            <Button variant="ghost" size="icon" className="size-8 shrink-0 rounded-full hover:bg-destructive/10 hover:text-destructive transition-colors ml-auto" onClick={() => deleteActiveQuestion()} title="현재 문항 삭제">
              <Trash2 className="size-4" />
            </Button>
          )}
        </div>

        <div className="space-y-4">
          <div className="flex items-start gap-3">
            <Button
              variant="ghost"
              size="icon"
              className={`size-10 shrink-0 rounded-xl transition-all border ${activeQuestion.isCompleted ? 'bg-emerald-50 text-emerald-600 border-emerald-200' : 'bg-background text-muted-foreground border-border'}`}
              onClick={() => toggleActiveQuestionCompletion()}
              disabled={!company && isProcessing}
              title={activeQuestion.isCompleted ? "작성 완료 취소" : "작성 완료로 표시"}
            >
              <CheckCircle2 className={`size-5 ${activeQuestion.isCompleted ? 'fill-emerald-600/10' : ''}`} />
            </Button>
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
                      type="number" 
                      value={activeQuestion.maxLength} 
                      onChange={(e) => updateActiveQuestion({ maxLength: parseInt(e.target.value) || 0 })}
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

      {/* Mode Switcher */}
      <div className="bg-muted/5 border-b border-border px-6 py-2 shrink-0">
        <Tabs value={leftPanelTab} onValueChange={(v) => setLeftPanelTab(v as any)} className="w-full">
          <TabsList className="grid w-full grid-cols-2 bg-muted/20">
            <TabsTrigger value="context" className="text-xs font-bold uppercase tracking-tighter gap-2">
              <FileText className="size-3" />
              작성 가이드
            </TabsTrigger>
            <TabsTrigger value="report" className="text-xs font-bold uppercase tracking-tighter gap-2" disabled={!aiReviewReport}>
              <Sparkles className="size-3" />
              AI 분석 리포트
              {aiReviewReport && <div className="size-1.5 rounded-full bg-primary" />}
            </TabsTrigger>
          </TabsList>
        </Tabs>
      </div>

      <div className="shrink-0 border-b border-border bg-background/95 px-6 py-3 backdrop-blur supports-[backdrop-filter]:bg-background/80">
        <div className="flex flex-col gap-2 sm:flex-row sm:items-center sm:justify-between">
          <div className="min-w-0">
            <p className="text-[11px] font-black uppercase tracking-wider text-primary">
              {"\uc791\uc5c5 \uc561\uc158"}
            </p>
            <p className="text-xs text-muted-foreground">
              {"\uae30\uc5c5 \ubd84\uc11d\uacfc \uacbd\ud5d8 \ucee8\ud14d\uc2a4\ud2b8\ub97c \ubcf4\uba74\uc11c \ubc14\ub85c \ucd08\uc548 \uc0dd\uc131\uacfc \uc138\ud0c1\uc744 \uc2e4\ud589\ud560 \uc218 \uc788\uc2b5\ub2c8\ub2e4."}
            </p>
          </div>
          <div className="flex flex-wrap items-center gap-2">
            <Button
              size="sm"
              onClick={handleInitialGenerate}
              disabled={isProcessing}
              className="gap-2 rounded-full px-4 font-bold shadow-sm"
            >
              {isProcessing ? (
                <Loader2 className="size-3.5 animate-spin" />
              ) : (
                <Sparkles className="size-3.5" />
              )}
              {"\uc0c8\ub85c \uc0dd\uc131"}
            </Button>
            <Button
              size="sm"
              variant="outline"
              onClick={handleRegenerate}
              disabled={isProcessing || !hasDraft}
              className="gap-2 rounded-full px-4 font-bold shadow-sm"
            >
              {isProcessing ? (
                <Loader2 className="size-3.5 animate-spin" />
              ) : (
                <Wand2 className="size-3.5" />
              )}
              {"\ub2e4\ub4ec\uae30"}
            </Button>
          </div>
        </div>
      </div>

      <ScrollArea className="flex-1 h-full min-h-0 overflow-x-hidden px-6 py-4">
        <div ref={topRef} />
        <div className="min-w-0 space-y-8 overflow-x-hidden pb-10">
          {leftPanelTab === "context" ? (
            <>
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

          {processingError && !isProcessing && (
            <Card className="border-destructive/30 bg-destructive/5">
              <CardContent className="p-4 flex items-start gap-3">
                <AlertTriangle className="size-4 text-destructive mt-0.5 shrink-0" />
                <div className="space-y-1">
                  <p className="text-xs font-black uppercase tracking-wider text-destructive">파이프라인 오류</p>
                  <p className="text-sm text-foreground/80 break-words">{processingError}</p>
                </div>
              </CardContent>
            </Card>
          )}

          {companyResearchData && (
            <section className="min-w-0 overflow-hidden">
              <button
                type="button"
                onClick={() => setIsCompanyInsightOpen((prev) => !prev)}
                className="mb-4 flex w-full items-center justify-between rounded-2xl border border-primary/15 bg-gradient-to-r from-primary/[0.08] via-background to-background px-4 py-3 text-left shadow-sm transition-all hover:border-primary/30 hover:bg-primary/[0.06]"
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
              className="mb-4 flex w-full items-center justify-between rounded-2xl border border-primary/15 bg-gradient-to-r from-background via-primary/[0.03] to-background px-4 py-3 text-left shadow-sm transition-all hover:border-primary/30 hover:bg-primary/[0.04]"
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
              <div className="flex items-center gap-3 pl-4">
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
                    className="min-w-0 overflow-hidden bg-muted/40 border-none shadow-none group hover:bg-primary/5 transition-colors"
                  >
                    <CardContent className="p-4">
                      <div className="flex min-w-0 items-center justify-between gap-2 mb-2">
                        <span className="min-w-0 break-words text-sm font-black group-hover:text-primary transition-colors text-foreground">{ctx.experienceTitle}</span>
                        <Badge variant="outline" className="text-[10px] bg-background border-primary/20 text-primary font-bold">
                          {ctx.relevanceScore}% 일치
                        </Badge>
                      </div>
                      <p className="break-words text-xs text-foreground/90 leading-relaxed font-medium">
                        {ctx.relevantPart}
                      </p>
                    </CardContent>
                  </Card>
                ))}
              </div>
            )}
          </section>

          <section className="min-w-0 overflow-hidden">
            <div className="flex items-center justify-between mb-4">
                <div className="flex items-center gap-2">
                  <Wand2 className="size-4 text-primary" />
                  <h3 className="text-sm font-black uppercase tracking-wider text-primary">AI 초안 작업장</h3>
                </div>
              <div className="flex items-center gap-2">
                <Button 
                  size="sm" 
                  variant="default" 
                  onClick={handleInitialGenerate}
                  disabled={isProcessing}
                  className="gap-2 shadow-sm rounded-full px-4 font-bold"
                >
                  {isProcessing ? (
                    <Loader2 className="size-3 animate-spin" />
                  ) : (
                    <Sparkles className="size-3" />
                  )}
                  {"새로 생성"}
                </Button>
                <Button
                  size="sm"
                  variant="outline"
                  onClick={handleRegenerate}
                  disabled={isProcessing || !hasDraft}
                  className="gap-2 shadow-sm rounded-full px-4 font-bold"
                >
                  {isProcessing ? (
                    <Loader2 className="size-3 animate-spin" />
                  ) : (
                    <Wand2 className="size-3" />
                  )}
                  {"다듬기"}
                </Button>
              </div>
            </div>

            {/* AI 작성 가이드 (Directives) */}
            <div className="min-w-0 space-y-4 overflow-hidden pt-2">
              <div className="rounded-xl border border-primary/15 bg-primary/[0.03] p-4">
                <div className="mb-2 flex items-center justify-between gap-2">
                  <label className="text-sm font-semibold text-primary">희망 글자수</label>
                  <span className="text-[11px] text-muted-foreground">
                    미입력 시 자동으로 최대 글자수의 80% 이상을 목표로 생성합니다.
                  </span>
                </div>
                <div className="flex items-center gap-2">
                  <Input
                    type="number"
                    min={1}
                    max={Math.max(1, activeQuestion.maxLength)}
                    value={lengthTargetDraft}
                    onChange={(e) => setLengthTargetDraft(e.target.value)}
                    onBlur={commitLengthTargetDraft}
                    placeholder={`예: ${Math.floor((activeQuestion.maxLength || 1000) * 0.8)}`}
                    className="h-9 w-full max-w-[220px]"
                  />
                  <span className="text-xs text-muted-foreground">
                    최대 {activeQuestion.maxLength.toLocaleString()}자
                  </span>
                </div>
              </div>
              <div className="flex min-w-0 items-center justify-between gap-3">
                <label className="text-sm font-semibold flex items-center gap-2 text-primary">
                  <MessageSquare className="w-4 h-4" />
                  추가 요청
                </label>
                <span className="min-w-0 break-words text-right text-[11px] text-muted-foreground">
                  원하는 방향을 자연스럽게 적어주세요.
                </span>
              </div>
              <div className="flex flex-wrap gap-2">
                {NATURAL_DIRECTIVE_PRESETS.map((preset) => (
                  <Button
                    key={preset.label}
                    type="button"
                    size="sm"
                    variant="outline"
                    className="h-8 rounded-full border-primary/20 bg-background/70 px-3 text-[11px] font-bold hover:bg-primary/5"
                    onClick={() => handleDirectivePreset(preset.value)}
                  >
                    {preset.label}
                  </Button>
                ))}
              </div>
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
                placeholder="예) 병원 전산 직무라서 기술 나열보다 운영 안정성과 데이터 흐름 개선에 기여한 경험을 강조해줘. 문장은 추상적 표현보다 근거 중심으로 다듬어줘."
                className="min-h-[150px] w-full max-w-full resize-none overflow-x-hidden whitespace-pre-wrap break-words text-sm leading-7 bg-background/50 focus:bg-background border border-border/40 focus:border-primary/40 transition-all duration-300"
              />
              <p className="break-words text-[11px] text-muted-foreground leading-relaxed">
                이 요청은 최우선 제약사항 바로 아래 우선순위로 반영됩니다. 사실성, 글자수, 금지 규칙과 충돌하지 않는 한 최대한 가깝게 따릅니다.
              </p>
            </div>

            <Separator className="bg-primary/10" />

            <div className="relative min-w-0 overflow-hidden">
              <div className="absolute top-3 left-3 flex items-center gap-1.5 pointer-events-none opacity-70">
                <FileText className="size-4 text-primary" />
                <span className="text-[10px] font-black uppercase tracking-tighter text-primary">초안 편집</span>
              </div>
              <Textarea
                value={activeQuestion.content}
                onChange={(e) => updateActiveQuestion({ content: e.target.value })}
                className={`min-h-[400px] w-full max-w-full resize-none overflow-x-hidden whitespace-pre-wrap break-words bg-muted/20 border-none focus-visible:ring-1 focus-visible:ring-primary/20 p-8 pt-12 text-sm leading-relaxed rounded-2xl placeholder:opacity-70 text-foreground font-medium transition-all ${hoveredMistranslationId ? 'ring-2 ring-primary/40 bg-primary/5 scale-[1.01]' : ''}`}
                placeholder="나의 경험 데이터를 기반으로 AI가 초안을 작성합니다. 직접 내용을 입력하거나 수정할 수도 있습니다."
              />
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
    </div>
  )
}

