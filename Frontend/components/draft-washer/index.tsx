"use client"

import { useCallback, useEffect, useMemo, useRef, useState, type ReactNode } from "react"
import Link from "next/link"
import { useRouter } from "next/navigation"
import {
  AlertTriangle,
  ArrowRightLeft,
  Building2,
  CheckCircle2,
  ChevronDown,
  ClipboardCheck,
  Copy,
  ExternalLink,
  FileText,
  Languages,
  ListChecks,
  Loader2,
  PencilLine,
  RefreshCw,
  RotateCcw,
  Save,
  Sparkles,
  Waves,
  X,
} from "lucide-react"
import { toast } from "sonner"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Collapsible, CollapsibleContent, CollapsibleTrigger } from "@/components/ui/collapsible"
import { Progress } from "@/components/ui/progress"
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select"
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs"
import { Textarea } from "@/components/ui/textarea"
import {
  fetchWashApplications,
  saveManualDraft,
  streamDraftWash,
  streamQuestionRewash,
  type DraftWashProgress,
  type DraftWashResult,
  type DraftWashStage,
  type QuestionWashProgress,
  type QuestionWashResult,
  type WashApplicationSummary,
} from "@/lib/api/draft-wash"
import {
  fetchFinalEditorData,
  fetchTitleSuggestionsForDraft,
  saveFinalText,
  type TitleCandidate,
} from "@/lib/api/final-editor"
import { countResumeCharacters } from "@/lib/text/resume-character-count"
import { cn } from "@/lib/utils"

const MAX_DRAFT_LENGTH = 12_000

const standaloneSteps: Array<{
  stage: DraftWashStage
  label: string
  description: string
}> = [
  {
    stage: "received",
    label: "접수",
    description: "붙여넣은 초안을 확인합니다.",
  },
  {
    stage: "toEnglish",
    label: "영어 번역",
    description: "문장의 기계적 결을 한번 분리합니다.",
  },
  {
    stage: "toKorean",
    label: "한글 역번역",
    description: "다시 자연스러운 한국어로 되돌립니다.",
  },
  {
    stage: "ready",
    label: "편집 가능",
    description: "결과를 에디터에 올립니다.",
  },
]

const questionSteps = [
  { stage: "CONNECTED", label: "연결", description: "문항 WASH 작업을 준비합니다." },
  { stage: "DRAFT", label: "초안 저장", description: "ChatGPT 초안을 문항 본문으로 확정합니다." },
  { stage: "WASH", label: "번역 세탁", description: "영어 번역 후 역번역을 진행합니다." },
  { stage: "PATCH", label: "휴먼 패치", description: "오역과 어색한 표현을 검토합니다." },
  { stage: "DONE", label: "저장", description: "WASH 결과를 문항에 저장합니다." },
]

interface DraftWasherProps {
  initialApplicationId?: string
  initialQuestionId?: string
}

interface LoadedQuestionState {
  questionId: number
  applicationId: number | null
  questionText: string
  maxLength: number
  companyName: string | null
  position: string | null
  savedDraft: string
  savedFinal: string
  hasSavedFinal: boolean
}

interface CharMeterProps {
  label: string
  chars: number
  limit: number
  icon: ReactNode
  emphasis?: "draft" | "wash" | "final"
}

function isAbortError(error: unknown) {
  return error instanceof DOMException && error.name === "AbortError"
}

function resolveCharUsage(chars: number, limit: number) {
  if (!limit || limit <= 0) {
    return {
      percent: 0,
      remaining: null,
      isOver: false,
      isNearLimit: false,
    }
  }

  const percent = Math.min(Math.round((chars / limit) * 100), 100)
  return {
    percent,
    remaining: limit - chars,
    isOver: chars > limit,
    isNearLimit: chars >= limit * 0.9 && chars <= limit,
  }
}

function CharMeter({ label, chars, limit, icon, emphasis = "draft" }: CharMeterProps) {
  const usage = resolveCharUsage(chars, limit)
  const accentClass = usage.isOver
    ? "text-destructive"
    : usage.isNearLimit
      ? "text-amber-600"
      : emphasis === "final"
        ? "text-emerald-600"
        : emphasis === "wash"
          ? "text-primary"
          : "text-foreground"
  const indicatorClassName = usage.isOver
    ? "bg-destructive"
    : usage.isNearLimit
      ? "bg-amber-500"
      : emphasis === "final"
        ? "bg-emerald-600"
        : emphasis === "wash"
          ? "bg-primary"
          : "bg-foreground"

  return (
    <div
      className={cn(
        "min-w-0 rounded-lg border bg-background px-3 py-2.5 shadow-sm",
        usage.isOver ? "border-destructive/40" : "border-border/80"
      )}
    >
      <div className="flex items-center justify-between gap-3">
        <div className="flex min-w-0 items-center gap-2">
          <span className="flex size-7 shrink-0 items-center justify-center rounded-md bg-muted text-muted-foreground">
            {icon}
          </span>
          <span className="truncate text-xs font-semibold text-muted-foreground">{label}</span>
        </div>
        <span className={cn("shrink-0 text-[11px] font-medium", accentClass)}>
          {usage.isOver
            ? `${Math.abs(usage.remaining ?? 0).toLocaleString()}자 초과`
            : usage.remaining == null
              ? "제한 없음"
              : `${usage.remaining.toLocaleString()}자 남음`}
        </span>
      </div>
      <div className="mt-2 flex items-end justify-between gap-3">
        <div className="min-w-0">
          <span className={cn("text-2xl font-semibold tabular-nums leading-none", accentClass)}>
            {chars.toLocaleString()}
          </span>
          <span className="ml-1 text-xs text-muted-foreground">자</span>
        </div>
        {limit > 0 ? (
          <span className="shrink-0 text-xs tabular-nums text-muted-foreground">
            / {limit.toLocaleString()}자
          </span>
        ) : null}
      </div>
      {limit > 0 ? (
        <Progress
          value={usage.percent}
          className="mt-2 h-1.5 bg-muted"
          indicatorClassName={indicatorClassName}
        />
      ) : null}
    </div>
  )
}

