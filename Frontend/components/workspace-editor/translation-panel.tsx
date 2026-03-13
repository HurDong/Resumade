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
import { Button } from "@/components/ui/button"
import { ScrollArea } from "@/components/ui/scroll-area"
import { Separator } from "@/components/ui/separator"
import { mockWorkspaceData } from "@/lib/mock-data"

export function TranslationPanel() {
  const { originalText, translatedText, mistranslations, aiReviewReport } = mockWorkspaceData

  // Function to highlight mistranslated words
  const highlightText = (text: string) => {
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

  return (
    <div className="flex h-full flex-col bg-muted/20">
      <div className="shrink-0 border-b border-border p-6">
        <Button size="lg" className="w-full gap-2 text-base font-semibold h-14">
          <Wand2 className="size-5" />
          휴먼 패치 (문맥 정제)
          <ArrowRight className="size-5 ml-auto" />
        </Button>
      </div>

      <ScrollArea className="flex-1 p-6">
        <div className="space-y-6">
          <div className="grid grid-cols-2 gap-4">
            <Card>
              <CardHeader className="pb-3">
                <CardTitle className="text-sm font-medium flex items-center gap-2">
                  <FileText className="size-4" />
                  원문 (한국어)
                </CardTitle>
              </CardHeader>
              <CardContent>
                <p className="text-sm leading-relaxed text-muted-foreground whitespace-pre-wrap">
                  {originalText}
                </p>
              </CardContent>
            </Card>

            <Card className="border-primary/30">
              <CardHeader className="pb-3">
                <CardTitle className="text-sm font-medium flex items-center gap-2">
                  <CheckCircle className="size-4 text-primary" />
                  번역본 (후처리)
                </CardTitle>
                <CardDescription className="text-xs">
                  강조된 용어는 잠재적 오역을 나타냅니다
                </CardDescription>
              </CardHeader>
              <CardContent>
                <p
                  className="text-sm leading-relaxed whitespace-pre-wrap"
                  dangerouslySetInnerHTML={{ __html: highlightText(translatedText) }}
                />
              </CardContent>
            </Card>
          </div>

          <Card className="border-amber-500/30 bg-amber-50/50 dark:bg-amber-950/20">
            <CardHeader className="pb-3">
              <CardTitle className="text-base flex items-center gap-2">
                <Lightbulb className="size-5 text-amber-600 dark:text-amber-400" />
                AI 오역 검토 리포트
              </CardTitle>
              <CardDescription>
                {aiReviewReport.summary}
              </CardDescription>
            </CardHeader>
            <CardContent className="space-y-4">
              <div className="grid grid-cols-3 gap-4">
                <div className="text-center p-3 rounded-lg bg-background">
                  <div className="text-2xl font-bold text-primary">{aiReviewReport.overallScore}%</div>
                  <div className="text-xs text-muted-foreground">종합 점수</div>
                </div>
                <div className="text-center p-3 rounded-lg bg-background">
                  <div className="text-2xl font-bold text-destructive">{aiReviewReport.technicalAccuracy}%</div>
                  <div className="text-xs text-muted-foreground">기술용어 정확성</div>
                </div>
                <div className="text-center p-3 rounded-lg bg-background">
                  <div className="text-2xl font-bold text-chart-2">{aiReviewReport.readability}%</div>
                  <div className="text-xs text-muted-foreground">가독성</div>
                </div>
              </div>

              <Separator />

              <div>
                <h4 className="text-sm font-semibold mb-3 flex items-center gap-2">
                  <AlertTriangle className="size-4 text-amber-600 dark:text-amber-400" />
                  감지된 오역 ({mistranslations.length})
                </h4>
                <div className="space-y-3">
                  {mistranslations.map((mis) => (
                    <div
                      key={mis.id}
                      className={`p-4 rounded-lg border-2 transition-all ${
                        mis.severity === "high"
                          ? "bg-red-100/80 border-red-400 dark:bg-red-950/40 dark:border-red-600 shadow-sm shadow-red-200 dark:shadow-red-900/30"
                          : "bg-amber-50/50 border-amber-200 dark:bg-amber-950/20 dark:border-amber-900"
                      }`}
                    >
                      <div className="flex items-center gap-2 mb-2">
                        {mis.severity === "high" && (
                          <AlertTriangle className="size-4 text-red-600 dark:text-red-400 animate-pulse" />
                        )}
                        <Badge
                          variant={mis.severity === "high" ? "destructive" : "secondary"}
                          className={`text-xs ${mis.severity === "high" ? "bg-red-600 dark:bg-red-700" : ""}`}
                        >
                          {mis.severity === "high" ? "심각한 오역" : "경고"}
                        </Badge>
                      </div>
                      <div className="flex items-center gap-2 mb-2 flex-wrap">
                        <code className={`text-sm font-mono px-2 py-1 rounded ${
                          mis.severity === "high" 
                            ? "bg-red-200 dark:bg-red-900/50 text-red-800 dark:text-red-200" 
                            : "bg-background"
                        }`}>
                          {mis.original}
                        </code>
                        <ArrowRight className="size-3 text-muted-foreground" />
                        <code className={`text-sm font-mono px-2 py-1 rounded line-through ${
                          mis.severity === "high"
                            ? "bg-red-200/50 dark:bg-red-900/30 text-red-600 dark:text-red-400"
                            : "bg-background text-muted-foreground"
                        }`}>
                          {mis.translated}
                        </code>
                      </div>
                      <p className={`text-xs mt-2 ${
                        mis.severity === "high" 
                          ? "text-red-700 dark:text-red-300 font-medium" 
                          : "text-muted-foreground"
                      }`}>
                        {mis.suggestion}
                      </p>
                    </div>
                  ))}
                </div>
              </div>
            </CardContent>
          </Card>
        </div>
      </ScrollArea>
    </div>
  )
}
