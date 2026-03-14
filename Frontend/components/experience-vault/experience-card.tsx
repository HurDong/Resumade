"use client"

import { Card, CardContent, CardHeader, CardTitle, CardDescription } from "@/components/ui/card"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Folder, Sparkles, Calendar, User, Trash2 } from "lucide-react"
import { type Experience } from "@/lib/mock-data"
import { toast } from "sonner"
import { useState } from "react"

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
      className={`group relative transition-all hover:shadow-md hover:border-primary/30 cursor-pointer ${isDeleting ? "opacity-50 pointer-events-none" : ""}`}
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
          <div className="flex items-center gap-3">
            <div className="flex size-10 items-center justify-center rounded-lg bg-primary/10">
              <Sparkles className="size-5 text-primary" />
            </div>
            <div>
              <CardTitle className="text-base leading-tight">{experience.title}</CardTitle>
              <CardDescription className="flex items-center gap-1 mt-1">
                <Folder className="size-3" />
                {experience.category}
              </CardDescription>
            </div>
          </div>
        </div>
      </CardHeader>
      <CardContent className="space-y-4">
        <p className="text-sm text-muted-foreground leading-relaxed line-clamp-3">
          {experience.description}
        </p>
        
        <div className="flex flex-wrap gap-1.5">
          {experience.techStack.map((tech) => (
            <Badge key={tech} variant="secondary" className="text-xs font-normal">
              {tech}
            </Badge>
          ))}
        </div>

        <div className="space-y-2 pt-2 border-t border-border">
          <div className="flex flex-wrap gap-2">
            {experience.metrics.slice(0, 2).map((metric, idx) => (
              <span key={idx} className="text-xs text-primary font-medium bg-primary/10 px-2 py-1 rounded-md">
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
