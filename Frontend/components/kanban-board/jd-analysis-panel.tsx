"use client"

import { useEffect, useMemo, useState } from "react"
import { Textarea } from "@/components/ui/textarea"
import { Button } from "@/components/ui/button"
import { Badge } from "@/components/ui/badge"
import { Save, Sparkles } from "lucide-react"
import { parseJdInsight } from "@/lib/application-intelligence"
import { type Application } from "@/lib/mock-data"

export function JDAnalysisPanel({
  application,
  onUpdateApplication,
}: {
  application: Application
  onUpdateApplication: (updates: Partial<Application>) => void | Promise<void>
}) {
  const [jdText, setJdText] = useState(application.rawJd || "")
  const [isSaving, setIsSaving] = useState(false)

  const aiData = parseJdInsight(application.aiInsight)
  const isAnalyzed = !!application.aiInsight
  const originalJd = application.rawJd || ""
  const hasChanges = useMemo(() => jdText !== originalJd, [jdText, originalJd])

  useEffect(() => {
    setJdText(application.rawJd || "")
  }, [application.id, application.rawJd])

  const handleSave = async () => {
    setIsSaving(true)
    try {
      await onUpdateApplication({ rawJd: jdText })
    } finally {
      setIsSaving(false)
    }
  }

  return (
    <div className="space-y-6">
      <div>
        <div className="mb-2 flex items-center justify-between gap-3">
          <label className="text-sm font-medium">채용 공고 원문 (JD)</label>
          <Button
            onClick={handleSave}
            disabled={!hasChanges || isSaving}
            size="sm"
            className="gap-2 rounded-full"
          >
            <Save className={`size-4 ${isSaving ? "animate-pulse" : ""}`} />
            저장
          </Button>
        </div>
        <Textarea
          placeholder="채용 공고 내용을 수정하세요."
          className="min-h-[160px] resize-none border-primary/10"
          value={jdText}
          onChange={(event) => setJdText(event.target.value)}
        />
        <p className="mt-1 text-[10px] text-muted-foreground">
          공고 원문을 바로 수정할 수 있습니다. 저장해도 기존 분석 결과는 유지되며, 필요하면 다시 분석하면 됩니다.
        </p>
      </div>

      {isAnalyzed ? (
        <div className="space-y-5 border-t border-border pt-4">
          {aiData?.requirements ? (
            <>
              <div>
                <h4 className="mb-3 flex items-center gap-2 text-sm font-semibold">
                  <div className="size-2 rounded-full bg-primary" />
                  필수 기술 스택
                </h4>
                <div className="flex flex-wrap gap-2">
                  {(aiData.requirements.technicalSkills || []).map((skill: string) => (
                    <Badge key={skill} className="border-0 bg-primary/10 text-primary hover:bg-primary/20">
                      {skill}
                    </Badge>
                  ))}
                </div>
              </div>

              <div>
                <h4 className="mb-3 flex items-center gap-2 text-sm font-semibold">
                  <div className="size-2 rounded-full bg-chart-2" />
                  핵심 요구 역량
                </h4>
                <div className="space-y-2">
                  {(aiData.requirements.coreCompetencies || []).map((competency: string, index: number) => (
                    <div key={`${competency}-${index}`} className="flex items-start gap-2 text-sm">
                      <span className="flex size-5 shrink-0 items-center justify-center rounded bg-chart-2/10 text-xs font-medium text-chart-2">
                        {index + 1}
                      </span>
                      <span className="text-muted-foreground">{competency}</span>
                    </div>
                  ))}
                </div>
              </div>

              {(aiData.requirements.preferredExperience || []).length > 0 && (
                <div>
                  <h4 className="mb-3 flex items-center gap-2 text-sm font-semibold">
                    <div className="size-2 rounded-full bg-chart-3" />
                    우대 사항
                  </h4>
                  <div className="flex flex-wrap gap-2">
                    {(aiData.requirements.preferredExperience || []).map((qualification: string) => (
                      <Badge key={qualification} variant="outline" className="text-xs">
                        {qualification}
                      </Badge>
                    ))}
                  </div>
                </div>
              )}
            </>
          ) : null}
        </div>
      ) : (
        <div className="mt-4 border-t border-dashed border-border py-10 text-center text-muted-foreground">
          <Sparkles className="mx-auto mb-2 size-8 opacity-20" />
          <p className="text-sm">분석된 AI 데이터가 없습니다.</p>
        </div>
      )}
    </div>
  )
}
