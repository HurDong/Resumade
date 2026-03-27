"use client"

import type { BatchPlanResponse } from "@/lib/workspace/types"
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog"
import { Button } from "@/components/ui/button"
import { Badge } from "@/components/ui/badge"
import { ScrollArea } from "@/components/ui/scroll-area"

interface BatchPlanDialogProps {
  open: boolean
  plan: BatchPlanResponse | null
  onOpenChange: (open: boolean) => void
  onConfirm: () => void
  isSubmitting?: boolean
}

export function BatchPlanDialog({
  open,
  plan,
  onOpenChange,
  onConfirm,
  isSubmitting = false,
}: BatchPlanDialogProps) {
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="w-[90vw] max-w-5xl sm:max-w-5xl rounded-[28px] border-border/70 p-0">
        <DialogHeader className="border-b border-border/60 px-6 py-5 sm:px-7">
          <DialogTitle className="text-2xl font-black tracking-tight">자소서 전략 미리보기</DialogTitle>
          <DialogDescription className="text-sm leading-6">
            프로젝트 이름 중복보다, 문항끼리 같은 세부 기술과 배운점이 반복되지 않도록 설계한 배치 전략입니다.
          </DialogDescription>
          {plan?.coverageSummary ? (
            <div className="rounded-2xl border border-emerald-200 bg-emerald-50/80 px-4 py-3 text-sm font-medium text-emerald-900">
              {plan.coverageSummary}
            </div>
          ) : null}
          {plan?.globalGuardrails?.length ? (
            <div className="flex flex-wrap gap-2">
              {plan.globalGuardrails.map((rule) => (
                <Badge key={rule} variant="secondary" className="rounded-full bg-muted/70 px-3 py-1 text-[11px]">
                  {rule}
                </Badge>
              ))}
            </div>
          ) : null}
        </DialogHeader>

        <ScrollArea className="max-h-[65vh]">
          <div className="space-y-4 px-6 py-5 sm:px-7">
            {plan?.assignments?.map((assignment, index) => (
              <div
                key={`${assignment.questionId}-${index}`}
                className="rounded-3xl border border-border/60 bg-muted/20 p-5"
              >
                <div className="space-y-2">
                  <div className="text-xs font-black uppercase tracking-[0.2em] text-primary/70">
                    Question {index + 1}
                  </div>
                  <div className="text-base font-black leading-snug text-foreground">
                    {assignment.questionTitle}
                  </div>
                  <Badge className="h-auto max-w-full whitespace-normal rounded-full px-3 py-1.5 text-[11px] font-bold leading-relaxed">
                    {assignment.angle}
                  </Badge>
                </div>

                <div className="mt-4 grid gap-4 md:grid-cols-2">
                  <div className="min-w-0 space-y-2">
                    <div className="text-[11px] font-black uppercase tracking-[0.18em] text-muted-foreground">
                      사용할 경험
                    </div>
                    <div className="flex flex-wrap gap-2">
                      {assignment.primaryExperiences.map((experience) => (
                        <Badge key={experience} variant="outline" className="h-auto max-w-full whitespace-normal rounded-full px-3 py-1 text-xs leading-snug">
                          {experience}
                        </Badge>
                      ))}
                    </div>
                  </div>

                  <div className="min-w-0 space-y-2">
                    <div className="text-[11px] font-black uppercase tracking-[0.18em] text-muted-foreground">
                      강조할 배운점
                    </div>
                    <div className="flex flex-wrap gap-2">
                      {assignment.learningPoints.map((point) => (
                        <Badge key={point} variant="secondary" className="h-auto max-w-full whitespace-normal rounded-full px-3 py-1 text-xs leading-snug">
                          {point}
                        </Badge>
                      ))}
                    </div>
                  </div>
                </div>

                <div className="mt-4 space-y-2">
                  <div className="text-[11px] font-black uppercase tracking-[0.18em] text-muted-foreground">
                    이번 문항에서 잡을 세부 디테일
                  </div>
                  <ul className="space-y-2 text-sm leading-6 text-foreground/90">
                    {assignment.focusDetails.map((detail) => (
                      <li key={detail} className="rounded-2xl bg-background/80 px-4 py-2">
                        {detail}
                      </li>
                    ))}
                  </ul>
                </div>

                {assignment.avoidDetails.length ? (
                  <div className="mt-4 space-y-2">
                    <div className="text-[11px] font-black uppercase tracking-[0.18em] text-amber-700">
                      다른 문항과 겹치면 안 되는 디테일
                    </div>
                    <ul className="space-y-2 text-sm leading-6 text-amber-950/85">
                      {assignment.avoidDetails.map((detail) => (
                        <li key={detail} className="rounded-2xl border border-amber-200 bg-amber-50 px-4 py-2">
                          {detail}
                        </li>
                      ))}
                    </ul>
                  </div>
                ) : null}
              </div>
            ))}
          </div>
        </ScrollArea>

        <DialogFooter className="border-t border-border/60 px-6 py-4 sm:px-7">
          <Button variant="ghost" onClick={() => onOpenChange(false)} disabled={isSubmitting}>
            취소
          </Button>
          <Button onClick={onConfirm} disabled={isSubmitting || !plan}>
            {isSubmitting ? "적용 중..." : "이 전략으로 전체 생성"}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
