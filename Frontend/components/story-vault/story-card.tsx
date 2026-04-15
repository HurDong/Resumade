"use client"

import { Clock, Edit2, Trash2, Quote, ScrollText } from "lucide-react"
import { Button } from "@/components/ui/button"
import { Card, CardHeader, CardContent, CardFooter } from "@/components/ui/card"
import { Badge } from "@/components/ui/badge"
import { cn } from "@/lib/utils"
import { type PersonalStory, STORY_TYPE_LABELS } from "@/lib/workspace/types"

interface StoryCardProps {
  story: PersonalStory
  onEdit: () => void
  onDelete: () => void
}

export function StoryCard({ story, onEdit, onDelete }: StoryCardProps) {
  const isGuide = story.type === "WRITING_GUIDE"

  return (
    <Card className={cn(
      "group flex h-full flex-col overflow-hidden transition-all",
      isGuide
        ? "border-amber-200/60 bg-amber-50/30 hover:border-amber-300/60 hover:shadow-md dark:border-amber-800/40 dark:bg-amber-950/20"
        : "border-border/50 bg-card/50 hover:border-primary/30 hover:shadow-md"
    )}>
      <CardHeader className="p-5 pb-0">
        <div className="flex items-start justify-between">
          <Badge
            variant="outline"
            className={cn(
              "rounded-md px-2 py-0.5 text-[10px] font-semibold",
              isGuide
                ? "border-amber-300/60 bg-amber-100/60 text-amber-700 dark:border-amber-700/60 dark:bg-amber-900/40 dark:text-amber-400"
                : "border-primary/20 bg-primary/5 text-primary"
            )}
          >
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
        <div className={cn(
          "mt-3 flex items-center gap-1.5 text-sm font-medium",
          isGuide ? "text-amber-700/70 dark:text-amber-400/70" : "text-foreground"
        )}>
          {isGuide ? (
            <>
              <ScrollText className="size-3.5" />
              AI 프롬프트 주입 가이드
            </>
          ) : (
            <>
              <Clock className="size-3.5 text-muted-foreground/60" />
              {story.period || "전체 시기"}
            </>
          )}
        </div>
      </CardHeader>

      <CardContent className="flex-1 p-5 pt-4">
        <div className="relative">
          {isGuide ? (
            <p className="line-clamp-5 font-mono text-xs leading-relaxed text-muted-foreground">
              {story.content}
            </p>
          ) : (
            <>
              <Quote className="absolute -left-1 -top-1 size-8 rotate-180 text-muted-foreground/10" />
              <p className="relative line-clamp-4 text-sm leading-relaxed text-muted-foreground">
                {story.content}
              </p>
            </>
          )}
        </div>
      </CardContent>

      <CardFooter className={cn(
        "flex flex-wrap gap-1.5 border-t p-4",
        isGuide
          ? "border-amber-200/40 bg-amber-50/50 dark:border-amber-800/30 dark:bg-amber-950/30"
          : "border-border/30 bg-muted/20"
      )}>
        {story.keywords && story.keywords.length > 0 ? (
          story.keywords.map((kw, idx) => (
            <Badge key={idx} variant="secondary" className="rounded-sm bg-muted/40 px-1.5 py-0 text-[10px] text-muted-foreground">
              #{kw}
            </Badge>
          ))
        ) : (
          <span className={cn(
            "text-[10px]",
            isGuide ? "text-amber-600/50 dark:text-amber-400/40" : "text-muted-foreground/50"
          )}>
            {isGuide ? "프롬프트 직접 주입" : "#키워드 없음"}
          </span>
        )}
      </CardFooter>
    </Card>
  )
}