function normalizeStage(stage: string | null | undefined) {
  return (stage ?? "").trim().toUpperCase()
}

function standaloneStageIndex(stage: string | null | undefined) {
  const normalized = stage ?? ""
  return standaloneSteps.findIndex((step) => step.stage === normalized)
}

function questionStageIndex(stage: string | null | undefined) {
  const normalized = normalizeStage(stage)
  return questionSteps.findIndex((step) => step.stage === normalized)
}

const LEADING_BRACKET_TITLE_PATTERN = /^\s*\[[^\]\n]+\]\s*/

function applyTitleLineToText(text: string, titleLine: string) {
  const normalizedTitle = titleLine.trim()
  const normalizedText = text.trim()

  if (!normalizedTitle) return text
  if (!normalizedText) return normalizedTitle

  if (LEADING_BRACKET_TITLE_PATTERN.test(normalizedText)) {
    return normalizedText.replace(LEADING_BRACKET_TITLE_PATTERN, `${normalizedTitle}\n\n`)
  }

  return `${normalizedTitle}\n\n${normalizedText}`
}

function findApplicationForInitialQuestion(
  applications: WashApplicationSummary[],
  initialApplicationId?: string,
  initialQuestionId?: string
) {
  if (initialApplicationId) {
    const directMatch = applications.find((application) => application.id === initialApplicationId)
    if (directMatch) {
      return directMatch
    }
  }

  if (initialQuestionId) {
    return applications.find((application) =>
      application.questions.some((question) => question.id === initialQuestionId)
    )
  }

  return applications[0] ?? null
}

