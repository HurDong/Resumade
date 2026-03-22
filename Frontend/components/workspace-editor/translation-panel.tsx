"use client"

import type { MouseEvent, ReactNode } from "react"
import {
  AlertTriangle,
  CheckCircle,
  Copy,
  FileText,
  RefreshCw,
  ScanSearch,
  Wand2,
} from "lucide-react"
import { toast } from "sonner"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { ScrollArea } from "@/components/ui/scroll-area"
import {
  ensureTaggedTitle,
  getTranslationProcessingMeta,
  injectHighlightTags,
} from "@/lib/workspace/translation-panel-helpers"
import { useWorkspaceStore } from "@/lib/store/workspace-store"

interface ProcessingOverlayProps {
  label: string
  description: string
}

interface DraftCardProps {
  title: string
  description?: string
  icon: ReactNode
  content: string
  renderHtml?: boolean
  className?: string
  titleClassName?: string
  contentClassName: string
  overlay?: ProcessingOverlayProps
}

function EmptyTranslationState() {
  return (
    <div className="flex h-full flex-col items-center justify-center bg-muted/5 p-10 text-center text-muted-foreground">
      <div className="relative mb-6">
        <Wand2 className="size-16 opacity-20" />
        <div className="absolute inset-0 -z-10 size-16 rounded-full bg-primary/10 blur-2xl" />
      </div>
      <h3 className="flex items-center gap-2 text-lg font-bold">
        <Wand2 className="size-5 text-primary" />
        휴먼 패치 스튜디오
      </h3>
      <p className="max-w-md text-balance text-sm font-medium leading-relaxed text-muted-foreground/80">
        초안이 생성되면 번역, 역번역, 휴먼 패치 결과가 순서대로 이곳에 표시됩니다.
      </p>
    </div>
  )
}

function ProcessingOverlay({ label, description }: ProcessingOverlayProps) {
  return (
    <div className="absolute inset-0 flex items-center justify-center rounded-b-xl bg-background/72 backdrop-blur-[2px]">
      <div className="flex flex-col items-center gap-4 text-center">
        <div className="relative">
          <div className="absolute inset-0 rounded-full bg-primary/20 blur-2xl" />
          <div className="relative flex size-20 items-center justify-center rounded-full border border-primary/20 bg-primary/8 shadow-lg">
            <div className="absolute inset-0 rounded-full border-4 border-primary/15 border-t-primary animate-spin" />
            <Wand2 className="size-9 text-primary" />
          </div>
        </div>
        <div className="space-y-1">
          <p className="text-base font-black tracking-tight text-primary">{label}</p>
          <p className="text-sm font-medium text-foreground/70">{description}</p>
        </div>
      </div>
    </div>
  )
}

function DraftCard({
  title,
  description,
  icon,
  content,
  renderHtml = false,
  className,
  titleClassName,
  contentClassName,
  overlay,
}: DraftCardProps) {
  return (
    <Card className={className}>
      <CardHeader className="pb-4">
        <CardTitle
          className={`flex items-center gap-2 text-xs font-black uppercase tracking-widest ${
            titleClassName ?? "text-muted-foreground/60"
          }`}
        >
          {icon}
          {title}
        </CardTitle>
        {description ? (
          <CardDescription className="text-[10px] font-semibold italic text-muted-foreground/80">
            {description}
          </CardDescription>
        ) : null}
      </CardHeader>
      <CardContent className="relative min-h-[420px]">
        {renderHtml ? (
          <div
            className={contentClassName}
            dangerouslySetInnerHTML={{ __html: content }}
          />
        ) : (
          <div className={contentClassName}>{content}</div>
        )}
        {overlay ? <ProcessingOverlay {...overlay} /> : null}
      </CardContent>
    </Card>
  )
}

