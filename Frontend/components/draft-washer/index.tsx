"use client"

import { useMemo, useRef, useState } from "react"
import {
  CheckCircle2,
  Clipboard,
  Copy,
  FileText,
  Languages,
  Loader2,
  PencilLine,
  RotateCcw,
  Sparkles,
  X,
} from "lucide-react"
import { toast } from "sonner"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Textarea } from "@/components/ui/textarea"
import {
  streamDraftWash,
  type DraftWashProgress,
  type DraftWashResult,
  type DraftWashStage,
} from "@/lib/api/draft-wash"

const MAX_DRAFT_LENGTH = 12_000

const steps: Array<{
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
    description: "문장을 한 번 낯설게 풀어냅니다.",
  },
  {
    stage: "toKorean",
    label: "한국어 재번역",
    description: "다시 자연스러운 한국어로 돌립니다.",
  },
  {
    stage: "ready",
    label: "편집 가능",
    description: "결과를 에디터에 올립니다.",
  },
]

function countChars(text: string) {
  return text.replace(/\r\n/g, "\n").replace(/\r/g, "\n").length
}

function stageIndex(stage: DraftWashStage | null) {
  if (!stage) return -1
  return steps.findIndex((step) => step.stage === stage)
}

function isAbortError(error: unknown) {
  return error instanceof DOMException && error.name === "AbortError"
}

