"use client"

import { useState } from "react"
import {
  Sparkles,
  FileText,
  Wand2,
} from "lucide-react"
import { Card, CardContent } from "@/components/ui/card"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Textarea } from "@/components/ui/textarea"
import { Progress } from "@/components/ui/progress"
import { ScrollArea } from "@/components/ui/scroll-area"
import { Separator } from "@/components/ui/separator"
import { mockWorkspaceData } from "@/lib/mock-data"

export function ContextPanel() {
  const { currentQuestion, extractedContext, draft } = mockWorkspaceData
  const [draftText, setDraftText] = useState(draft)

  return (
    <div className="flex h-full flex-col border-r border-border">
      <div className="shrink-0 border-b border-border bg-muted/30 p-6">
        <div className="flex items-center gap-2 text-sm text-muted-foreground mb-2">
          <FileText className="size-4" />
          <span>{currentQuestion.company}</span>
          <span className="text-muted-foreground/50">|</span>
          <span>{currentQuestion.position}</span>
        </div>
        <h2 className="text-lg font-semibold leading-relaxed">
          질문 {currentQuestion.questionNumber}: {currentQuestion.questionTitle}
        </h2>
        <div className="mt-3 flex items-center gap-3">
          <Progress value={(draftText.length / currentQuestion.maxLength) * 100} className="h-2 flex-1" />
          <span className="text-sm text-muted-foreground tabular-nums">
            {draftText.length} / {currentQuestion.maxLength}
          </span>
        </div>
      </div>

      <ScrollArea className="flex-1 p-6">
        <div className="space-y-6">
          <div>
            <div className="flex items-center gap-2 mb-3">
              <Sparkles className="size-4 text-primary" />
              <h3 className="font-semibold">RAG 추출 콘텍스트</h3>
            </div>
            <div className="space-y-3">
              {extractedContext.map((ctx) => (
                <Card key={ctx.id} className="bg-primary/5 border-primary/20">
                  <CardContent className="p-4">
                    <div className="flex items-start justify-between gap-2 mb-2">
                      <span className="text-sm font-medium">{ctx.experienceTitle}</span>
                      <Badge variant="secondary" className="text-xs shrink-0">
                        {ctx.relevanceScore}% 일치
                      </Badge>
                    </div>
                    <p className="text-sm text-muted-foreground leading-relaxed">
                      {ctx.relevantPart}
                    </p>
                  </CardContent>
                </Card>
              ))}
            </div>
          </div>

          <Separator />

          <div>
            <div className="flex items-center justify-between mb-3">
              <div className="flex items-center gap-2">
                <Wand2 className="size-4 text-primary" />
                <h3 className="font-semibold">AI 초안</h3>
              </div>
              <Button size="sm" variant="outline">
                <Sparkles className="size-3 mr-1" />
                재생성
              </Button>
            </div>
            <Textarea
              value={draftText}
              onChange={(e) => setDraftText(e.target.value)}
              className="min-h-[300px] resize-none text-sm leading-relaxed"
              placeholder="AI 생성 초안이 여기에 표시됩니다..."
            />
          </div>
        </div>
      </ScrollArea>
    </div>
  )
}
