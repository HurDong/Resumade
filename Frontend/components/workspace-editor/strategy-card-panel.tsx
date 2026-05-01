"use client"

import { useEffect, useMemo, useState } from "react"
import { AlertTriangle, CheckCircle2, FileSearch, Loader2, RefreshCw, Trash2, Wand2 } from "lucide-react"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Textarea } from "@/components/ui/textarea"
import { Separator } from "@/components/ui/separator"
import { toApiUrl } from "@/lib/network/api-base"
import { streamSse } from "@/lib/network/stream-sse"
import { useWorkspaceStore } from "@/lib/store/workspace-store"
import type {
  QuestionStrategyCard,
  QuestionStrategyCardCandidate,
  QuestionStrategyCardRecord,
} from "@/lib/workspace/types"
import { cn } from "@/lib/utils"

const STAGE_LABEL: Record<string, string> = {
  START: "전략카드 생성을 시작합니다.",
  PLANNING: "문항 의도와 경험 근거를 설계하고 있습니다.",
  STRUCTURING: "초안 지시문과 미리보기를 정리하고 있습니다.",
}

export function StrategyCardPanel() {
  const applicationId = useWorkspaceStore((state) => state.applicationId)
  const questions = useWorkspaceStore((state) => state.questions)
  const activeQuestionId = useWorkspaceStore((state) => state.activeQuestionId)
  const updateActiveQuestion = useWorkspaceStore((state) => state.updateActiveQuestion)
  const isProcessing = useWorkspaceStore((state) => state.isProcessing)
  const isBatchRunning = useWorkspaceStore((state) => state.isBatchRunning)

  const activeQuestion = useMemo(
    () => questions.find((question) => question.id === activeQuestionId) ?? null,
    [questions, activeQuestionId]
  )
  const questionDbId = activeQuestion?.dbId ?? null

  const [activeRecord, setActiveRecord] = useState<QuestionStrategyCardRecord | null>(null)
  const [candidate, setCandidate] = useState<QuestionStrategyCardCandidate | null>(null)
  const [progressMessage, setProgressMessage] = useState("")
  const [error, setError] = useState<string | null>(null)
  const [isLoading, setIsLoading] = useState(false)
  const [reviewNote, setReviewNote] = useState("")
  const [isSavingNote, setIsSavingNote] = useState(false)

  const candidateRecord = useMemo(() => {
    if (!candidate || !questionDbId) {
      return null
    }
    return candidate.cards.find((card) => card.questionId === questionDbId) ?? candidate.cards[0] ?? null
  }, [candidate, questionDbId])

  useEffect(() => {
    let cancelled = false
    setCandidate(null)
    setError(null)
    setProgressMessage("")

    async function loadActiveCard() {
      if (!questionDbId) {
        setActiveRecord(null)
        setReviewNote("")
        return
      }

      try {
        const response = await fetch(toApiUrl(`/api/workspace/questions/${questionDbId}/strategy-card`), {
          cache: "no-store",
        })
        if (cancelled) {
          return
        }
        if (response.status === 204) {
          setActiveRecord(null)
          setReviewNote("")
          return
        }
        if (!response.ok) {
          throw new Error("Failed to load strategy card")
        }
        const data = (await response.json()) as QuestionStrategyCardRecord
        setActiveRecord(data)
        setReviewNote(data.reviewNote ?? "")
      } catch (loadError) {
        if (!cancelled) {
          console.warn("Strategy card unavailable", loadError)
          setActiveRecord(null)
          setReviewNote("")
        }
      }
    }

    void loadActiveCard()
    return () => {
      cancelled = true
    }
  }, [questionDbId])

  const handleGenerate = async () => {
    if (!questionDbId) {
      return
    }
    setIsLoading(true)
    setCandidate(null)
    setError(null)
    setProgressMessage("전략카드 생성을 준비하고 있습니다.")

    try {
      const initResponse = await fetch(toApiUrl(`/api/workspace/questions/${questionDbId}/strategy-card/init`), {
        method: "POST",
        headers: { "Content-Type": "application/json" },
      })
      if (!initResponse.ok) {
        throw new Error(await initResponse.text().catch(() => "Failed to initialize strategy card"))
      }
      const { uuid } = (await initResponse.json()) as { uuid: string }
      let nextCandidate: QuestionStrategyCardCandidate | null = null
      let streamError: string | null = null

      await streamSse({
        url: toApiUrl(`/api/workspace/strategy-card/stream/${uuid}`),
        onEvent: ({ event, data }) => {
          if (event === "COMPLETE") {
            nextCandidate = JSON.parse(data) as QuestionStrategyCardCandidate
            return
          }
          if (event === "ERROR") {
            streamError = parseSseError(data)
            return
          }
          setProgressMessage(STAGE_LABEL[event] ?? parseSseError(data) ?? event)
        },
      })

      if (streamError) {
        throw new Error(streamError)
      }
      if (!nextCandidate) {
        throw new Error("Strategy card stream ended without a result.")
      }

      setCandidate(nextCandidate)
      setProgressMessage("")
    } catch (generateError) {
      setError(generateError instanceof Error ? generateError.message : "전략카드 생성에 실패했습니다.")
    } finally {
      setIsLoading(false)
    }
  }

  const handleActivate = async () => {
    if (!questionDbId || !candidate?.uuid) {
      return
    }
    setIsLoading(true)
    setError(null)
    try {
      const response = await fetch(toApiUrl(`/api/workspace/questions/${questionDbId}/strategy-card/activate`), {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ uuid: candidate.uuid }),
      })
      if (!response.ok) {
        throw new Error(await response.text().catch(() => "Failed to activate strategy card"))
      }
      const data = (await response.json()) as QuestionStrategyCardRecord
      setActiveRecord(data)
      setCandidate(null)
      setReviewNote(data.reviewNote ?? "")
      updateActiveQuestion({ batchStrategyDirective: data.directivePrefix || data.card.draftDirective || "" })
    } catch (activateError) {
      setError(activateError instanceof Error ? activateError.message : "전략카드 적용에 실패했습니다.")
    } finally {
      setIsLoading(false)
    }
  }

  const handleDiscard = async () => {
    if (!questionDbId || !candidate?.uuid) {
      setCandidate(null)
      return
    }
    await fetch(toApiUrl(`/api/workspace/questions/${questionDbId}/strategy-card/candidate/${candidate.uuid}`), {
      method: "DELETE",
    }).catch(() => undefined)
    setCandidate(null)
    setProgressMessage("")
    setError(null)
  }

  const handleSaveReviewNote = async () => {
    if (!questionDbId || !activeRecord) {
      return
    }
    setIsSavingNote(true)
    setError(null)
    try {
      const response = await fetch(toApiUrl(`/api/workspace/questions/${questionDbId}/strategy-card/review-note`), {
        method: "PATCH",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ reviewNote }),
      })
      if (!response.ok) {
        throw new Error(await response.text().catch(() => "Failed to save review note"))
      }
      const data = (await response.json()) as QuestionStrategyCardRecord
      setActiveRecord(data)
      setReviewNote(data.reviewNote ?? "")
    } catch (saveError) {
      setError(saveError instanceof Error ? saveError.message : "검수 메모 저장에 실패했습니다.")
    } finally {
      setIsSavingNote(false)
    }
  }

  const displayedRecord = candidateRecord ?? activeRecord
  const displayedCard = displayedRecord?.card ?? null
  const isCandidateMode = Boolean(candidateRecord)
  const disabled = !applicationId || !questionDbId || isProcessing || isBatchRunning || isLoading

  return (
    <section className="min-w-0 overflow-hidden">
      <div className="mb-3 flex items-center gap-3">
        <div className="flex size-10 shrink-0 items-center justify-center rounded-2xl bg-primary/10 text-primary">
          <FileSearch className="size-4" />
        </div>
        <div className="min-w-0">
          <p className="text-[11px] font-black uppercase tracking-[0.24em] text-primary/70">전략카드</p>
          <h3 className="truncate text-sm font-black tracking-tight text-foreground">문항별 작성 전략</h3>
        </div>
      </div>

      <div className="flex flex-col gap-3 rounded-2xl border border-border/60 bg-muted/20 p-4">
        {!displayedCard ? (
          <div className="flex flex-col gap-3">
            <p className="text-xs leading-6 text-muted-foreground">
              Fit 프로필, JD, RAG 경험을 묶어 초안 생성 전에 쓸 주장과 근거를 먼저 정합니다. 전략 없이도 기존 초안 생성은 계속 사용할 수 있습니다.
            </p>
            <Button size="sm" onClick={handleGenerate} disabled={disabled} className="w-fit gap-1.5">
              {isLoading ? <Loader2 className="size-3.5 animate-spin" /> : <Wand2 className="size-3.5" />}
              전략카드 생성
            </Button>
          </div>
        ) : (
          <div className="flex flex-col gap-4">
            <StrategyCardPreview card={displayedCard} isCandidate={isCandidateMode} />

            {isCandidateMode ? (
              <div className="flex flex-wrap gap-2">
                <Button size="sm" onClick={handleActivate} disabled={disabled} className="gap-1.5">
                  <CheckCircle2 className="size-3.5" />
                  적용
                </Button>
                <Button size="sm" variant="outline" onClick={handleDiscard} disabled={isLoading} className="gap-1.5">
                  <Trash2 className="size-3.5" />
                  폐기
                </Button>
                <Button size="sm" variant="ghost" onClick={handleGenerate} disabled={disabled} className="gap-1.5">
                  <RefreshCw className="size-3.5" />
                  다시 생성
                </Button>
              </div>
            ) : (
              <div className="flex flex-col gap-2">
                <div className="flex flex-wrap gap-2">
                  <Badge variant="secondary" className="w-fit rounded-full">
                    적용됨
                  </Badge>
                  {displayedRecord?.modelName ? (
                    <Badge variant="outline" className="w-fit rounded-full">
                      {displayedRecord.modelName}
                    </Badge>
                  ) : null}
                </div>
                <Textarea
                  value={reviewNote}
                  onChange={(event) => setReviewNote(event.target.value)}
                  placeholder="검수 메모를 남겨두면 다음 초안 생성 전에 사람이 확인한 기준으로 참고할 수 있습니다."
                  className="min-h-20 resize-none bg-background text-xs leading-6"
                />
                <div className="flex flex-wrap gap-2">
                  <Button size="sm" variant="outline" onClick={handleSaveReviewNote} disabled={isSavingNote}>
                    {isSavingNote ? "저장 중..." : "검수 메모 저장"}
                  </Button>
                  <Button size="sm" variant="ghost" onClick={handleGenerate} disabled={disabled} className="gap-1.5">
                    <RefreshCw className="size-3.5" />
                    새 후보 생성
                  </Button>
                </div>
              </div>
            )}
          </div>
        )}

        {isLoading && progressMessage ? (
          <p className="rounded-xl border border-primary/15 bg-background px-3 py-2 text-xs font-medium text-primary">
            {progressMessage}
          </p>
        ) : null}

        {error ? (
          <div className="flex items-start gap-2 rounded-xl border border-destructive/20 bg-destructive/5 px-3 py-2 text-xs text-destructive">
            <AlertTriangle className="mt-0.5 size-3.5 shrink-0" />
            <span>{error}</span>
          </div>
        ) : null}
      </div>
    </section>
  )
}

