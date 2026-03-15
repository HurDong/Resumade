"use client"

import { useState } from "react"
import {
  Settings,
  Plus,
  FileText,
  Sparkles,
  Loader2,
  MessageSquare,
  Wand2,
  AlertTriangle,
  Lightbulb,
  ArrowRight,
  TrendingUp,
  Check,
} from "lucide-react"
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from "@/components/ui/card"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Textarea } from "@/components/ui/textarea"
import { Progress } from "@/components/ui/progress"
import { ScrollArea } from "@/components/ui/scroll-area"
import { Separator } from "@/components/ui/separator"
import { Input } from "@/components/ui/input"
import { Tabs, TabsList, TabsTrigger } from "@/components/ui/tabs"
import { useWorkspaceStore } from "@/lib/store/workspace-store"
import { Tooltip, TooltipContent, TooltipProvider, TooltipTrigger } from "@/components/ui/tooltip"

// Helper for standard character length (including spaces)
const getLength = (str: string) => {
  return str ? str.length : 0;
};

export function ContextPanel() {
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
    setError,
    extractedContext,
    leftPanelTab,
    setLeftPanelTab,
    setHoveredMistranslationId,
    hoveredMistranslationId,
    applySuggestion,
    dismissMistranslation
  } = useWorkspaceStore()

  const activeQuestion = questions.find(q => q.id === activeQuestionId) || questions[0]
  const { mistranslations, aiReviewReport, washedKr } = activeQuestion

  const currentCount = getLength(washedKr || activeQuestion.content)

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
    <div className="flex h-full flex-col border-r border-border bg-background min-h-0 overflow-hidden">
      {/* Header Context */}
      <div className="shrink-0 border-b border-border bg-muted/20 p-6">
        <div className="flex items-center gap-2 text-sm text-muted-foreground mb-6">
          <Settings className="size-4 shrink-0 text-muted-foreground/60" />
          <input
            value={company}
            onChange={(e) => setCompany(e.target.value)}
            className="bg-transparent border-none p-0 focus:ring-0 w-24 font-bold text-foreground"
            placeholder="회사명"
          />
          <span className="text-muted-foreground/40">|</span>
          <input
            value={position}
            onChange={(e) => setPosition(e.target.value)}
            className="bg-transparent border-none p-0 focus:ring-0 flex-1 text-muted-foreground/80"
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
              className="text-lg font-bold leading-tight bg-transparent border-none focus-visible:ring-0 p-0 min-h-[50px] resize-none overflow-hidden placeholder:text-muted-foreground/50"
              placeholder="자소서 문항 내용을 입력하세요..."
              rows={2}
            />
          </div>

          <div className="flex flex-col gap-2 p-4 bg-muted/20 rounded-2xl border border-muted-foreground/10 group hover:bg-muted/30 transition-all">
            <div className="flex items-center justify-between text-xs text-muted-foreground mb-1 px-1">
              <div className="flex items-center gap-2">
                <TrendingUp className={`size-3.5 transition-colors ${currentCount > activeQuestion.maxLength ? 'text-destructive animate-pulse' : 'text-primary'}`} />
                <span className="font-bold text-muted-foreground/80 lowercase tracking-tight">글자수 진행도 (공백 포함)</span>
              </div>
              <div className="flex items-center gap-1.5">
                <span className="font-bold text-[10px] uppercase opacity-40">Limit</span>
                <Input 
                  type="number" 
                  value={activeQuestion.maxLength} 
                  onChange={(e) => updateActiveQuestion({ maxLength: parseInt(e.target.value) || 0 })}
                  className="w-16 h-6 px-1.5 py-0 text-center text-[11px] font-black bg-background border-primary/20 focus-visible:ring-primary/30 rounded-md"
                />
                <span className="font-black text-[10px] text-muted-foreground/40">자</span>
              </div>
            </div>
            
            <div className="flex items-center gap-4 px-1">
              <div className="flex-1">
                <Progress 
                  value={Math.min((currentCount / (activeQuestion.maxLength || 1)) * 100, 100)} 
                  className={`h-2 shadow-inner transition-all ${currentCount > activeQuestion.maxLength ? 'bg-destructive/10' : ''}`}
                  indicatorClassName={currentCount > activeQuestion.maxLength ? 'bg-destructive' : 'bg-primary shadow-[0_0_8px_rgba(var(--primary),0.5)]'}
                />
              </div>
              <div className="flex flex-col items-end min-w-[80px]">
                <div className="flex items-baseline gap-1">
                  <span className={`text-sm tabular-nums font-black tracking-tighter transition-all ${currentCount > activeQuestion.maxLength ? 'text-destructive scale-110 drop-shadow-[0_0_4px_rgba(239,68,68,0.3)]' : 'text-primary'}`}>
                    {currentCount.toLocaleString()}
                  </span>
                  <span className="text-[10px] font-bold text-muted-foreground/40 italic"> / {activeQuestion.maxLength.toLocaleString()} 자</span>
                </div>
                {currentCount > activeQuestion.maxLength && (
                  <span className="text-[9px] font-black text-destructive uppercase tracking-widest animate-in fade-in slide-in-from-right-1">Over Limit!</span>
                )}
              </div>
            </div>
          </div>
        </div>
      </div>

      {/* Mode Switcher */}
      <div className="bg-muted/5 border-b border-border px-6 py-2 shrink-0">
        <Tabs value={leftPanelTab} onValueChange={(v) => setLeftPanelTab(v as any)} className="w-full">
          <TabsList className="grid w-full grid-cols-2 bg-muted/20">
            <TabsTrigger value="context" className="text-xs font-bold uppercase tracking-tighter gap-2">
              <FileText className="size-3" />
              작성 가이드
            </TabsTrigger>
            <TabsTrigger value="report" className="text-xs font-bold uppercase tracking-tighter gap-2" disabled={!aiReviewReport}>
              <Sparkles className="size-3" />
              AI 분석 리포트
              {aiReviewReport && <div className="size-1.5 rounded-full bg-primary" />}
            </TabsTrigger>
          </TabsList>
        </Tabs>
      </div>

      <ScrollArea className="flex-1 h-full min-h-0 px-6 py-4">
        <div className="space-y-8 pb-10">
          {leftPanelTab === "context" ? (
            <>
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
              <TrendingUp className="size-4 text-primary" />
              <h3 className="text-sm font-black uppercase tracking-wider text-primary">관련 경험 콘텍스트 (RAG)</h3>
            </div>
            <div className="space-y-3">
              {extractedContext.map((ctx) => (
                <Card key={ctx.id} className="bg-muted/40 border-none shadow-none group hover:bg-primary/5 transition-colors">
                  <CardContent className="p-4">
                    <div className="flex items-center justify-between gap-2 mb-2">
                      <span className="text-sm font-black group-hover:text-primary transition-colors text-foreground">{ctx.experienceTitle}</span>
                      <Badge variant="outline" className="text-[10px] bg-background border-primary/20 text-primary font-bold">
                        {ctx.relevanceScore}% 매칭
                      </Badge>
                    </div>
                    <p className="text-xs text-foreground/90 leading-relaxed font-medium">
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
                <h3 className="text-sm font-black uppercase tracking-wider text-primary">AI 초안 작업장</h3>
              </div>
              <Button 
                size="sm" 
                variant="default" 
                onClick={handleStartProcess}
                disabled={isProcessing}
                className="gap-2 shadow-sm rounded-full px-4 font-bold"
              >
                {isProcessing ? (
                  <Loader2 className="size-3 animate-spin" />
                ) : (
                  <Sparkles className="size-3" />
                )}
                {activeQuestion.content ? "다시 생성" : "초안 생성 및 세탁"}
              </Button>
            </div>

            {/* AI 작성 가이드 (Directives) */}
            <div className="space-y-3 pt-2">
              <label className="text-sm font-semibold flex items-center gap-2 text-primary">
                <MessageSquare className="w-4 h-4" />
                AI 작성 가이드 (Directives)
              </label>
              <Textarea
                placeholder="예: B 프로젝트 위주로 작성해줘, 좀 더 적극적인 어조로 세탁해줘 등..."
                className="min-h-[100px] text-sm bg-background/50 focus:bg-background border-primary/20 focus:border-primary transition-all duration-300 resize-none"
                value={activeQuestion.userDirective || ""}
                onChange={(e) => {
                  updateActiveQuestion({ userDirective: e.target.value });
                }}
              />
              <p className="text-[11px] text-muted-foreground leading-relaxed">
                * 입력한 지시사항이 초안 생성 및 세탁 과정에 최우선적으로 반영됩니다.
              </p>
            </div>

            <Separator className="bg-primary/10" />

            <div className="relative">
              <div className="absolute top-3 left-3 flex items-center gap-1.5 pointer-events-none opacity-70">
                <FileText className="size-4 text-primary" />
                <span className="text-[10px] font-black uppercase tracking-tighter text-primary">Draft Pad</span>
              </div>
              <Textarea
                value={activeQuestion.content}
                onChange={(e) => updateActiveQuestion({ content: e.target.value })}
                className={`min-h-[400px] w-full bg-muted/20 border-none focus-visible:ring-1 focus-visible:ring-primary/20 p-8 pt-12 text-sm leading-relaxed rounded-2xl placeholder:opacity-70 text-foreground font-medium transition-all ${hoveredMistranslationId ? 'ring-2 ring-primary/40 bg-primary/5 scale-[1.01]' : ''}`}
                placeholder="나의 경험 데이터를 기반으로 AI가 초안을 작성합니다. 직접 내용을 입력하거나 수정할 수도 있습니다."
              />
            </div>
          </section>
        </>
      ) : (
        /* Analysis Report Tab */
        <div className="space-y-6 animate-in fade-in slide-in-from-left-2">
          <Card className="border-none bg-primary/5 shadow-none overflow-hidden border border-primary/20">
            <CardContent className="p-6 flex gap-4">
              <div className="p-3 rounded-2xl bg-primary/10 text-primary h-fit">
                <Sparkles className="size-6" />
              </div>
              <div className="flex-1">
                <h4 className="text-sm font-black text-primary uppercase tracking-tighter mb-1">Detected Key Summary</h4>
                <p className="text-[13px] text-foreground/80 leading-relaxed font-bold italic">
                  "{aiReviewReport?.summary}"
                </p>
              </div>
            </CardContent>
          </Card>

          <div className="space-y-4">
            <div className="flex items-center gap-2 mb-2">
              <AlertTriangle className="size-4 text-destructive" />
              <h5 className="text-xs font-black uppercase tracking-tighter">감지된 주요 오역 ({mistranslations.filter(m => !m.original.includes("최적의 휴먼 패치")).length})</h5>
            </div>
            
            <div className="space-y-4">
              {mistranslations
                .filter(m => !m.original.includes("최적의 휴먼 패치") && !m.translated.includes("세탁본 전반"))
                .map((mis, idx) => {
                const isHovered = hoveredMistranslationId === mis.id
                return (
                  <div
                    key={`${mis.id}-${idx}`}
                    onMouseEnter={() => setHoveredMistranslationId(mis.id)}
                    onMouseLeave={() => setHoveredMistranslationId(null)}
                    className={`p-5 rounded-3xl border-l-[8px] transition-all duration-300 cursor-pointer relative overflow-hidden group ${
                      isHovered 
                        ? "bg-primary/10 border-primary shadow-2xl translate-x-1 ring-1 ring-primary/20" 
                        : "bg-muted/10 border-transparent hover:bg-muted/20"
                    }`}
                  >
                    {/* Background decoration for focus */}
                    {isHovered && (
                      <div className="absolute inset-0 bg-gradient-to-br from-primary/5 to-transparent pointer-events-none" />
                    )}
                    
                    <div className="flex items-center justify-between mb-4">
                      <div className="flex items-center gap-2">
                        {isHovered ? (
                          <Badge variant={mis.severity === "high" ? "destructive" : "default"} className="text-[9px] font-black uppercase px-2 py-0.5 animate-in zoom-in-95">
                            {mis.severity === "high" ? "Critical Error" : "Style Warning"}
                          </Badge>
                        ) : (
                          <div className={`p-1.5 rounded-full ${mis.severity === "high" ? 'bg-destructive/10 text-destructive' : 'bg-muted-foreground/10 text-muted-foreground'}`}>
                            <AlertTriangle className="size-3" />
                          </div>
                        )}
                      </div>
                      <div className="flex items-center gap-2">
                        <Button 
                          size="sm" 
                          variant="secondary" 
                          className="h-7 px-2.5 text-[10px] font-bold gap-1.5 rounded-lg border shadow-sm hover:bg-primary hover:text-primary-foreground transition-all"
                          onClick={(e) => {
                            e.stopPropagation();
                            applySuggestion(mis.id);
                          }}
                        >
                          <Check className="size-3" />
                          반영하기
                        </Button>
                        <TooltipProvider>
                          <Tooltip>
                            <TooltipTrigger asChild>
                              <Button 
                                size="sm" 
                                variant="ghost" 
                                className="h-7 px-2 text-[10px] font-bold text-muted-foreground/40 hover:text-destructive hover:bg-destructive/10 rounded-lg transition-all"
                                onClick={(e) => {
                                  e.stopPropagation();
                                  dismissMistranslation(mis.id);
                                }}
                              >
                                넘기기
                              </Button>
                            </TooltipTrigger>
                            <TooltipContent>
                              <p className="text-[10px]">이 오역은 무시하고 리스트에서 제거합니다.</p>
                            </TooltipContent>
                          </Tooltip>
                        </TooltipProvider>
                        <span className="text-[10px] font-bold text-muted-foreground/40 italic">Case #{idx + 1}</span>
                      </div>
                    </div>
                  <div className="flex items-center gap-3 mb-4">
                    <div className={`px-2.5 py-1 rounded-md bg-background border text-[11px] font-mono font-bold italic truncate max-w-[120px] transition-colors ${hoveredMistranslationId === mis.id ? 'border-primary/50 text-primary bg-primary/5' : 'text-foreground/70'}`}>
                      {mis.original}
                    </div>
                    <ArrowRight className={`size-3.5 transition-colors ${hoveredMistranslationId === mis.id ? 'text-primary' : 'text-muted-foreground/30'}`} />
                    <div className="px-2.5 py-1 rounded-md bg-background border line-through text-[11px] font-mono text-muted-foreground/50 truncate max-w-[120px]">
                      {mis.translated}
                    </div>
                  </div>
                  
                  <div className="text-[13px] font-bold text-foreground/90 leading-relaxed bg-primary/5 p-4 rounded-2xl border border-primary/20 shadow-sm transition-all group-hover:bg-primary/10">
                    <div className="flex items-center gap-2 mb-1.5">
                      <Lightbulb className="size-3.5 text-primary" />
                      <span className="text-[10px] uppercase tracking-wider font-black text-primary">AI Suggestion</span>
                    </div>
                    {mis.suggestion}
                  </div>
                  </div>
                )
              })}
            </div>
          </div>
        </div>
      )}
        </div>
      </ScrollArea>
    </div>
  )
}
