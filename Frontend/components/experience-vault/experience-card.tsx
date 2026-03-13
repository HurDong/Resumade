"use client"

import { Card, CardContent, CardHeader, CardTitle, CardDescription } from "@/components/ui/card"
import { Badge } from "@/components/ui/badge"
import { Folder, Sparkles, Calendar, User } from "lucide-react"
import { type Experience } from "@/lib/mock-data"

export function ExperienceCard({ experience, onClick }: { experience: Experience; onClick: () => void }) {
  return (
    <Card 
      className="group transition-all hover:shadow-md hover:border-primary/30 cursor-pointer"
      onClick={onClick}
    >
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
