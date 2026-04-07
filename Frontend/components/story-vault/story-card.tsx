"use client"

import { Clock, Edit2, Trash2, Tag, Quote } from "lucide-react"
import { Button } from "@/components/ui/button"
import { Card, CardHeader, CardContent, CardFooter } from "@/components/ui/card"
import { Badge } from "@/components/ui/badge"
import { type PersonalStory, STORY_TYPE_LABELS } from "@/lib/workspace/types"

interface StoryCardProps {
  story: PersonalStory
  onEdit: () => void
  onDelete: () => void
}

export function StoryCard({ story, onEdit, onDelete }: StoryCardProps) {
  return (
    <Card className="group flex h-full flex-col overflow-hidden border-border/50 bg-card/50 transition-all hover:border-primary/30 hover:shadow-md">
      <CardHeader className="p-5 pb-0">
        <div className="flex items-start justify-between">
          <Badge variant="outline" className="rounded-md border-primary/20 bg-primary/5 px-2 py-0.5 text-[10px] font-semibold text-primary">
            {STORY_TYPE_LABELS[story.type]}
          </Badge>
          <div className="flex gap-1 opacity-0 transition-opacity group-hover:opacity-100">
            <Button variant="ghost" size="icon" className="size-8" onClick={onEdit}>
              <Edit2 className="size-3.5 text-muted-foreground" />
            </Button>
            <Button variant="ghost" size="icon" className="size-8 text-destructive/70 hover:bg-destructive/10 hover:text-destructive" onClick={onDelete}>
              <Trash2 className="size-3.5" />
            </Button>
          </div>
        </div>
        <div className="mt-3 flex items-center gap-1.5 text-sm font-medium text-foreground">
          <Clock className="size-3.5 text-muted-foreground/60" />
          {story.period || "전체 시기"}
        </div>
      </CardHeader>
      
      <CardContent className="flex-1 p-5 pt-4">
        <div className="relative">
          <Quote className="absolute -left-1 -top-1 size-8 rotate-180 text-muted-foreground/10" />
          <p className="relative line-clamp-4 text-sm leading-relaxed text-muted-foreground">
            {story.content}
          </p>
        </div>
      </CardContent>

      <CardFooter className="flex flex-wrap gap-1.5 border-t border-border/30 bg-muted/20 p-4">
        {story.keywords && story.keywords.length > 0 ? (
          story.keywords.map((kw, idx) => (
            <Badge key={idx} variant="secondary" className="rounded-sm bg-muted/40 px-1.5 py-0 text-[10px] text-muted-foreground">
              #{kw}
            </Badge>
          ))
        ) : (
          <span className="text-[10px] text-muted-foreground/50">#키워드 없음</span>
        )}
      </CardFooter>
    </Card>
  )
}
