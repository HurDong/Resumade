"use client"

import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Building2, Calendar, Pencil, Trash2 } from "lucide-react"
import { cn } from "@/lib/utils"
import { type CodingProblem } from "./mock-data"

export function ProblemCard({
  problem,
  onClick,
  onEdit,
  onDelete,
}: {
  problem: CodingProblem
  onClick: () => void
  onEdit: (e: React.MouseEvent) => void
  onDelete: (e: React.MouseEvent) => void
}) {
  return (
    <div
      onClick={onClick}
      className="group relative cursor-pointer rounded-xl border border-border bg-background p-5 transition-colors hover:border-primary/30 hover:shadow-md"
    >
      {/* 호버 액션 */}
      <div className="absolute right-3 top-3 flex items-center gap-1 opacity-0 transition-opacity group-hover:opacity-100">
        <Button
          variant="ghost"
          size="icon"
          className="size-7 text-muted-foreground hover:text-foreground"
          onClick={onEdit}
        >
          <Pencil className="size-3.5" />
        </Button>
        <Button
          variant="ghost"
          size="icon"
          className="size-7 text-muted-foreground hover:text-destructive hover:bg-destructive/10"
          onClick={onDelete}
        >
          <Trash2 className="size-3.5" />
        </Button>
      </div>

      {/* 제목 */}
      <h3 className="pr-16 text-base font-semibold leading-snug">{problem.title}</h3>

      {/* 메타 */}
      <div className="mt-1.5 flex flex-wrap items-center gap-x-3 gap-y-1 text-xs text-muted-foreground">
        <span className="flex items-center gap-1">
          <Building2 className="size-3" />
          {problem.company}
        </span>
        <span className="flex items-center gap-1">
          <Calendar className="size-3" />
          {problem.date}
        </span>
        <span>
          {problem.platform}
          {problem.level != null && ` Lv.${problem.level}`}
        </span>
      </div>

      {/* 내 풀이 미리보기 */}
      {problem.myApproach && (
        <p className="mt-3 line-clamp-2 text-sm leading-relaxed text-foreground/70">
          {problem.myApproach}
        </p>
      )}

      {/* 유형 태그 */}
      {problem.types.length > 0 && (
        <div className="mt-3 flex flex-wrap gap-1.5">
          {problem.types.map((t) => (
            <Badge key={t} variant="secondary" className="text-xs font-normal">
              {t}
            </Badge>
          ))}
        </div>
      )}
    </div>
  )
}
