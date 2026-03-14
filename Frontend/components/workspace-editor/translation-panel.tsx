"use client"

import {
  FileText,
  AlertTriangle,
  CheckCircle,
  Lightbulb,
  ArrowRight,
  Wand2,
} from "lucide-react"
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from "@/components/ui/card"
import { Badge } from "@/components/ui/badge"
import { ScrollArea } from "@/components/ui/scroll-area"
import { Separator } from "@/components/ui/separator"
import { useWorkspaceStore } from "@/lib/store/workspace-store"

export function TranslationPanel() {
  const { questions, activeQuestionId, isProcessing, progressMessage } = useWorkspaceStore()
  
  const activeQuestion = questions.find(q => q.id === activeQuestionId) || questions[0]
  const { content: draft, washedKr, mistranslations, aiReviewReport } = activeQuestion

  // Function to highlight mistranslated words
  const highlightText = (text: string) => {
    if (!text) return ""
    let result = text
    mistranslations.forEach((mis) => {
      const regex = new RegExp(mis.translated, "g")
      const severityClass =
        mis.severity === "high"
          ? "bg-[var(--highlight-error)] text-foreground"
          : "bg-[var(--highlight-warning)] text-foreground"
      result = result.replace(
        regex,
        `<mark class="${severityClass} px-0.5 rounded">${mis.translated}</mark>`
      )
    })
    return result
  }

  if (!draft && !washedKr && !isProcessing) {
    return (
      <div className="flex flex-col items-center justify-center h-full bg-muted/5 text-muted-foreground p-10 text-center">
        <div className="relative mb-6">
          <Wand2 className="size-16 opacity-10" />
          <div className="absolute inset-0 size-16 bg-primary/5 rounded-full blur-2xl -z-10" />
        </div>
        <h3 className="text-xl font-bold mb-3">휴먼 패치 작업실</h3>
        <p className="text-sm max-w-sm text-balance text-muted-foreground/60 leading-relaxed">
          왼쪽 패널에서 초안을 생성하면 [영어 → 한국어] 역번역을 통한 
          AI 탐지 우회 및 문맥 정제 과정이 이곳에 표시됩니다.
        </p>
      </div>
    )
  }

  return (
    <div className="flex h-full flex-col bg-muted/10 overflow-hidden">
      {/* Header */}
      <div className="shrink-0 border-b border-border p-6 bg-background/50 backdrop-blur-md sticky top-0 z-10">
        <div className="flex items-center justify-between">
          <div>
            <h3 className="text-lg font-bold flex items-center gap-2">
              <CheckCircle className="size-5 text-emerald-500" />
              휴먼 패치 결과
            </h3>
            <p className="text-xs text-muted-foreground mt-1">
              역번역(EN→KR)을 거쳐 기계적인 문투를 지우고 자연스럽게 다듬었습니다.
            </p>
          </div>
          {isProcessing && (
            <Badge variant="secondary" className="animate-in fade-in zoom-in-95 gap-2 py-1.5 px-3">
              <div className="size-2 rounded-full bg-primary animate-pulse" />
              <span className="text-[11px] font-bold">{progressMessage}</span>
            </Badge>
          )}
        </div>
      </div>

      <ScrollArea className="flex-1">
        <div className="p-6 pb-20 space-y-8 animate-in fade-in slide-in-from-bottom-2 duration-500">
          <div className="grid grid-cols-1 xl:grid-cols-2 gap-6">
            {/* Original AI Draft */}
            <Card className="shadow-sm border-none bg-background/80">
              <CardHeader className="pb-4">
                <CardTitle className="text-xs font-bold uppercase tracking-widest text-muted-foreground/50 flex items-center gap-2">
                  <FileText className="size-3.5" />
                  원문 (AI 초안)
                </CardTitle>
              </CardHeader>
              <CardContent>
                <div className="text-sm leading-relaxed text-muted-foreground/80 whitespace-pre-wrap min-h-[200px] font-medium italic opacity-70">
                  {draft || "초안 생성 대기 중..."}
                </div>
              </CardContent>
            </Card>

            {/* Washed Version (Human Patch) */}
            <Card className="shadow-lg border-primary/20 bg-background ring-1 ring-primary/5">
              <CardHeader className="pb-4">
                <CardTitle className="text-xs font-bold uppercase tracking-widest text-primary flex items-center gap-2">
                  <CheckCircle className="size-3.5" />
                  번역본 (Human Patch)
                </CardTitle>
                <CardDescription className="text-[10px] text-muted-foreground/60 italic font-medium">
                  하이라이트된 용어는 IT 문맥에 맞춰 검수가 필요한 부분입니다.
                </CardDescription>
              </CardHeader>
              <CardContent>
                <div
                  className="text-sm leading-relaxed whitespace-pre-wrap min-h-[200px] font-medium"
                  dangerouslySetInnerHTML={{ __html: highlightText(washedKr) }}
                />
                {!washedKr && isProcessing && (
                  <div className="flex flex-col items-center justify-center py-20 gap-3 opacity-20">
                    <Wand2 className="size-8 animate-spin" />
                    <span className="text-xs font-bold tracking-tighter">Washing in progress...</span>
                  </div>
                )}
              </CardContent>
            </Card>
          </div>

          {/* AI Analysis Report */}
          {aiReviewReport && (
            <div className="space-y-6 pt-4">
              <div className="flex items-center gap-2">
                <div className="h-px flex-1 bg-border" />
                <span className="text-[10px] font-black uppercase tracking-[0.2em] text-muted-foreground/40 px-4 italic">Analysis Report</span>
                <div className="h-px flex-1 bg-border" />
              </div>

              <Card className="border-none bg-background shadow-xl overflow-hidden ring-1 ring-border/50">
                <div className="bg-amber-500/10 p-6 flex flex-col sm:flex-row sm:items-center gap-4">
                  <div className="p-3 rounded-2xl bg-amber-500/20 text-amber-600">
                    <Lightbulb className="size-8" />
                  </div>
                  <div className="flex-1">
                    <h4 className="text-lg font-bold text-amber-900 dark:text-amber-400">AI 오역 검토 리포트</h4>
                    <p className="text-sm text-amber-800/70 dark:text-amber-300/70 leading-relaxed font-medium mt-1">
                      {aiReviewReport.summary}
                    </p>
                  </div>
                </div>

                <div className="p-6 space-y-8">
                  <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
                    <div className="relative group">
                      <div className="absolute inset-0 bg-primary/10 rounded-2xl blur-xl group-hover:bg-primary/20 transition-colors" />
                      <div className="relative p-6 rounded-2xl bg-background border border-border/50 text-center">
                        <div className="text-4xl font-black text-primary mb-1">{aiReviewReport.overallScore}%</div>
                        <div className="text-[10px] font-bold uppercase tracking-widest text-muted-foreground">종합 신뢰도</div>
                      </div>
                    </div>
                    <div className="relative group">
                      <div className="absolute inset-0 bg-destructive/5 rounded-2xl blur-xl group-hover:bg-destructive/10 transition-colors" />
                      <div className="relative p-6 rounded-2xl bg-background border border-border/50 text-center">
                        <div className="text-4xl font-black text-destructive mb-1">{aiReviewReport.technicalAccuracy}%</div>
                        <div className="text-[10px] font-bold uppercase tracking-widest text-muted-foreground">기술용어 정확도</div>
                      </div>
                    </div>
                    <div className="relative group">
                      <div className="absolute inset-0 bg-emerald-500/5 rounded-2xl blur-xl group-hover:bg-emerald-500/10 transition-colors" />
                      <div className="relative p-6 rounded-2xl bg-background border border-border/50 text-center">
                        <div className="text-4xl font-black text-emerald-500 mb-1">{aiReviewReport.readability}%</div>
                        <div className="text-[10px] font-bold uppercase tracking-widest text-muted-foreground">문장 가독성</div>
                      </div>
                    </div>
                  </div>

                  {mistranslations.length > 0 && (
                    <div className="space-y-4">
                      <div className="flex items-center gap-2 mb-2">
                        <AlertTriangle className="size-4 text-destructive" />
                        <h5 className="text-sm font-bold">감지된 주요 오역 및 수정 제안 ({mistranslations.length})</h5>
                      </div>
                      
                      <div className="grid grid-cols-1 gap-4">
                        {mistranslations.map((mis) => (
                          <div
                            key={mis.id}
                            className={`p-5 rounded-2xl border-l-4 transition-all shadow-sm ${
                              mis.severity === "high"
                                ? "bg-red-50/50 border-red-500 dark:bg-red-950/20"
                                : "bg-muted/30 border-muted"
                            }`}
                          >
                            <div className="flex items-center justify-between mb-3">
                              <Badge
                                variant={mis.severity === "high" ? "destructive" : "secondary"}
                                className="text-[10px] font-black uppercase tracking-tighter"
                              >
                                {mis.severity === "high" ? "심각한 오류" : "단어 적합성 경고"}
                              </Badge>
                            </div>
                            
                            <div className="flex items-center gap-4 flex-wrap mb-3">
                              <div className="px-3 py-1.5 rounded-lg bg-background border text-sm font-mono font-bold italic">
                                {mis.original}
                              </div>
                              <ArrowRight className="size-4 text-muted-foreground/30 animate-pulse" />
                              <div className="px-3 py-1.5 rounded-lg bg-background border line-through text-sm font-mono text-muted-foreground/50">
                                {mis.translated}
                              </div>
                            </div>
                            
                            <p className="text-xs font-semibold text-foreground/80 leading-relaxed bg-background/50 p-3 rounded-xl border-dashed border">
                              <span className="text-primary mr-2 italic">💡 Suggestion:</span>
                              {mis.suggestion}
                            </p>
                          </div>
                        ))}
                      </div>
                    </div>
                  )}
                </div>
              </Card>
            </div>
          )}
        </div>
      </ScrollArea>
    </div>
  )
}
