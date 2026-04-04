"use client"

import { Card, CardContent, CardHeader, CardTitle, CardDescription } from "@/components/ui/card"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Folder, Sparkles, Calendar, User, Trash2 } from "lucide-react"
import { type Experience } from "@/lib/mock-data"
import { toast } from "sonner"
import { useState } from "react"

function buildPreviewSummary(experience: Experience) {
  const normalizedDescription = experience.description.trim()
  if (normalizedDescription && normalizedDescription !== experience.title.trim()) {
    return normalizedDescription
  }

  const facet = experience.facets?.find(
    (candidate) =>
      candidate.results.length > 0 ||
      candidate.actions.length > 0 ||
      candidate.situation.length > 0 ||
      candidate.judgment.length > 0
  )

  const fallback =
    facet?.results[0] ||
    facet?.actions[0] ||
    facet?.situation[0] ||
    facet?.judgment[0] ||
    experience.metrics[0] ||
    experience.role ||
    experience.origin ||
    experience.title

  return fallback.trim()
}

export function ExperienceCard({ 
  experience, 
  onClick,
  onDelete
}: { 
  experience: Experience; 
  onClick: () => void;
  onDelete?: () => void;
}) {
  const [isDeleting, setIsDeleting] = useState(false)
  const visibleTechStack = experience.techStack.slice(0, 3)
  const hiddenTechCount = Math.max(experience.techStack.length - visibleTechStack.length, 0)
  const previewSummary = buildPreviewSummary(experience)

  const handleDelete = async (e: React.MouseEvent) => {
    e.stopPropagation()
    if (!confirm("이 경험 항목을 삭제하시겠습니까?")) return

    try {
      setIsDeleting(true)
      const response = await fetch(`/api/experiences/${experience.id}`, {
        method: "DELETE",
      })

      if (!response.ok) throw new Error("삭제 실패")

      toast.success("삭제 완료", {
        description: "경험 항목이 보관소에서 삭제되었습니다."
      })
      onDelete?.()
    } catch (error) {
      toast.error("삭제 실패", {
        description: "항목을 삭제하는 중 오류가 발생했습니다."
      })
    } finally {
      setIsDeleting(false)
    }
  }

  return (
    <Card 
      className={`group relative cursor-pointer overflow-hidden transition-all hover:border-primary/30 hover:shadow-md ${isDeleting ? "pointer-events-none opacity-50" : ""}`}
      onClick={onClick}
    >
      <div className="absolute top-3 right-3 opacity-0 group-hover:opacity-100 transition-opacity z-10">
        <Button
          variant="ghost"
          size="icon"
          className="size-8 text-muted-foreground hover:text-destructive hover:bg-destructive/10"
          onClick={handleDelete}
        >
          <Trash2 className="size-4" />
        </Button>
      </div>
      <CardHeader className="pb-3">
        <div className="flex items-start justify-between gap-3">
          <div className="flex min-w-0 items-center gap-3">
            <div className="flex size-10 items-center justify-center rounded-lg bg-primary/10">
              <Sparkles className="size-5 text-primary" />
            </div>
            <div className="min-w-0">
              <CardTitle className="line-clamp-2 text-base leading-tight">{experience.title}</CardTitle>
              <CardDescription className="mt-1 flex items-center gap-1">
                <Folder className="size-3" />
                <span className="truncate">{experience.category}</span>
              </CardDescription>
            </div>
          </div>
        </div>
      </CardHeader>
      <CardContent className="min-w-0 space-y-4">
        <p className="text-sm text-muted-foreground leading-relaxed line-clamp-3">
          {previewSummary}
        </p>
        
        <div className="flex flex-wrap gap-1.5">
          {visibleTechStack.map((tech) => (
            <Badge
              key={tech}
              variant="secondary"
              className="h-auto max-w-full justify-start whitespace-normal break-words px-2.5 py-1 text-left text-xs font-normal leading-snug"
            >
              {tech}
            </Badge>
          ))}
          {hiddenTechCount > 0 && (
            <span className="inline-flex items-center rounded-md bg-muted px-2 py-1 text-xs text-muted-foreground">
              +{hiddenTechCount}개 더보기
            </span>
          )}
        </div>

        <div className="space-y-2 pt-2 border-t border-border">
          <div className="flex flex-wrap gap-2">
            {experience.metrics.slice(0, 2).map((metric, idx) => (
              <span
                key={idx}
                className="max-w-full rounded-md bg-primary/10 px-2 py-1 text-xs font-medium text-primary break-words whitespace-normal"
              >
                {metric}
              </span>
            ))}
            {experience.metrics.length > 2 && (
              <span className="text-xs text-muted-foreground bg-muted px-2 py-1 rounded-md">
                +{experience.metrics.length - 2}개 더보기
              </span>
            )}
          </div>
        </div>

        <div className="flex items-center justify-between text-xs text-muted-foreground pt-2">
          <div className="flex items-center gap-1">
            <Calendar className="size-3" />
            <span>{experience.period}</span>
          </div>
          <div className="flex items-center gap-1">
            <User className="size-3" />
            <span>{experience.role}</span>
          </div>
        </div>
      </CardContent>
    </Card>
  )
}
