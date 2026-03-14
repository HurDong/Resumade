"use client"

import { useState } from "react"
import {
  Sparkles,
  FileText,
  Wand2,
  Loader2,
  Plus,
  ChevronRight,
  Settings,
} from "lucide-react"
import { Card, CardContent } from "@/components/ui/card"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Textarea } from "@/components/ui/textarea"
import { Progress } from "@/components/ui/progress"
import { ScrollArea } from "@/components/ui/scroll-area"
import { Separator } from "@/components/ui/separator"
import { Input } from "@/components/ui/input"
import { mockWorkspaceData } from "@/lib/mock-data"
import { useWorkspaceStore } from "@/lib/store/workspace-store"

export function ContextPanel() {
  const { extractedContext } = mockWorkspaceData
  const { 
    company,
    setCompany,
    position,
    setPosition,
    questions,
    activeQuestionId,
    setActiveQuestionId,
    addQuestion,
    updateActiveQuestion,
    isProcessing,
    progressMessage, 
    startProcessing,
    updateProgress,
    updateDraftIntermediate,
    updateWashedIntermediate,
    completeProcessing,
    setError
  } = useWorkspaceStore()

  const activeQuestion = questions.find(q => q.id === activeQuestionId) || questions[0]

  const handleStartProcess = () => {
    startProcessing()

    const questionId = activeQuestion.dbId || activeQuestion.id
    
    // We send the process to the backend with the question ID
    const eventSource = new EventSource(
      `/api/workspace/stream/${questionId}`
    )

    eventSource.addEventListener("progress", (event) => {
      updateProgress(event.data)
    })

    eventSource.addEventListener("draft_intermediate", (event) => {
      updateDraftIntermediate(event.data)
    })

    eventSource.addEventListener("washed_intermediate", (event) => {
      updateWashedIntermediate(event.data)
    })

    eventSource.addEventListener("complete", (event) => {
      const data = JSON.parse(event.data)
      completeProcessing(data)
      eventSource.close()
    })

    eventSource.addEventListener("error", (event) => {
      setError(event.data)
      eventSource.close()
    })

    eventSource.onerror = () => {
      setError("서버와의 연결이 끊어졌습니다.")
      eventSource.close()
    }
  }

  return (
    <div className="flex h-full flex-col border-r border-border bg-background">
      {/* Header Context */}
      <div className="shrink-0 border-b border-border bg-muted/20 p-6">
        <div className="flex items-center gap-2 text-sm text-muted-foreground mb-6">
          <Settings className="size-4 shrink-0 text-muted-foreground/50" />
          <input
            value={company}
            onChange={(e) => setCompany(e.target.value)}
            className="bg-transparent border-none p-0 focus:ring-0 w-24 font-bold text-foreground"
            placeholder="회사명"
          />
          <span className="text-muted-foreground/30">|</span>
          <input
            value={position}
            onChange={(e) => setPosition(e.target.value)}
            className="bg-transparent border-none p-0 focus:ring-0 flex-1 text-muted-foreground"
            placeholder="지원 직무"
          />
        </div>

        {/* Question Tabs/List */}
        <div className="flex items-center gap-2 mb-4 overflow-x-auto no-scrollbar">
          {questions.map((q, idx) => (
            <Button
              key={q.id}
              variant={activeQuestionId === q.id ? "default" : "outline"}
              size="sm"
              onClick={() => setActiveQuestionId(q.id)}
              className="shrink-0 gap-2 h-8"
            >
              문항 {idx + 1}
              {activeQuestionId === q.id && <div className="size-1.5 rounded-full bg-primary-foreground" />}
            </Button>
          ))}
          <Button variant="ghost" size="icon" className="size-8 shrink-0 rounded-full" onClick={addQuestion}>
            <Plus className="size-4" />
          </Button>
        </div>
        
        <div className="space-y-4">
          <div className="flex items-start gap-4">
            <Textarea
              value={activeQuestion.title}
              onChange={(e) => updateActiveQuestion({ title: e.target.value })}
              className="text-lg font-bold leading-tight bg-transparent border-none focus-visible:ring-0 p-0 min-h-[50px] resize-none overflow-hidden placeholder:text-muted-foreground/30"
              placeholder="자소서 문항 내용을 입력하세요..."
              rows={2}
            />
          </div>

          <div className="flex flex-col gap-2">
            <div className="flex items-center justify-between text-xs text-muted-foreground mb-1">
              <span className="font-medium">글자수 진행도</span>
              <div className="flex items-center gap-1.5">
                <Input 
                  type="number" 
                  value={activeQuestion.maxLength} 
                  onChange={(e) => updateActiveQuestion({ maxLength: parseInt(e.target.value) || 0 })}
                  className="w-16 h-6 px-1.5 py-0 text-center text-[11px] bg-background border-muted-foreground/20"
                />
                <span className="opacity-50">자 제한</span>
              </div>
            </div>
            <div className="flex items-center gap-3">
              <Progress value={(activeQuestion.content.length / (activeQuestion.maxLength || 1000)) * 100} className="h-1.5 flex-1" />
              <span className={`text-xs tabular-nums font-semibold ${activeQuestion.content.length > activeQuestion.maxLength ? 'text-destructive' : 'text-primary'}`}>
                {activeQuestion.content.length} <span className="font-normal text-muted-foreground/50">/ {activeQuestion.maxLength}</span>
              </span>
            </div>
          </div>
        </div>
      </div>

      <ScrollArea className="flex-1 px-6 py-4">
        <div className="space-y-8 pb-10">
          {/* Progress Overlay during processing */}
          {isProcessing && (
            <Card className="border-primary/50 bg-primary/5 animate-in fade-in slide-in-from-top-2">
              <CardContent className="p-4 flex items-center gap-4">
                <div className="relative">
                  <Loader2 className="size-5 text-primary animate-spin" />
                  <div className="absolute inset-0 size-5 text-primary animate-ping opacity-20" />
                </div>
                <div className="flex-1">
                  <p className="text-sm font-semibold text-primary">{progressMessage}</p>
                </div>
              </CardContent>
            </Card>
          )}

          <section>
            <div className="flex items-center gap-2 mb-4">
              <Sparkles className="size-4 text-primary" />
              <h3 className="text-sm font-bold uppercase tracking-wider text-muted-foreground/70">관련 경험 콘텍스트 (RAG)</h3>
            </div>
            <div className="space-y-3">
              {extractedContext.map((ctx) => (
                <Card key={ctx.id} className="bg-muted/30 border-none shadow-none group hover:bg-primary/5 transition-colors">
                  <CardContent className="p-4">
                    <div className="flex items-center justify-between gap-2 mb-2">
                      <span className="text-sm font-bold group-hover:text-primary transition-colors">{ctx.experienceTitle}</span>
                      <Badge variant="outline" className="text-[10px] bg-background">
                        {ctx.relevanceScore}% 매칭
                      </Badge>
                    </div>
                    <p className="text-xs text-muted-foreground leading-relaxed line-clamp-2">
                      {ctx.relevantPart}
                    </p>
                  </CardContent>
                </Card>
              ))}
            </div>
          </section>

          <section>
            <div className="flex items-center justify-between mb-4">
              <div className="flex items-center gap-2">
                <Wand2 className="size-4 text-primary" />
                <h3 className="text-sm font-bold uppercase tracking-wider text-muted-foreground/70">AI 초안 작업장</h3>
              </div>
              <Button 
                size="sm" 
                variant="default" 
                onClick={handleStartProcess}
                disabled={isProcessing}
                className="gap-2 shadow-sm rounded-full px-4"
              >
                {isProcessing ? (
                  <Loader2 className="size-3 animate-spin" />
                ) : (
                  <Sparkles className="size-3" />
                )}
                {activeQuestion.content ? "다시 생성" : "초안 생성 및 세탁"}
              </Button>
            </div>
            <div className="relative">
              <div className="absolute top-3 left-3 flex items-center gap-1.5 pointer-events-none opacity-20">
                <FileText className="size-4" />
                <span className="text-[10px] font-bold uppercase tracking-tighter">Draft Pad</span>
              </div>
              <Textarea
                value={activeQuestion.content}
                onChange={(e) => updateActiveQuestion({ content: e.target.value })}
                className="min-h-[400px] w-full bg-muted/10 border-none focus-visible:ring-1 focus-visible:ring-primary/20 p-8 pt-12 text-sm leading-relaxed rounded-2xl placeholder:opacity-20"
                placeholder="나의 경험 데이터를 기반으로 AI가 초안을 작성합니다. 직접 내용을 입력하거나 수정할 수도 있습니다."
              />
            </div>
          </section>
        </div>
      </ScrollArea>
    </div>
  )
}