function StrategyCardPreview({ card, isCandidate }: { card: QuestionStrategyCard; isCandidate: boolean }) {
  return (
    <div className="flex flex-col gap-3">
      <div className="flex flex-wrap items-center gap-2">
        <Badge variant={isCandidate ? "outline" : "secondary"} className="rounded-full">
          {isCandidate ? "후보" : "활성"}
        </Badge>
        <Badge variant="outline" className="rounded-full">
          {card.category}
        </Badge>
      </div>
      <div>
        <p className="text-sm font-black leading-6 text-foreground">{card.summary}</p>
        <p className="mt-1 text-xs leading-6 text-muted-foreground">{card.intent?.detail}</p>
      </div>
      <Separator />
      <InfoBlock title="핵심 주장" items={[card.primaryClaim]} strong />
      <InfoBlock title="사용할 경험" items={[...(card.experiencePlan?.primaryExperiences ?? []), ...(card.experiencePlan?.facets ?? [])]} />
      <InfoBlock title="회사 Fit 연결" items={[card.fitConnection?.domainBridge, ...(card.fitConnection?.lexicon ?? [])]} />
      <div className="flex flex-col gap-2">
        <p className="text-[11px] font-black uppercase tracking-[0.18em] text-muted-foreground">문단 구성</p>
        <div className="flex flex-col gap-2">
          {(card.paragraphPlan ?? []).map((paragraph) => (
            <div key={paragraph.paragraph} className="rounded-xl border border-border/60 bg-background px-3 py-2">
              <div className="flex items-center gap-2">
                <Badge variant="outline" className="rounded-full text-[10px]">
                  {paragraph.paragraph}문단
                </Badge>
                <span className="text-xs font-bold text-foreground">{paragraph.role}</span>
              </div>
              {paragraph.contents?.length ? (
                <p className="mt-1 line-clamp-2 text-xs leading-5 text-muted-foreground">
                  {paragraph.contents.join(" · ")}
                </p>
              ) : null}
            </div>
          ))}
        </div>
      </div>
      <InfoBlock title="주의" items={[...(card.warnings ?? []), ...(card.confidenceNotes ?? [])]} warning />
    </div>
  )
}

