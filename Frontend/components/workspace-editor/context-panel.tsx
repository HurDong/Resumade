"use client"

import { useState, useEffect, useRef } from "react"
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
  Trash2,
  CheckCircle2,
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
    refineDraft,
    generateDraft,
    setError,
    extractedContext,
    leftPanelTab,
    setLeftPanelTab,
    setHoveredMistranslationId,
    hoveredMistranslationId,
    deleteActiveQuestion,
    toggleActiveQuestionCompletion,
    applySuggestion,
    updateMistranslationSuggestion,
    dismissMistranslation
  } = useWorkspaceStore()

  // Virtual Progress Management for Premium UX
  const [virtualProgress, setVirtualProgress] = useState(0)
  const [isFinishing, setIsFinishing] = useState(false)
  const completionDataRef = useRef<any>(null)
  const topRef = useRef<HTMLDivElement>(null)

  const scrollToTop = () => {
    topRef.current?.scrollIntoView({ behavior: "smooth", block: "start" })
  }

  // Manage virtual progress increment
  useEffect(() => {
    let interval: NodeJS.Timeout
    if (isProcessing && !isFinishing) {
      // Pace to ~15 seconds for 90% (90 / (15 / 0.3) = 1.8 avg increment)
      interval = setInterval(() => {
        setVirtualProgress((prev) => {
          if (prev < 90) return prev + (Math.random() * 1.6 + 1.0) // 1.0~2.6 range, avg 1.8
          return prev
        })
      }, 300)
    } else if (isFinishing) {
      // Fast forward to 100% when backend is done
      interval = setInterval(() => {
        setVirtualProgress((prev) => {
          if (prev >= 100) {
            clearInterval(interval)
            return 100
          }
          return prev + 5
        })
      }, 50)
    } else {
      setVirtualProgress(0)
      setIsFinishing(false)
      completionDataRef.current = null
    }
    return () => clearInterval(interval)
  }, [isProcessing, isFinishing])

  // EFFECTIVE COMPLETION: Trigger store update only when animation reaches 100%
  useEffect(() => {
    if (isFinishing && virtualProgress >= 100 && completionDataRef.current) {
      // Use a tiny timeout to ensure it's outside of any rendering loop
      const timer = setTimeout(() => {
        completeProcessing(completionDataRef.current)
        setIsFinishing(false)
        completionDataRef.current = null
      }, 100)
      return () => clearTimeout(timer)
    }
  }, [isFinishing, virtualProgress, completeProcessing])

  // Sync with real progress messages to "nudge" the virtual progress if server is ahead
  useEffect(() => {
    if (progressMessage.includes('DRAFT')) setVirtualProgress(p => Math.max(25, p))
    if (progressMessage.includes('TRANSLATE')) setVirtualProgress(p => Math.max(50, p))
    if (progressMessage.includes('PATCH')) setVirtualProgress(p => Math.max(75, p))
  }, [progressMessage])

  // Mapping virtual progress to UI steps
  const getStepStatus = (stepIdx: number) => {
    const threshold = stepIdx * 25
    const isCompleted = virtualProgress >= threshold + 25
    const isActive = virtualProgress >= threshold && virtualProgress < threshold + 25
    return { isCompleted, isActive }
  }

  const activeQuestion = questions.find(q => q.id === activeQuestionId) || questions[0]
  const { mistranslations, aiReviewReport, washedKr } = activeQuestion

  const currentCount = getLength(washedKr || activeQuestion.content)

  const handleStartProcess = () => {
    scrollToTop()
    generateDraft()
  }

  return (
    <div className="flex h-full flex-col border-r border-border bg-background min-h-0 overflow-hidden">
      {/* Header Context */}
      <div className="shrink-0 border-b border-border bg-muted/20 p-6">
        <div className="flex items-center gap-2 text-sm text-muted-foreground mb-6">
          <Settings className="size-4 shrink-0 text-muted-foreground/60" />
          {!company && isProcessing ? (
            <div className="h-5 w-24 bg-muted animate-pulse rounded-md" />
          ) : (
            <input
              value={company}
              onChange={(e) => setCompany(e.target.value)}
              className="bg-transparent border-none p-0 focus:ring-0 w-24 font-bold text-foreground"
              placeholder="회사명"
            />
          )}
          <span className="text-muted-foreground/40">|</span>
          {!company && isProcessing ? (
            <div className="h-5 w-32 bg-muted animate-pulse rounded-md" />
          ) : (
            <input
              value={position}
              onChange={(e) => setPosition(e.target.value)}
              className="bg-transparent border-none p-0 focus:ring-0 flex-1 text-muted-foreground/80"
              placeholder="지원 직무"
            />
          )}
        </div>

        {/* Question Tabs/List */}
        <div className="flex items-center gap-2 mb-4 overflow-x-auto no-scrollbar">
          {!company && isProcessing ? (
            <>
              <div className="h-8 w-20 bg-muted animate-pulse rounded-md" />
              <div className="h-8 w-20 bg-muted animate-pulse rounded-md" />
              <div className="h-8 w-8 bg-muted animate-pulse rounded-full" />
            </>
          ) : (
            <>
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
              <Button variant="ghost" size="icon" className="size-8 shrink-0 rounded-full hover:bg-primary/10 hover:text-primary transition-colors" onClick={addQuestion} title="문항 추가">
                <Plus className="size-4" />
              </Button>
            </>
          )}
          {questions.length > 1 && !(!company && isProcessing) && (
            <Button variant="ghost" size="icon" className="size-8 shrink-0 rounded-full hover:bg-destructive/10 hover:text-destructive transition-colors ml-auto" onClick={() => deleteActiveQuestion()} title="현재 문항 삭제">
              <Trash2 className="size-4" />
            </Button>
          )}
        </div>
        
        <div className="space-y-4">
          <div className="flex items-start gap-3">
            <Button
              variant="ghost"
              size="icon"
              className={`size-10 shrink-0 rounded-xl transition-all border ${activeQuestion.isCompleted ? 'bg-emerald-50 text-emerald-600 border-emerald-200' : 'bg-background text-muted-foreground border-border'}`}
              onClick={() => toggleActiveQuestionCompletion()}
              disabled={!company && isProcessing}
              title={activeQuestion.isCompleted ? "작성 완료 취소" : "작성 완료로 표시"}
            >
              <CheckCircle2 className={`size-5 ${activeQuestion.isCompleted ? 'fill-emerald-600/10' : ''}`} />
            </Button>
            {!company && isProcessing ? (
              <div className="flex-1 space-y-2 py-2">
                <div className="h-4 w-full bg-muted animate-pulse rounded-md" />
                <div className="h-4 w-3/4 bg-muted animate-pulse rounded-md" />
              </div>
            ) : (
              <Textarea
                value={activeQuestion.title}
                onChange={(e) => updateActiveQuestion({ title: e.target.value })}
                onFocus={(e) => {
                  if (activeQuestion.title === "새로운 문항을 입력하세요.") {
                    updateActiveQuestion({ title: "" });
                  }
                }}
                className={`text-lg font-bold leading-tight bg-transparent border-none focus-visible:ring-0 p-0 min-h-[50px] resize-none overflow-hidden placeholder:text-muted-foreground/30 transition-all ${activeQuestion.isCompleted ? 'text-emerald-700/80 line-through decoration-emerald-500/30' : ''}`}
                placeholder="새로운 문항을 입력해 주세요..."
                rows={2}
              />
            )}
          </div>

          {!company && isProcessing ? (
            <div className="p-4 bg-muted/20 rounded-2xl border border-muted-foreground/10 space-y-4">
              <div className="h-3 w-32 bg-muted animate-pulse rounded-md" />
              <div className="flex items-center gap-4">
                <div className="flex-1 h-3 bg-muted animate-pulse rounded-full" />
                <div className="h-6 w-20 bg-muted animate-pulse rounded-md" />
              </div>
            </div>
          ) : (
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
          )}
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
        <div ref={topRef} />
        <div className="space-y-8 pb-10">
          {leftPanelTab === "context" ? (
            <>
          {/* Progress Overlay during processing */}
          {isProcessing && (
            <Card className="border-primary/30 bg-primary/5 shadow-2xl shadow-primary/10 animate-in fade-in slide-in-from-top-2 border-r-4 border-r-primary">
              <CardContent className="p-6">
                <div className="flex items-center gap-4 mb-6">
                  <div className="relative">
                    <div className="size-10 rounded-2xl bg-primary/20 flex items-center justify-center">
                      <Loader2 className="size-5 text-primary animate-spin" />
                    </div>
                    <div className="absolute -inset-1 rounded-2xl bg-primary/20 animate-ping opacity-30" />
                  </div>
                  <div>
                    <h4 className="text-sm font-black text-primary uppercase tracking-tighter">Human Patch Pipeline</h4>
                    <p className="text-[11px] font-bold text-primary/60 italic">AI가 문장 하나하나 고심하여 세탁하고 있습니다...</p>
                  </div>
                </div>

                <div className="space-y-4">
                  {[
                    { id: 'RAG', label: '경험 데이터 매칭' },
                    { id: 'DRAFT', label: 'AI 초안 작성' },
                    { id: 'WASH', label: '역번역 (영어→한글) 세탁' },
                    { id: 'PATCH', label: '휴먼 패치 분석 및 검토' },
                  ].map((step, idx, arr) => {
                    const { isCompleted, isActive } = getStepStatus(idx)
                    
                    return (
                      <div key={idx} className="flex items-center gap-3 relative">
                        <div className={`relative z-10 size-6 rounded-full flex items-center justify-center text-[10px] font-black border-2 transition-all duration-500 ${
                          isCompleted ? 'bg-primary border-primary text-white' : 
                          isActive ? 'bg-background border-primary text-primary animate-pulse shadow-[0_0_8px_rgba(var(--primary),0.4)]' : 
                          'bg-muted border-muted-foreground/20 text-muted-foreground/40'
                        }`}>
                          {isCompleted ? <Check className="size-3" strokeWidth={4} /> : idx + 1}
                        </div>
                        <div className="flex-1">
                          <p className={`text-xs font-bold transition-colors ${
                            isCompleted ? 'text-primary/60' : 
                            isActive ? 'text-primary' : 
                            'text-muted-foreground/30'
                          }`}>
                            {step.label}
                          </p>
                        </div>
                        {isActive && (
                          <div className="flex items-center gap-1">
                            <div className="size-1 bg-primary rounded-full animate-bounce [animation-delay:-0.3s]" />
                            <div className="size-1 bg-primary rounded-full animate-bounce [animation-delay:-0.15s]" />
                            <div className="size-1 bg-primary rounded-full animate-bounce" />
                          </div>
                        )}
                        {/* Connecting line */}
                        {idx < arr.length - 1 && (
                          <div className={`absolute left-3 top-6 w-0.5 h-4 -translate-x-1/2 transition-colors duration-500 ${isCompleted ? 'bg-primary' : 'bg-muted'}`} />
                        )}
                      </div>
                    )
                  })}
                </div>

                <div className="mt-6 pt-4 border-t border-primary/10">
                  <p className="text-[10px] font-mono text-muted-foreground/60 truncate">
                    <span className="text-primary/40 font-black mr-2">LOG</span>
                    {progressMessage}
                  </p>
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
                  <div className="text-[13px] font-bold text-foreground/90 leading-relaxed bg-primary/5 p-4 rounded-2xl border border-primary/20 shadow-sm transition-all group-hover:bg-primary/10">
                    <div className="flex items-center gap-2 mb-2">
                      <Lightbulb className="size-3.5 text-primary" />
                      <span className="text-[10px] uppercase tracking-wider font-black text-primary">직접 수정 (User Edit)</span>
                    </div>
                    <Textarea
                      value={mis.suggestion}
                      onChange={(e) => updateMistranslationSuggestion(mis.id, e.target.value)}
                      onMouseEnter={(e) => e.stopPropagation()} 
                      placeholder="수정 없이 '반영하기'를 누르면 '초안' 원문으로 복구됩니다."
                      className="min-h-[60px] p-0 bg-transparent border-none focus-visible:ring-0 text-sm leading-relaxed resize-none font-medium h-auto placeholder:text-muted-foreground/30 placeholder:italic"
                    />
                  </div>
                  </div>
                )
              })}
            </div>

            {/* Premium AI Refinement Section */}
            <Separator className="my-8 opacity-50" />
            <div className="p-6 rounded-[32px] bg-gradient-to-br from-primary/10 via-background to-secondary/5 border-2 border-primary/20 shadow-xl space-y-4">
              <div className="flex items-center justify-between">
                <div className="flex items-center gap-3">
                  <div className="p-2 rounded-xl bg-primary text-primary-foreground shadow-lg shadow-primary/20">
                    <Wand2 className="size-5" />
                  </div>
                  <div>
                    <h4 className="text-sm font-black text-primary uppercase tracking-tighter">추가 피드백 기반 리터칭</h4>
                    <p className="text-[10px] text-muted-foreground font-bold">원하는 방향으로 한 번 더 세탁합니다.</p>
                  </div>
                </div>
                <Button 
                  size="sm"
                  disabled={isProcessing || !activeQuestion.userDirective}
                  onClick={() => {
                    setLeftPanelTab("context") // Switch tab first
                    setTimeout(() => {
                      scrollToTop()
                      refineDraft(activeQuestion.userDirective)
                    }, 0) // Delay scroll to next tick to ensure tab is rendered if needed
                  }}
                  className="h-8 gap-2 px-4 rounded-xl font-black text-[11px] shadow-lg shadow-primary/20 hover:scale-105 transition-all"
                >
                  <Sparkles className="size-3.5" />
                  다시 세탁하기
                </Button>
              </div>

              <div className="relative group">
                <Textarea
                  value={activeQuestion.userDirective}
                  onChange={(e) => updateActiveQuestion({ userDirective: e.target.value })}
                  placeholder="예: '조금 더 열정적인 톤으로 바꿔줘', '협업 능력을 더 강조해줘' 등..."
                  className="min-h-[100px] bg-background/50 border-2 border-muted-foreground/10 rounded-2xl p-4 text-sm font-medium focus:border-primary/40 focus:ring-0 focus:bg-background transition-all"
                />
              </div>
              <p className="text-[10px] text-center text-muted-foreground/60 font-medium">※ 이전의 번역본은 새롭게 생성된 결과로 대체됩니다.</p>
            </div>
          </div>
        </div>
      )}
        </div>
      </ScrollArea>
    </div>
  )
}