export function DraftWasher() {
  const [draftText, setDraftText] = useState("")
  const [washedText, setWashedText] = useState("")
  const [englishText, setEnglishText] = useState("")
  const [progress, setProgress] = useState<DraftWashProgress | null>(null)
  const [result, setResult] = useState<DraftWashResult | null>(null)
  const [errorMessage, setErrorMessage] = useState<string | null>(null)
  const [isWashing, setIsWashing] = useState(false)
  const [copied, setCopied] = useState(false)
  const abortRef = useRef<AbortController | null>(null)

  const draftChars = countChars(draftText)
  const washedChars = countChars(washedText)
  const activeStepIndex = stageIndex(progress?.stage ?? null)
  const isOverLimit = draftChars > MAX_DRAFT_LENGTH
  const canWash = draftText.trim().length > 0 && !isOverLimit && !isWashing

  const statusText = useMemo(() => {
    if (isOverLimit) return "12,000자 이하로 줄여 주세요."
    if (errorMessage) return errorMessage
    if (progress) return progress.message
    if (result) return "WASH본을 바로 수정할 수 있습니다."
    return "웹 LLM 초안을 붙여넣고 WASH를 누르세요."
  }, [errorMessage, isOverLimit, progress, result])

  const handleWash = async () => {
    if (!canWash) return

    const controller = new AbortController()
    abortRef.current = controller
    setIsWashing(true)
    setProgress(null)
    setResult(null)
    setErrorMessage(null)
    setCopied(false)

    try {
      await streamDraftWash({
        draftText,
        signal: controller.signal,
        onProgress: setProgress,
        onComplete: (nextResult) => {
          setResult(nextResult)
          setEnglishText(nextResult.englishText ?? "")
          setWashedText(nextResult.washedText ?? "")
        },
      })

      toast.success("WASH가 완료되었습니다.")
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

  const handleCancel = () => {
    abortRef.current?.abort()
  }

  const handleCopy = async () => {
    if (!washedText.trim()) return

    await navigator.clipboard.writeText(washedText)
    setCopied(true)
    toast.success("WASH본을 복사했습니다.")
    window.setTimeout(() => setCopied(false), 1600)
  }

  const handleReset = () => {
    abortRef.current?.abort()
    setDraftText("")
    setWashedText("")
    setEnglishText("")
    setProgress(null)
    setResult(null)
    setErrorMessage(null)
    setCopied(false)
  }

  return (
    <div className="flex h-full min-h-0 flex-col bg-background">
      <header className="shrink-0 border-b border-border bg-background px-6 py-4">
        <div className="flex flex-col gap-4 lg:flex-row lg:items-center lg:justify-between">
          <div className="min-w-0">
            <div className="flex items-center gap-2">
              <h1 className="text-xl font-semibold tracking-tight">WASH 임시 에디터</h1>
              <Badge variant="secondary" className="rounded-full">
                Sub service
              </Badge>
            </div>
            <p className="mt-1 text-sm text-muted-foreground">
              초안을 영어로 번역한 뒤 다시 한국어로 돌려 기계적인 문장감을 낮춥니다.
            </p>
          </div>

          <div className="flex flex-wrap items-center gap-2">
            {isWashing ? (
              <Button variant="outline" onClick={handleCancel}>
                <X className="size-4" />
                중단
              </Button>
            ) : (
              <Button variant="outline" onClick={handleReset} disabled={!draftText && !washedText}>
                <RotateCcw className="size-4" />
                초기화
              </Button>
            )}
            <Button onClick={handleWash} disabled={!canWash} className="min-w-28">
              {isWashing ? <Loader2 className="size-4 animate-spin" /> : <Sparkles className="size-4" />}
              WASH
            </Button>
            <Button variant="secondary" onClick={handleCopy} disabled={!washedText.trim()}>
              {copied ? <CheckCircle2 className="size-4 text-emerald-600" /> : <Copy className="size-4" />}
              복사
            </Button>
          </div>
        </div>
      </header>

      <main className="grid min-h-0 flex-1 grid-rows-[auto_1fr] bg-muted/25">
        <section className="border-b border-border bg-background/80 px-6 py-3">
          <div className="flex flex-col gap-3 xl:flex-row xl:items-center xl:justify-between">
            <p className={`text-sm ${errorMessage || isOverLimit ? "text-destructive" : "text-muted-foreground"}`}>
              {statusText}
            </p>

            <div className="grid gap-2 sm:grid-cols-4 xl:min-w-[720px]">
              {steps.map((step, index) => {
                const isDone = activeStepIndex > index || (result && index === steps.length - 1)
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
                      <span className="block truncate text-[11px] text-muted-foreground">{step.description}</span>
                    </span>
                  </div>
                )
              })}
            </div>
          </div>
        </section>

        <section className="grid min-h-0 gap-4 p-4 xl:grid-cols-[minmax(0,1fr)_minmax(0,1fr)]">
          <div className="flex min-h-[340px] min-w-0 flex-col overflow-hidden rounded-lg border border-border bg-background">
            <div className="flex shrink-0 items-center justify-between border-b border-border px-4 py-3">
              <div className="flex min-w-0 items-center gap-2">
                <FileText className="size-4 shrink-0 text-muted-foreground" />
                <div className="min-w-0">
                  <p className="truncate text-sm font-semibold">붙여넣은 초안</p>
                  <p className="text-xs text-muted-foreground">웹 LLM에서 받은 초안을 그대로 넣으세요.</p>
                </div>
              </div>
              <span className={`text-xs tabular-nums ${isOverLimit ? "font-semibold text-destructive" : "text-muted-foreground"}`}>
                {draftChars.toLocaleString()} / {MAX_DRAFT_LENGTH.toLocaleString()}
              </span>
            </div>
            <Textarea
              value={draftText}
              onChange={(event) => setDraftText(event.target.value)}
              placeholder="여기에 초안을 붙여넣으세요."
              className="min-h-0 flex-1 resize-none rounded-none border-0 bg-transparent px-5 py-4 text-[15px] leading-8 shadow-none focus-visible:ring-0"
            />
          </div>

          <div className="flex min-h-[340px] min-w-0 flex-col overflow-hidden rounded-lg border border-border bg-background">
            <div className="flex shrink-0 items-center justify-between border-b border-border px-4 py-3">
              <div className="flex min-w-0 items-center gap-2">
                <PencilLine className="size-4 shrink-0 text-primary" />
                <div className="min-w-0">
                  <p className="truncate text-sm font-semibold">WASH본 에디터</p>
                  <p className="text-xs text-muted-foreground">결과를 이 창에서 바로 다듬은 뒤 복사할 수 있습니다.</p>
                </div>
              </div>
              <span className="text-xs tabular-nums text-muted-foreground">
                {washedChars.toLocaleString()}자
              </span>
            </div>
            <Textarea
              value={washedText}
              onChange={(event) => setWashedText(event.target.value)}
              placeholder={isWashing ? "WASH 중입니다..." : "WASH 결과가 여기에 표시됩니다."}
              className="min-h-0 flex-1 resize-none rounded-none border-0 bg-primary/[0.025] px-5 py-4 text-[15px] leading-8 shadow-none focus-visible:ring-0"
            />
          </div>
        </section>
      </main>

      {englishText && (
        <footer className="shrink-0 border-t border-border bg-background px-6 py-3">
          <details className="group">
            <summary className="flex cursor-pointer list-none items-center gap-2 text-xs font-medium text-muted-foreground transition-colors hover:text-foreground">
              <Languages className="size-3.5" />
              중간 영어 번역 보기
              <Clipboard className="ml-auto size-3.5 opacity-0 transition-opacity group-open:opacity-100" />
            </summary>
            <p className="mt-2 max-h-24 overflow-y-auto rounded-md bg-muted px-3 py-2 text-xs leading-5 text-muted-foreground">
              {englishText}
            </p>
          </details>
        </footer>
      )}
    </div>
  )
}