export function DraftWasher({
  initialApplicationId,
  initialQuestionId,
}: DraftWasherProps = {}) {
  const router = useRouter()
  const [applications, setApplications] = useState<WashApplicationSummary[]>([])
  const [selectedApplicationId, setSelectedApplicationId] = useState(initialApplicationId ?? "")
  const [selectedQuestionId, setSelectedQuestionId] = useState(initialQuestionId ?? "")
  const [loadedQuestion, setLoadedQuestion] = useState<LoadedQuestionState | null>(null)
  const [draftText, setDraftText] = useState("")
  const [washedText, setWashedText] = useState("")
  const [finalText, setFinalText] = useState("")
  const [selectedTitle, setSelectedTitle] = useState("")
  const [titleCandidates, setTitleCandidates] = useState<TitleCandidate[]>([])
  const [isTitlePanelOpen, setIsTitlePanelOpen] = useState(false)
  const [englishText, setEnglishText] = useState("")
  const [progress, setProgress] = useState<DraftWashProgress | QuestionWashProgress | null>(null)
  const [result, setResult] = useState<DraftWashResult | QuestionWashResult | null>(null)
  const [errorMessage, setErrorMessage] = useState<string | null>(null)
  const [isLoadingApplications, setIsLoadingApplications] = useState(true)
  const [isLoadingQuestion, setIsLoadingQuestion] = useState(false)
  const [isSavingDraft, setIsSavingDraft] = useState(false)
  const [isSavingFinal, setIsSavingFinal] = useState(false)
  const [isGeneratingTitle, setIsGeneratingTitle] = useState(false)
  const [isWashing, setIsWashing] = useState(false)
  const [copied, setCopied] = useState(false)
  const [outputTab, setOutputTab] = useState<"final" | "wash">("final")
  const abortRef = useRef<AbortController | null>(null)

  const selectedApplication = applications.find(
    (application) => application.id === selectedApplicationId
  )
  const selectedQuestion = selectedApplication?.questions.find(
    (question) => question.id === selectedQuestionId
  )
  const selectedQuestionIndex = selectedApplication?.questions.findIndex(
    (question) => question.id === selectedQuestionId
  ) ?? -1
  const questionDbId = selectedQuestionId ? Number(selectedQuestionId) : null
  const hasConnectedQuestion = questionDbId !== null && Number.isFinite(questionDbId)
  const draftChars = countResumeCharacters(draftText)
  const washedChars = countResumeCharacters(washedText)
  const finalChars = countResumeCharacters(finalText || washedText)
  const maxLength = loadedQuestion?.maxLength ?? selectedQuestion?.maxLength ?? 0
  const isOverStandaloneLimit = !hasConnectedQuestion && draftText.length > MAX_DRAFT_LENGTH
  const isDraftDirty = hasConnectedQuestion && draftText !== (loadedQuestion?.savedDraft ?? "")
  const isFinalDirty =
    hasConnectedQuestion &&
    (finalText !== (loadedQuestion?.savedFinal ?? "") ||
      (!loadedQuestion?.hasSavedFinal && finalText.trim().length > 0))
  const canWash =
    draftText.trim().length > 0 &&
    !isOverStandaloneLimit &&
    !isWashing &&
    !isSavingDraft &&
    (!hasConnectedQuestion || questionDbId !== null)

  const progressSteps = hasConnectedQuestion ? questionSteps : standaloneSteps
  const activeStepIndex = hasConnectedQuestion
    ? questionStageIndex(progress?.stage)
    : standaloneStageIndex(progress?.stage)
  const selectedQuestionNumber = selectedQuestionIndex >= 0 ? selectedQuestionIndex + 1 : null
  const selectedQuestionTitle = selectedQuestion?.title || loadedQuestion?.questionText || "문항을 선택하세요"
  const selectedApplicationTitle = selectedApplication
    ? `${selectedApplication.companyName}${selectedApplication.position ? ` · ${selectedApplication.position}` : ""}`
    : "공고 선택 없이 임시 WASH"
  const copySourceLabel = finalText.trim() ? "최종본" : "WASH 결과"
  const charLimit = hasConnectedQuestion && maxLength ? maxLength : MAX_DRAFT_LENGTH
  const titleSourceText = finalText.trim() || washedText.trim() || draftText.trim()
  const topTitleCandidate = titleCandidates[0]
  const canGenerateTitle =
    hasConnectedQuestion &&
    questionDbId !== null &&
    titleSourceText.length > 0 &&
    !isLoadingQuestion &&
    !isSavingFinal &&
    !isWashing

  const statusText = useMemo(() => {
    if (isLoadingApplications) return "공고 목록을 불러오는 중입니다."
    if (isLoadingQuestion) return "선택한 문항의 저장된 본문을 불러오는 중입니다."
    if (isOverStandaloneLimit) return "12,000자 이하로 줄여 주세요."
    if (errorMessage) return errorMessage
    if (progress) return progress.message
    if (hasConnectedQuestion) {
      if (washedText) return "WASH 결과를 최종본으로 다듬고 바로 복사할 수 있습니다."
      return "ChatGPT 웹에서 만든 초안을 붙여넣고 문항에 저장한 뒤 WASH를 실행하세요."
    }
    if (result) return "WASH본을 바로 수정하거나 복사할 수 있습니다."
    return "공고/문항을 선택하면 RESUMADE 문항 데이터에 저장됩니다. 선택하지 않으면 임시 WASH로 동작합니다."
  }, [
    errorMessage,
    hasConnectedQuestion,
    isLoadingApplications,
    isLoadingQuestion,
    isOverStandaloneLimit,
    progress,
    result,
    washedText,
  ])

  const loadQuestion = useCallback(async (nextQuestionId: number) => {
    setIsLoadingQuestion(true)
    setErrorMessage(null)

    try {
      const data = await fetchFinalEditorData(nextQuestionId)
      const savedDraft = data.originalDraft ?? ""
      const savedWashed = data.washedDraft ?? ""
      const savedFinal = data.savedFinalText ?? ""
      const displayFinal = data.savedFinalText ?? savedWashed

      setLoadedQuestion({
        questionId: data.questionId,
        applicationId: data.applicationId,
        questionText: data.questionText,
        maxLength: data.maxLength,
        companyName: data.companyName,
        position: data.position,
        savedDraft,
        savedFinal,
        hasSavedFinal: data.savedFinalText != null,
      })
      setDraftText(savedDraft)
      setWashedText(savedWashed)
      setFinalText(displayFinal)
      setSelectedTitle(data.selectedTitle ?? "")
      setTitleCandidates(data.titleCandidates ?? [])
      setIsTitlePanelOpen(false)
      setEnglishText("")
      setProgress(null)
      setResult(null)
      setCopied(false)
      setOutputTab("final")
      setApplications((current) =>
        current.map((application) => ({
          ...application,
          questions: application.questions.map((question) =>
            question.id === String(nextQuestionId)
              ? {
                  ...question,
                  title: data.questionText,
                  maxLength: data.maxLength,
                  content: savedDraft,
                  washedKr: savedWashed,
                  finalText: data.savedFinalText ?? null,
                }
              : question
          ),
        }))
      )
    } catch (error) {
      const message = error instanceof Error ? error.message : "문항 정보를 불러오지 못했습니다."
      setErrorMessage(message)
      toast.error(message)
    } finally {
      setIsLoadingQuestion(false)
    }
  }, [])

  useEffect(() => {
    let cancelled = false

    const loadApplications = async () => {
      setIsLoadingApplications(true)
      try {
        const nextApplications = await fetchWashApplications()
        if (cancelled) return

        setApplications(nextApplications)

        const initialApplication = findApplicationForInitialQuestion(
          nextApplications,
          initialApplicationId,
          initialQuestionId
        )
        const initialQuestion =
          initialApplication?.questions.find((question) => question.id === initialQuestionId) ??
          initialApplication?.questions[0] ??
          null

        setSelectedApplicationId(initialApplication?.id ?? "")
        setSelectedQuestionId(initialQuestion?.id ?? "")
      } catch (error) {
        if (cancelled) return
        const message = error instanceof Error ? error.message : "공고 목록을 불러오지 못했습니다."
        setErrorMessage(message)
        toast.error(message)
      } finally {
        if (!cancelled) {
          setIsLoadingApplications(false)
        }
      }
    }

    loadApplications()
    return () => {
      cancelled = true
    }
  }, [initialApplicationId, initialQuestionId])

  useEffect(() => {
    if (!hasConnectedQuestion || questionDbId === null) {
      setLoadedQuestion(null)
      setSelectedTitle("")
      setTitleCandidates([])
      setIsTitlePanelOpen(false)
      return
    }

    void loadQuestion(questionDbId)
  }, [hasConnectedQuestion, loadQuestion, questionDbId])

  const replaceSelectionUrl = useCallback(
    (applicationId: string, questionId: string) => {
      if (applicationId && questionId) {
        router.replace(`/wash?applicationId=${applicationId}&questionId=${questionId}`, {
          scroll: false,
        })
        return
      }

      if (applicationId) {
        router.replace(`/wash?applicationId=${applicationId}`, { scroll: false })
        return
      }

      router.replace("/wash", { scroll: false })
    },
    [router]
  )

  const handleApplicationChange = (applicationId: string) => {
    const nextApplication = applications.find((application) => application.id === applicationId)
    const nextQuestionId = nextApplication?.questions[0]?.id ?? ""

    abortRef.current?.abort()
    setSelectedApplicationId(applicationId)
    setSelectedQuestionId(nextQuestionId)
    replaceSelectionUrl(applicationId, nextQuestionId)
  }

  const handleQuestionChange = (questionId: string) => {
    abortRef.current?.abort()
    setSelectedQuestionId(questionId)
    replaceSelectionUrl(selectedApplicationId, questionId)
  }

  const patchLoadedQuestionAfterDraftSave = (nextDraft: string) => {
    setLoadedQuestion((current) =>
      current
        ? {
            ...current,
            savedDraft: nextDraft,
          }
        : current
    )
    setApplications((current) =>
      current.map((application) => ({
        ...application,
        questions: application.questions.map((question) =>
          question.id === selectedQuestionId
            ? { ...question, content: nextDraft }
            : question
        ),
      }))
    )
  }

  const handleSaveDraft = async ({ silent = false } = {}) => {
    if (!hasConnectedQuestion || questionDbId === null) {
      return false
    }

    setIsSavingDraft(true)
    setErrorMessage(null)

    try {
      await saveManualDraft(questionDbId, draftText)
      patchLoadedQuestionAfterDraftSave(draftText)
      if (!silent) {
        toast.success("문항 초안을 저장했습니다.")
      }
      return true
    } catch (error) {
      const message = error instanceof Error ? error.message : "초안 저장에 실패했습니다."
      setErrorMessage(message)
      toast.error(message)
      return false
    } finally {
      setIsSavingDraft(false)
    }
  }

  const handleStandaloneWash = async (controller: AbortController) => {
    await streamDraftWash({
      draftText,
      signal: controller.signal,
      onProgress: setProgress,
      onComplete: (nextResult) => {
        setResult(nextResult)
        setEnglishText(nextResult.englishText ?? "")
        setWashedText(nextResult.washedText ?? "")
        setFinalText(nextResult.washedText ?? "")
        setSelectedTitle("")
        setTitleCandidates([])
        setIsTitlePanelOpen(false)
        setOutputTab("final")
      },
    })
  }

  const handleQuestionWash = async (
    controller: AbortController,
    currentQuestionId: number,
    { seedEmptyFinal }: { seedEmptyFinal: boolean }
  ) => {
    await streamQuestionRewash({
      questionId: currentQuestionId,
      signal: controller.signal,
      onProgress: setProgress,
      onDraftIntermediate: setDraftText,
      onWashedIntermediate: (nextWashedText) => {
        setWashedText(nextWashedText)
        if (seedEmptyFinal) {
          setFinalText(nextWashedText)
          setOutputTab("final")
        } else {
          setOutputTab("wash")
        }
      },
      onComplete: async (nextResult) => {
        setResult(nextResult)
        const nextWashedText = nextResult.draft ?? nextResult.washedDraft ?? washedText
        setWashedText(nextWashedText)
        if (seedEmptyFinal) {
          setFinalText(nextWashedText)
          setOutputTab("final")
        } else {
          setOutputTab("wash")
        }
        setLoadedQuestion((current) =>
          current
            ? {
                ...current,
                savedDraft: nextResult.sourceDraft ?? draftText,
              }
            : current
        )
        setApplications((current) =>
          current.map((application) => ({
            ...application,
            questions: application.questions.map((question) =>
              question.id === String(currentQuestionId)
                ? {
                    ...question,
                    content: nextResult.sourceDraft ?? draftText,
                    washedKr: nextWashedText,
                  }
                : question
            ),
          }))
        )
      },
    })
  }

  const handleWash = async () => {
    if (!canWash) return

    if (hasConnectedQuestion) {
      const saved = await handleSaveDraft({ silent: true })
      if (!saved) return
    }

    const controller = new AbortController()
    abortRef.current = controller
    setIsWashing(true)
    setProgress(null)
    setResult(null)
    setErrorMessage(null)
    setCopied(false)
    setSelectedTitle("")
    setTitleCandidates([])
    setIsTitlePanelOpen(false)

    try {
      if (hasConnectedQuestion && questionDbId !== null) {
        await handleQuestionWash(controller, questionDbId, {
          seedEmptyFinal: finalText.trim().length === 0,
        })
        toast.success("WASH 원본을 갱신했습니다. 최종본은 유지했습니다.")
      } else {
        await handleStandaloneWash(controller)
        toast.success("WASH가 완료되었습니다.")
      }
    } catch (error) {
      if (isAbortError(error)) {
        toast.info("WASH를 중단했습니다.")
        return
      }

      const message = error instanceof Error ? error.message : "WASH 중 오류가 발생했습니다."
      setErrorMessage(message)
      toast.error(message)
    } finally {
      setIsWashing(false)
      abortRef.current = null
    }
  }

  const handleSaveFinal = async () => {
    if (!hasConnectedQuestion || questionDbId === null) {
      return
    }

    setIsSavingFinal(true)
    setErrorMessage(null)

    try {
      await saveFinalText(questionDbId, finalText, selectedTitle || null)
      setLoadedQuestion((current) =>
        current
          ? {
              ...current,
              savedFinal: finalText,
              hasSavedFinal: true,
            }
          : current
      )
      setApplications((current) =>
        current.map((application) => ({
          ...application,
          questions: application.questions.map((question) =>
            question.id === selectedQuestionId ? { ...question, finalText } : question
          ),
        }))
      )
      toast.success("최종 복붙본을 저장했습니다.")
    } catch (error) {
      const message = error instanceof Error ? error.message : "최종본 저장에 실패했습니다."
      setErrorMessage(message)
      toast.error(message)
    } finally {
      setIsSavingFinal(false)
    }
  }

  const handleCancel = () => {
    abortRef.current?.abort()
  }

  const handleCopy = async () => {
    const copyTarget = finalText.trim() ? finalText : washedText
    if (!copyTarget.trim()) return

    await navigator.clipboard.writeText(copyTarget)
    setCopied(true)
    toast.success("복사했습니다.")
    window.setTimeout(() => setCopied(false), 1600)
  }

  const handleFinalTextChange = (nextText: string) => {
    setFinalText(nextText)
    if (selectedTitle && !nextText.trimStart().startsWith(selectedTitle)) {
      setSelectedTitle("")
    }
  }

  const handleGenerateTitles = async () => {
    if (!canGenerateTitle || questionDbId === null) return

    setIsGeneratingTitle(true)
    setErrorMessage(null)
    setIsTitlePanelOpen(true)

    try {
      const data = await fetchTitleSuggestionsForDraft(questionDbId, titleSourceText)
      const candidates = data.candidates ?? []
      setTitleCandidates(candidates)
      setIsTitlePanelOpen(candidates.length > 0)

      if (candidates.length === 0) {
        toast.info("추천할 제목 후보를 찾지 못했습니다.")
      } else {
        toast.success("제목 후보를 생성했습니다.")
      }
    } catch (error) {
      const message = error instanceof Error ? error.message : "제목 생성에 실패했습니다."
      setErrorMessage(message)
      toast.error(message)
    } finally {
      setIsGeneratingTitle(false)
    }
  }

  const handleApplyTitleCandidate = (title: string) => {
    const sourceText = finalText.trim() ? finalText : washedText.trim() ? washedText : draftText
    setSelectedTitle(title)
    setFinalText(applyTitleLineToText(sourceText, title))
    setOutputTab("final")
    toast.success("제목을 최종본에 적용했습니다.")
  }

  const handleSwapFinalWithWash = () => {
    if (!washedText.trim() || isWashing) return

    setFinalText(washedText)
    setSelectedTitle("")
    setIsTitlePanelOpen(false)
    setOutputTab("final")
    toast.success("WASH 원본을 최종본에 갈아끼웠습니다.")
  }

  const handleReset = () => {
    abortRef.current?.abort()

    if (hasConnectedQuestion && questionDbId !== null) {
      void loadQuestion(questionDbId)
      return
    }

    setDraftText("")
    setWashedText("")
    setFinalText("")
    setSelectedTitle("")
    setTitleCandidates([])
    setIsTitlePanelOpen(false)
    setEnglishText("")
    setProgress(null)
    setResult(null)
    setErrorMessage(null)
    setCopied(false)
    setOutputTab("final")
  }

  return (
    <div className="flex h-full min-h-0 flex-col bg-background">
      <header className="shrink-0 border-b border-border bg-background/95 px-5 py-4 backdrop-blur lg:px-6">
        <div className="flex flex-col gap-4">
          <div className="flex flex-col gap-3 xl:flex-row xl:items-center xl:justify-between">
            <div className="min-w-0">
              <div className="flex flex-wrap items-center gap-2">
                <h1 className="text-xl font-semibold tracking-tight">WASH 작업실</h1>
                <Badge variant={hasConnectedQuestion ? "default" : "secondary"} className="rounded-full">
                  {hasConnectedQuestion ? "서류 대기 공고" : "임시 WASH"}
                </Badge>
                {isDraftDirty ? (
                  <Badge variant="outline" className="rounded-full border-amber-300 bg-amber-50 text-amber-700">
                    초안 변경됨
                  </Badge>
                ) : null}
              </div>
              <p className="mt-1 text-sm text-muted-foreground">
                ChatGPT 웹 초안을 문항에 저장하고, WASH 원본은 보관하면서 최종 복붙본을 중심으로 다듬습니다.
              </p>
            </div>

            <div className="flex flex-wrap items-center gap-2">
              {hasConnectedQuestion && selectedApplicationId ? (
                <Button asChild variant="outline" size="sm" className="h-9 gap-2 rounded-full">
                  <Link href={`/workspace/${selectedApplicationId}?questionId=${selectedQuestionId}`}>
                    <ExternalLink className="size-4" />
                    작업공간
                  </Link>
                </Button>
              ) : null}
              {hasConnectedQuestion && questionDbId !== null ? (
                <Button asChild variant="outline" size="sm" className="h-9 gap-2 rounded-full">
                  <Link href={`/workspace/edit/${questionDbId}`}>
                    <PencilLine className="size-4" />
                    최종 편집
                  </Link>
                </Button>
              ) : null}
              {isWashing ? (
                <Button variant="outline" size="sm" className="h-9 rounded-full" onClick={handleCancel}>
                  <X className="size-4" />
                  중단
                </Button>
              ) : (
                <Button
                  variant="outline"
                  size="sm"
                  className="h-9 rounded-full"
                  onClick={handleReset}
                  disabled={!draftText && !washedText && !finalText}
                >
                  <RotateCcw className="size-4" />
                  초기화
                </Button>
              )}
            </div>
          </div>

          <div className="grid gap-3 rounded-xl border border-border/80 bg-muted/20 p-3 shadow-sm lg:grid-cols-[minmax(0,0.95fr)_minmax(0,1.25fr)_auto] lg:items-stretch">
            <Select
              value={selectedApplicationId || "__standalone"}
              onValueChange={(value) => handleApplicationChange(value === "__standalone" ? "" : value)}
              disabled={isLoadingApplications || isWashing}
            >
              <SelectTrigger className="h-auto min-h-[72px] w-full rounded-lg border-border/80 bg-background px-4 py-3 text-left shadow-none hover:border-primary/35 focus:ring-primary/15">
                <div className="flex min-w-0 items-center gap-3">
                  <span className="flex size-10 shrink-0 items-center justify-center rounded-md bg-primary/10 text-primary">
                    <Building2 className="size-5" />
                  </span>
                  <span className="min-w-0">
                    <span className="block text-[11px] font-semibold uppercase tracking-wide text-muted-foreground">
                      공고
                    </span>
                    <SelectValue>
                      <span className="block truncate text-sm font-semibold text-foreground">
                        {selectedApplicationTitle}
                      </span>
                    </SelectValue>
                    <span className="mt-0.5 block truncate text-xs text-muted-foreground">
                      서류 전형 · 대기중인 공고만 표시
                    </span>
                  </span>
                </div>
              </SelectTrigger>
              <SelectContent align="start" className="max-h-[360px] rounded-xl">
                <SelectItem value="__standalone" className="py-3">
                  <div className="flex flex-col">
                    <span className="font-medium">공고 선택 없이 임시 WASH</span>
                    <span className="text-xs text-muted-foreground">저장 없이 텍스트만 세탁합니다.</span>
                  </div>
                </SelectItem>
                {applications.map((application) => (
                  <SelectItem key={application.id} value={application.id} className="py-3">
                    <div className="flex min-w-0 flex-col">
                      <span className="truncate font-medium">{application.companyName}</span>
                      <span className="truncate text-xs text-muted-foreground">
                        {application.position || "직무 미입력"} · 문항 {application.questions.length}개
                      </span>
                    </div>
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>

            <Select
              value={selectedQuestionId || "__none"}
              onValueChange={(value) => handleQuestionChange(value === "__none" ? "" : value)}
              disabled={!selectedApplication || isLoadingQuestion || isWashing}
            >
              <SelectTrigger className="h-auto min-h-[72px] w-full rounded-lg border-border/80 bg-background px-4 py-3 text-left shadow-none hover:border-primary/35 focus:ring-primary/15">
                <div className="flex min-w-0 items-center gap-3">
                  <span className="flex size-10 shrink-0 items-center justify-center rounded-md bg-emerald-500/10 text-emerald-600">
                    <ListChecks className="size-5" />
                  </span>
                  <span className="min-w-0">
                    <span className="block text-[11px] font-semibold uppercase tracking-wide text-muted-foreground">
                      문항
                    </span>
                    <SelectValue>
                      <span className="block truncate text-sm font-semibold text-foreground">
                        {selectedQuestionNumber ? `${selectedQuestionNumber}. ${selectedQuestionTitle}` : "문항을 선택하세요"}
                      </span>
                    </SelectValue>
                    <span className="mt-0.5 block truncate text-xs text-muted-foreground">
                      {maxLength ? `제한 ${maxLength.toLocaleString()}자 · 현재 ${draftChars.toLocaleString()}자` : "공고를 먼저 선택하세요"}
                    </span>
                  </span>
                </div>
              </SelectTrigger>
              <SelectContent align="start" className="max-h-[420px] rounded-xl">
                <SelectItem value="__none" className="py-3">
                  <span className="text-muted-foreground">문항 선택</span>
                </SelectItem>
                {selectedApplication?.questions.map((question, index) => (
                  <SelectItem key={question.id} value={question.id} className="py-3">
                    <div className="flex min-w-0 flex-col">
                      <span className="truncate font-medium">
                        {index + 1}. {question.title || "제목 없는 문항"}
                      </span>
                      <span className="text-xs text-muted-foreground">
                        {question.maxLength.toLocaleString()}자 · 초안 {countResumeCharacters(question.content).toLocaleString()}자
                      </span>
                    </div>
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>

            <div className="grid min-w-[260px] grid-cols-2 gap-2 lg:flex lg:items-center">
              <Button
                variant="outline"
                onClick={() => void handleSaveDraft()}
                disabled={!hasConnectedQuestion || !isDraftDirty || isSavingDraft || isWashing}
                className="h-10 gap-2 rounded-full"
              >
                {isSavingDraft ? <Loader2 className="size-4 animate-spin" /> : <Save className="size-4" />}
                초안 저장
              </Button>
              <Button onClick={handleWash} disabled={!canWash} className="h-10 gap-2 rounded-full">
                {isWashing ? <Loader2 className="size-4 animate-spin" /> : <Waves className="size-4" />}
                WASH 실행
              </Button>
              <Button
                variant="outline"
                onClick={() => void handleSaveFinal()}
                disabled={!hasConnectedQuestion || !isFinalDirty || isSavingFinal || isWashing}
                className="h-10 gap-2 rounded-full"
              >
                {isSavingFinal ? <Loader2 className="size-4 animate-spin" /> : <ClipboardCheck className="size-4" />}
                최종본 저장
              </Button>
              <Button
                variant="secondary"
                onClick={handleCopy}
                disabled={!finalText.trim() && !washedText.trim()}
                className="h-10 gap-2 rounded-full"
              >
                {copied ? <CheckCircle2 className="size-4 text-emerald-600" /> : <Copy className="size-4" />}
                {copySourceLabel} 복사
              </Button>
            </div>
          </div>

          <div className="grid gap-2 lg:grid-cols-3">
            <CharMeter
              label="ChatGPT 초안"
              chars={draftChars}
              limit={charLimit}
              emphasis="draft"
              icon={<FileText className="size-4" />}
            />
            <CharMeter
              label="WASH 원본"
              chars={washedChars}
              limit={charLimit}
              emphasis="wash"
              icon={<Waves className="size-4" />}
            />
            <CharMeter
              label="최종 복붙본"
              chars={finalChars}
              limit={charLimit}
              emphasis="final"
              icon={<ClipboardCheck className="size-4" />}
            />
          </div>

          {hasConnectedQuestion ? (
            <div className="flex items-start gap-2 rounded-lg border border-amber-200 bg-amber-50/70 px-3 py-2 text-xs text-amber-800">
              <AlertTriangle className="mt-0.5 size-3.5 shrink-0" />
              <p>
                초안 저장과 WASH 재실행은 최종 복붙본을 자동으로 바꾸지 않습니다. 새 결과는
                WASH 원본 탭에만 갱신되며, 필요한 문장만 직접 가져와 최종본에 반영하세요.
              </p>
            </div>
          ) : null}
        </div>
      </header>

      <main className="grid min-h-0 flex-1 grid-rows-[auto_1fr] bg-muted/25">
        <section className="border-b border-border bg-background/80 px-5 py-3 lg:px-6">
          <div className="flex flex-col gap-3 xl:flex-row xl:items-center xl:justify-between">
            <div className="flex min-w-0 items-start gap-2">
              {errorMessage ? (
                <AlertTriangle className="mt-0.5 size-4 shrink-0 text-destructive" />
              ) : (
                <Sparkles className="mt-0.5 size-4 shrink-0 text-primary" />
              )}
              <p className={`text-sm ${errorMessage || isOverStandaloneLimit ? "text-destructive" : "text-muted-foreground"}`}>
                {statusText}
              </p>
            </div>

            <div className="grid gap-2 sm:grid-cols-2 xl:min-w-[780px] xl:grid-cols-5">
              {progressSteps.map((step, index) => {
                const isDone =
                  activeStepIndex > index ||
                  (hasConnectedQuestion
                    ? normalizeStage(progress?.stage) === "DONE" && index === progressSteps.length - 1
                    : !!result && index === progressSteps.length - 1)
                const isActive = activeStepIndex === index && isWashing

                return (
                  <div
                    key={step.stage}
                    className={`flex min-w-0 items-center gap-2 rounded-md border px-3 py-2 transition-colors ${
                      isActive
                        ? "border-primary/40 bg-primary/5"
                        : isDone
                          ? "border-emerald-200 bg-emerald-50 text-emerald-900"
                          : "border-border bg-background"
                    }`}
                  >
                    <span
                      className={`flex size-6 shrink-0 items-center justify-center rounded-full ${
                        isActive
                          ? "bg-primary text-primary-foreground"
                          : isDone
                            ? "bg-emerald-600 text-white"
                            : "bg-muted text-muted-foreground"
                      }`}
                    >
                      {isActive ? (
                        <Loader2 className="size-3.5 animate-spin" />
                      ) : isDone ? (
                        <CheckCircle2 className="size-3.5" />
                      ) : (
                        <span className="text-[11px] font-semibold">{index + 1}</span>
                      )}
                    </span>
                    <span className="min-w-0">
                      <span className="block truncate text-xs font-semibold">{step.label}</span>
                      <span className="block truncate text-[11px] text-muted-foreground">
                        {step.description}
                      </span>
                    </span>
                  </div>
                )
              })}
            </div>
          </div>
        </section>

        <section className="grid min-h-0 gap-4 p-4 xl:grid-cols-[minmax(0,1fr)_minmax(0,1fr)]">
          <div className="flex min-h-[360px] min-w-0 flex-col overflow-hidden rounded-lg border border-border bg-background">
            <div className="flex shrink-0 items-start justify-between gap-3 border-b border-border px-4 py-3">
              <div className="flex min-w-0 items-start gap-2">
                <FileText className="mt-0.5 size-4 shrink-0 text-muted-foreground" />
                <div className="min-w-0">
                  <p className="truncate text-sm font-semibold">ChatGPT 초안</p>
                  <p className="text-xs text-muted-foreground">
                    {hasConnectedQuestion
                      ? "이 본문이 선택한 문항의 원문 초안으로 저장됩니다."
                      : "공고를 선택하지 않으면 임시 WASH 입력으로만 사용됩니다."}
                  </p>
                </div>
              </div>
              <span className={`shrink-0 rounded-md bg-muted px-3 py-1.5 text-sm font-semibold tabular-nums ${isOverStandaloneLimit ? "text-destructive" : "text-foreground"}`}>
                {draftChars.toLocaleString()}
                {hasConnectedQuestion && maxLength ? ` / ${maxLength.toLocaleString()}` : ` / ${MAX_DRAFT_LENGTH.toLocaleString()}`}
                <span className="ml-1 text-xs font-medium text-muted-foreground">자</span>
              </span>
            </div>
            <Textarea
              value={draftText}
              onChange={(event) => setDraftText(event.target.value)}
              placeholder="ChatGPT 웹에서 생성한 문항 답변 초안을 붙여넣으세요."
              disabled={isLoadingQuestion}
              className="min-h-0 flex-1 resize-none rounded-none border-0 bg-transparent px-5 py-4 text-[15px] leading-8 shadow-none focus-visible:ring-0"
            />
          </div>

          <div className="flex min-h-[360px] min-w-0 flex-col overflow-hidden rounded-lg border border-border bg-background">
            <Tabs value={outputTab} onValueChange={(value) => setOutputTab(value as "final" | "wash")} className="min-h-0 flex-1 gap-0">
              <div className="flex shrink-0 flex-col gap-3 border-b border-border px-4 py-3 lg:flex-row lg:items-center lg:justify-between">
                <div className="flex min-w-0 items-start gap-2">
                  <PencilLine className="mt-0.5 size-4 shrink-0 text-emerald-600" />
                  <div className="min-w-0">
                    <p className="truncate text-sm font-semibold">최종 복붙본</p>
                    <p className="text-xs text-muted-foreground">
                      제출용 편집본이 기본 작업면입니다. WASH를 다시 실행해도 자동 변경되지 않습니다.
                    </p>
                  </div>
                </div>
                <div className="flex shrink-0 flex-wrap items-center gap-2">
                  <TabsList className="h-8 rounded-full">
                    <TabsTrigger value="final" className="rounded-full px-3 text-xs">
                      최종본
                    </TabsTrigger>
                    <TabsTrigger value="wash" className="rounded-full px-3 text-xs">
                      WASH 원본
                    </TabsTrigger>
                  </TabsList>
                  <Button
                    variant="outline"
                    size="sm"
                    className="h-8 rounded-full px-3 text-xs font-semibold"
                    disabled={!canGenerateTitle || isGeneratingTitle}
                    onClick={() => void handleGenerateTitles()}
                  >
                    {isGeneratingTitle ? <Loader2 className="size-3.5 animate-spin" /> : <Sparkles className="size-3.5" />}
                    제목 생성
                  </Button>
                  <Button
                    variant="default"
                    size="sm"
                    className="h-8 rounded-full px-3 text-xs font-semibold shadow-sm"
                    disabled={!washedText.trim() || isWashing}
                    onClick={handleSwapFinalWithWash}
                  >
                    <ArrowRightLeft className="size-3.5" />
                    WASH본 갈아끼우기
                  </Button>
                  <span className="rounded-md bg-muted px-3 py-1.5 text-sm font-semibold tabular-nums text-foreground">
                    {outputTab === "final" ? finalChars.toLocaleString() : washedChars.toLocaleString()}
                    <span className="ml-1 text-xs font-medium text-muted-foreground">자</span>
                  </span>
                </div>
              </div>

              {isGeneratingTitle || titleCandidates.length > 0 ? (
                <Collapsible
                  open={isTitlePanelOpen}
                  onOpenChange={setIsTitlePanelOpen}
                  className="shrink-0 border-b border-border/70 bg-muted/20"
                >
                  <CollapsibleTrigger asChild>
                    <button
                      type="button"
                      className="flex min-h-11 w-full items-center justify-between gap-3 px-4 py-2 text-left transition-colors hover:bg-muted/40"
                    >
                      <span className="flex min-w-0 items-center gap-2">
                        <span className="shrink-0 text-xs font-semibold uppercase tracking-wider text-muted-foreground">
                          제목 후보
                        </span>
                        <Badge variant="secondary" className="shrink-0 rounded-full text-[10px]">
                          {isGeneratingTitle ? "생성 중" : `${titleCandidates.length}개`}
                        </Badge>
                        {topTitleCandidate ? (
                          <span className="truncate text-xs text-muted-foreground">
                            {topTitleCandidate.title}
                          </span>
                        ) : null}
                      </span>
                      <ChevronDown
                        className={cn(
                          "size-4 shrink-0 text-muted-foreground transition-transform",
                          isTitlePanelOpen && "rotate-180"
                        )}
                      />
                    </button>
                  </CollapsibleTrigger>
                  <CollapsibleContent>
                    <div className="flex flex-col gap-2 px-4 pb-3">
                      <div className="flex justify-end">
                        <Badge variant="outline" className="rounded-full text-[10px]">
                          현재 본문 기준
                        </Badge>
                      </div>
                      {isGeneratingTitle ? (
                        <div className="grid gap-2 lg:grid-cols-3">
                          {[0, 1, 2].map((index) => (
                            <div
                              key={index}
                              className="min-h-[74px] rounded-lg border border-border/60 bg-background/60"
                            />
                          ))}
                        </div>
                      ) : (
                        <div className="grid gap-2 lg:grid-cols-3">
                          {titleCandidates.slice(0, 3).map((candidate, index) => {
                            const isSelected = selectedTitle === candidate.title
                            return (
                              <button
                                key={`${candidate.title}-${index}`}
                                type="button"
                                className={cn(
                                  "min-h-[74px] rounded-lg border px-3 py-2.5 text-left transition-colors",
                                  isSelected
                                    ? "border-primary bg-primary/5"
                                    : "border-border/70 bg-background hover:border-primary/35 hover:bg-muted/30"
                                )}
                                onClick={() => handleApplyTitleCandidate(candidate.title)}
                              >
                                <div className="mb-1.5 flex items-center justify-between gap-2">
                                  <Badge
                                    variant={candidate.recommended ? "default" : "secondary"}
                                    className="rounded-full text-[10px]"
                                  >
                                    {candidate.recommended ? "추천" : `${index + 1}안`}
                                  </Badge>
                                  {isSelected ? <CheckCircle2 className="size-3.5 shrink-0 text-primary" /> : null}
                                </div>
                                <p className="line-clamp-2 text-sm font-semibold leading-5 text-foreground">
                                  {candidate.title}
                                </p>
                                <p className="mt-1 line-clamp-1 text-xs leading-5 text-muted-foreground">
                                  {candidate.reason}
                                </p>
                              </button>
                            )
                          })}
                        </div>
                      )}
                    </div>
                  </CollapsibleContent>
                </Collapsible>
              ) : null}

              <TabsContent value="final" className="min-h-0 flex-1 data-[state=inactive]:hidden">
                <div className="flex h-full min-h-0 flex-col">
                  <Textarea
                    value={finalText}
                    onChange={(event) => handleFinalTextChange(event.target.value)}
                    placeholder="WASH 결과를 다듬어 채용 플랫폼에 붙여넣을 최종본으로 저장하세요."
                    className="min-h-0 flex-1 resize-none rounded-none border-0 bg-emerald-500/[0.025] px-5 py-4 text-[15px] leading-8 shadow-none focus-visible:ring-0"
                  />
                  <div className="flex shrink-0 items-center justify-between border-t border-border/70 px-4 py-2 text-xs text-muted-foreground">
                    <span>{loadedQuestion?.hasSavedFinal ? "저장된 최종본이 있습니다." : "저장 전에는 WASH 결과가 복사 fallback으로 사용됩니다."}</span>
                  </div>
                </div>
              </TabsContent>

              <TabsContent value="wash" className="min-h-0 flex-1 data-[state=inactive]:hidden">
                <div className="flex h-full min-h-0 flex-col">
                  <Textarea
                    value={washedText}
                    readOnly
                    placeholder={isWashing ? "WASH 중입니다..." : "WASH 결과가 여기에 표시됩니다."}
                    className="min-h-0 flex-1 resize-none rounded-none border-0 bg-primary/[0.025] px-5 py-4 text-[15px] leading-8 shadow-none focus-visible:ring-0"
                  />
                  <div className="flex shrink-0 items-center justify-between border-t border-border/70 px-4 py-2 text-xs text-muted-foreground">
                    <span>파이프라인 산출물 원본입니다. 제출 문구 수정은 최종본 탭에서 합니다.</span>
                  </div>
                </div>
              </TabsContent>
            </Tabs>
          </div>
        </section>
      </main>

      {englishText ? (
        <footer className="shrink-0 border-t border-border bg-background px-6 py-3">
          <details className="group">
            <summary className="flex cursor-pointer list-none items-center gap-2 text-xs font-medium text-muted-foreground transition-colors hover:text-foreground">
              <Languages className="size-3.5" />
              중간 영어 번역 보기
              <RefreshCw className="ml-auto size-3.5 opacity-0 transition-opacity group-open:opacity-100" />
            </summary>
            <p className="mt-2 max-h-24 overflow-y-auto rounded-md bg-muted px-3 py-2 text-xs leading-5 text-muted-foreground">
              {englishText}
            </p>
          </details>
        </footer>
      ) : null}
    </div>
  )
}