export function TranslationPanel() {
  const {
    questions,
    activeQuestionId,
    isProcessing,
    pipelineStage,
    progressMessage,
    processingError,
    hoveredMistranslationId,
    setHoveredMistranslationId,
    rerunWash,
    rerunPatch,
  } = useWorkspaceStore()

  const activeQuestion = questions.find((question) => question.id === activeQuestionId) ?? questions[0]
  const {
    content: draft,
    washedKr,
    mistranslations,
    aiReviewReport,
  } = activeQuestion
  const hasHighlights = mistranslations.length > 0
  const { target: processingTarget, label: processingLabel } = getTranslationProcessingMeta(
    isProcessing,
    pipelineStage
  )

  const originalContent = aiReviewReport?.taggedOriginalText
    ? injectHighlightTags(
        ensureTaggedTitle(draft || "", aiReviewReport.taggedOriginalText),
        mistranslations,
        hoveredMistranslationId,
        true
      )
    : draft || ""

  const washedContent = aiReviewReport?.taggedWashedText
    ? injectHighlightTags(
        ensureTaggedTitle(washedKr || "", aiReviewReport.taggedWashedText),
        mistranslations,
        hoveredMistranslationId,
        false
      )
    : washedKr || ""

  const handleHighlightMouseOver = (event: MouseEvent<HTMLDivElement>) => {
    const target = event.target instanceof Element ? event.target : null
    const misId = target?.closest("[data-mis-id]")?.getAttribute("data-mis-id")
    setHoveredMistranslationId(misId ?? null)
  }

  const copyWashedDraft = async () => {
    await navigator.clipboard.writeText(washedKr)
    toast.success("클립보드에 복사했습니다.", {
      description: "결과물을 바로 붙여넣기할 수 있습니다.",
      position: "top-center",
    })
  }

  if (!draft && !washedKr && !isProcessing) {
    return <EmptyTranslationState />
  }

  return (
    <div className="flex h-full flex-col overflow-hidden bg-muted/10">
      <div className="sticky top-0 z-10 shrink-0 border-b border-border bg-background/50 p-6 backdrop-blur-md">
        <div className="flex items-center justify-between gap-4">
          <div>
            <h3 className="flex items-center gap-2 text-lg font-bold">
              <CheckCircle className="size-5 text-emerald-500" />
              휴먼 패치 결과
            </h3>
            <p className="mt-1 text-xs text-muted-foreground">
              번역 루프로 세탁된 원고를 의미 충실도 기준으로 검토한 결과입니다.
            </p>
          </div>
          <div className="flex flex-wrap items-center gap-2">
            <Button
              size="sm"
              variant="outline"
              className="h-8 gap-2 rounded-lg border-primary/20 px-3 text-[11px] font-bold shadow-sm transition-all hover:bg-primary/5"
              onClick={() => void rerunWash()}
              disabled={!draft || isProcessing}
            >
              <RefreshCw className="size-3.5" />
              재세탁
            </Button>
            <Button
              size="sm"
              variant="outline"
              className="h-8 gap-2 rounded-lg border-primary/20 px-3 text-[11px] font-bold shadow-sm transition-all hover:bg-primary/5"
              onClick={() => void rerunPatch()}
              disabled={!draft || !washedKr || isProcessing}
            >
              <ScanSearch className="size-3.5" />
              재패치
            </Button>
            <Button
              size="sm"
              variant="outline"
              className="h-8 gap-2 rounded-lg border-primary/20 px-3 text-[11px] font-bold shadow-sm transition-all hover:bg-primary/5"
              onClick={() => void copyWashedDraft()}
              disabled={!washedKr}
            >
              <Copy className="size-3.5" />
              복사
            </Button>
            {isProcessing ? (
              <Badge variant="secondary" className="animate-in fade-in zoom-in-95 gap-2 px-3 py-1.5">
                <div className="size-2 rounded-full bg-primary animate-pulse" />
                <span className="text-[11px] font-bold">{progressMessage}</span>
              </Badge>
            ) : null}
            {!isProcessing && processingError ? (
              <Badge variant="destructive" className="max-w-full gap-2 px-3 py-1.5">
                <AlertTriangle className="size-3.5 shrink-0" />
                <span className="break-all text-[11px] font-bold">{processingError}</span>
              </Badge>
            ) : null}
          </div>
        </div>
      </div>

      <ScrollArea className="h-full min-h-0 flex-1">
        <div
          className="animate-in fade-in slide-in-from-bottom-2 space-y-8 p-6 pb-20 duration-500"
          onMouseOver={handleHighlightMouseOver}
          onMouseLeave={() => setHoveredMistranslationId(null)}
        >
          <div className="grid grid-cols-1 gap-6 xl:grid-cols-2">
            <DraftCard
              title="원본 초안"
              icon={<FileText className="size-3.5" />}
              content={originalContent}
              renderHtml={Boolean(aiReviewReport?.taggedOriginalText)}
              className="border-none bg-background/80 shadow-sm"
              contentClassName={`min-h-[200px] whitespace-pre-wrap text-sm font-medium italic leading-relaxed text-muted-foreground transition-all duration-300 ${
                hoveredMistranslationId ? "opacity-100" : "opacity-70"
              }`}
              overlay={
                processingTarget === "draft"
                  ? {
                      label: processingLabel,
                      description: "잠시 후 새로운 결과가 표시됩니다.",
                    }
                  : undefined
              }
            />

            <DraftCard
              title="세탁본"
              description={
                hasHighlights
                  ? "강조 표시된 부분은 의미 손실이 의심되어 검토가 필요한 영역입니다."
                  : "선택된 하이라이트가 없으면 전체 세탁본이 표시됩니다."
              }
              icon={<CheckCircle className="size-3.5 text-primary" />}
              content={washedContent}
              renderHtml={Boolean(aiReviewReport?.taggedWashedText)}
              className="border-primary/20 bg-background shadow-lg ring-1 ring-primary/5"
              titleClassName="text-primary"
              contentClassName={
                hasHighlights
                  ? "min-h-[200px] whitespace-pre-wrap text-sm font-medium leading-relaxed text-foreground"
                  : "min-h-[200px] whitespace-pre-wrap text-[15px] font-medium leading-8 text-foreground"
              }
              overlay={
                processingTarget === "washed"
                  ? {
                      label: processingLabel,
                      description: "현재 결과를 기준으로 세탁본을 다듬고 있습니다.",
                    }
                  : undefined
              }
            />
          </div>
        </div>
      </ScrollArea>
    </div>
  )
}