function InfoBlock({
  title,
  items,
  strong = false,
  warning = false,
}: {
  title: string
  items: Array<string | null | undefined>
  strong?: boolean
  warning?: boolean
}) {
  const normalized = items.filter((item): item is string => Boolean(item?.trim())).slice(0, 5)
  if (!normalized.length) {
    return null
  }

  return (
    <div className="flex flex-col gap-2">
      <p
        className={cn(
          "text-[11px] font-black uppercase tracking-[0.18em]",
          warning ? "text-amber-700" : "text-muted-foreground"
        )}
      >
        {title}
      </p>
      <div className="flex flex-col gap-1.5">
        {normalized.map((item) => (
          <div
            key={item}
            className={cn(
              "rounded-xl border px-3 py-2 text-xs leading-5",
              warning
                ? "border-amber-200 bg-amber-50 text-amber-950"
                : "border-border/60 bg-background text-muted-foreground",
              strong && "font-semibold text-foreground"
            )}
          >
            {item}
          </div>
        ))}
      </div>
    </div>
  )
}

function parseSseError(raw: string) {
  if (!raw) {
    return ""
  }
  try {
    const parsed = JSON.parse(raw)
    if (typeof parsed === "string") {
      return parsed
    }
    return parsed?.message ?? parsed?.data ?? raw
  } catch {
    return raw
  }
}
