"use client"

import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Building2, Calendar, BookOpen, Lightbulb, StickyNote, Pencil } from "lucide-react"
import { type CodingProblem } from "./mock-data"

export function ProblemDetailModal({
  problem,
  open,
  onClose,
  onEdit,
}: {
  problem: CodingProblem | null
  open: boolean
  onClose: () => void
  onEdit: () => void
}) {
  if (!problem) return null

  return (
    <Dialog open={open} onOpenChange={onClose}>
      <DialogContent className="max-w-2xl max-h-[85vh] overflow-y-auto">
        <DialogHeader>
          <div className="flex items-start justify-between gap-3">
            <DialogTitle className="text-lg font-bold leading-tight">{problem.title}</DialogTitle>
            <Button
              variant="outline"
              size="sm"
              className="shrink-0 gap-1.5 text-xs"
              onClick={() => { onClose(); setTimeout(onEdit, 150) }}
            >
              <Pencil className="size-3.5" />
              수정하기
            </Button>
          </div>

          <div className="flex flex-wrap items-center gap-3 mt-1">
            <span className="flex items-center gap-1 text-sm text-muted-foreground">
              <Building2 className="size-3.5" />
              {problem.company}
            </span>
            <span className="flex items-center gap-1 text-sm text-muted-foreground">
              <Calendar className="size-3.5" />
              {problem.date}
            </span>
            <span className="text-sm text-muted-foreground">
              {problem.platform}
              {problem.level != null && ` Lv.${problem.level}`}
            </span>
          </div>

          {problem.types.length > 0 && (
            <div className="flex flex-wrap gap-1.5 mt-2">
              {problem.types.map((t) => (
                <Badge key={t} variant="secondary" className="text-xs font-normal">
                  {t}
                </Badge>
              ))}
            </div>
          )}
        </DialogHeader>

        <div className="space-y-4 mt-2">
          {/* 내 풀이 */}
          {problem.myApproach && (
            <section className="rounded-lg border border-amber-200 bg-amber-50/50 p-4 dark:border-amber-900/40 dark:bg-amber-950/20">
              <h3 className="flex items-center gap-2 text-sm font-semibold text-amber-700 dark:text-amber-400 mb-2">
                <BookOpen className="size-4" />
                내 풀이
              </h3>
              <p className="text-sm leading-relaxed whitespace-pre-wrap text-foreground/80">
                {problem.myApproach}
              </p>
            </section>
          )}

          {/* 더 나은 풀이 */}
          {(problem.betterApproach || problem.betterCode) && (
            <section className="rounded-lg border border-blue-200 bg-blue-50/50 p-4 dark:border-blue-900/40 dark:bg-blue-950/20">
              <h3 className="flex items-center gap-2 text-sm font-semibold text-blue-700 dark:text-blue-400 mb-2">
                <Lightbulb className="size-4" />
                더 나은 풀이
              </h3>
              {problem.betterApproach && (
                <p className="text-sm leading-relaxed whitespace-pre-wrap text-foreground/80 mb-3">
                  {problem.betterApproach}
                </p>
              )}
              {problem.betterCode && (
                <pre className="overflow-x-auto rounded-md bg-zinc-900 p-4 text-xs text-zinc-100 leading-relaxed font-mono">
                  <code>{problem.betterCode}</code>
                </pre>
              )}
            </section>
          )}

          {/* 회고 */}
          {problem.note && (
            <section className="rounded-lg border border-violet-200 bg-violet-50/50 p-4 dark:border-violet-900/40 dark:bg-violet-950/20">
              <h3 className="flex items-center gap-2 text-sm font-semibold text-violet-700 dark:text-violet-400 mb-2">
                <StickyNote className="size-4" />
                회고 메모
              </h3>
              <p className="text-sm leading-relaxed whitespace-pre-wrap text-foreground/80">
                {problem.note}
              </p>
            </section>
          )}
        </div>
      </DialogContent>
    </Dialog>
  )
}
