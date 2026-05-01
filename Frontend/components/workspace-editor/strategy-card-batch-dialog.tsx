"use client"

import { AlertTriangle, CheckCircle2 } from "lucide-react"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog"
import { ScrollArea } from "@/components/ui/scroll-area"
import { QUESTION_CATEGORY_LABELS } from "@/lib/workspace/types"
import type { QuestionCategory, QuestionStrategyCardCandidate } from "@/lib/workspace/types"

interface StrategyCardBatchDialogProps {
  open: boolean
  candidate: QuestionStrategyCardCandidate | null
  onOpenChange: (open: boolean) => void
  onConfirm: () => void
  isSubmitting?: boolean
}

export function StrategyCardBatchDialog({
  open,
  candidate,
  onOpenChange,
  onConfirm,
  isSubmitting = false,
}: StrategyCardBatchDialogProps) {
  const cards = candidate?.cards ?? []

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="w-[90vw] max-w-5xl rounded-[28px] border-border/70 p-0 sm:max-w-5xl">
        <DialogHeader className="border-b border-border/60 px-6 py-5 sm:px-7">
          <DialogTitle className="text-2xl font-black tracking-tight">전략카드 미리보기</DialogTitle>
          <DialogDescription className="text-sm leading-6">
            각 문항의 주장, 사용할 경험, 회사 Fit 연결, 중복 방지 기준을 먼저 적용한 뒤 전체 초안을 생성합니다.
          </DialogDescription>
          <div className="flex flex-wrap gap-2">
            <Badge variant="secondary" className="rounded-full">
              {cards.length}개 문항
            </Badge>
            {candidate?.modelName ? (
              <Badge variant="outline" className="rounded-full">
                {candidate.modelName}
              </Badge>
            ) : null}
          </div>
        </DialogHeader>

        <ScrollArea className="max-h-[65vh]">
          <div className="flex flex-col gap-4 px-6 py-5 sm:px-7">
            {cards.map((record, index) => {
              const card = record.card
              const category = card.category as QuestionCategory
              return (
                <div
                  key={`${record.questionId}-${index}`}
                  className="rounded-3xl border border-border/60 bg-muted/20 p-5"
                >
                  <div className="flex flex-col gap-3">
                    <div className="flex flex-wrap items-center gap-2">
                      <Badge variant="outline" className="rounded-full">
                        Question {index + 1}
                      </Badge>
                      <Badge variant="secondary" className="rounded-full">
                        {QUESTION_CATEGORY_LABELS[category] ?? card.category}
                      </Badge>
                    </div>
                    <div>
                      <h3 className="text-base font-black leading-snug text-foreground">{card.questionTitle}</h3>
                      <p className="mt-1 text-sm font-semibold leading-6 text-primary">{card.summary}</p>
                    </div>
                  </div>

                  <div className="mt-4 grid gap-3 md:grid-cols-2">
                    <PreviewBlock title="핵심 주장" items={[card.primaryClaim]} />
                    <PreviewBlock
                      title="사용할 경험"
                      items={[...(card.experiencePlan?.primaryExperiences ?? []), ...(card.experiencePlan?.facets ?? [])]}
                    />
                    <PreviewBlock
                      title="회사 Fit 연결"
                      items={[card.fitConnection?.domainBridge, ...(card.fitConnection?.lexicon ?? [])]}
                    />
                    <PreviewBlock
                      title="중복/과장 방지"
                      items={[...(card.warnings ?? []), ...(card.confidenceNotes ?? [])]}
                      warning
                    />
                  </div>
                </div>
              )
            })}

            {!cards.length ? (
              <div className="flex items-center gap-2 rounded-2xl border border-destructive/20 bg-destructive/5 px-4 py-3 text-sm text-destructive">
                <AlertTriangle className="size-4" />
                생성된 전략카드가 없습니다.
              </div>
            ) : null}
          </div>
        </ScrollArea>

        <DialogFooter className="border-t border-border/60 px-6 py-4 sm:px-7">
          <Button variant="ghost" onClick={() => onOpenChange(false)} disabled={isSubmitting}>
            취소
          </Button>
          <Button onClick={onConfirm} disabled={isSubmitting || !cards.length} className="gap-1.5">
            <CheckCircle2 className="size-3.5" />
            {isSubmitting ? "적용 중..." : "이 전략으로 전체 생성"}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}

function PreviewBlock({
  title,
  items,
  warning = false,
}: {
  title: string
  items: Array<string | null | undefined>
  warning?: boolean
}) {
  const normalized = items.filter((item): item is string => Boolean(item?.trim())).slice(0, 4)
  if (!normalized.length) {
    return null
  }

  return (
    <div className="min-w-0 rounded-2xl border border-border/60 bg-background px-4 py-3">
      <p className={`text-[11px] font-black uppercase tracking-[0.18em] ${warning ? "text-amber-700" : "text-muted-foreground"}`}>
        {title}
      </p>
      <ul className="mt-2 flex flex-col gap-1.5 text-sm leading-6 text-foreground/85">
        {normalized.map((item) => (
          <li key={item} className="break-words">
            {item}
          </li>
        ))}
      </ul>
    </div>
  )
}
