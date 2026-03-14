"use client"

import { useState } from "react"
import { Textarea } from "@/components/ui/textarea"
import { Button } from "@/components/ui/button"
import { Badge } from "@/components/ui/badge"
import { Sparkles } from "lucide-react"
import { type Application } from "@/lib/mock-data"

// Mock JD Analysis Data
const mockJDAnalysis = {
  requiredSkills: ["Spring Boot", "Kotlin", "MySQL", "Redis", "Kafka"],
  coreCompetencies: ["대용량 트래픽 처리 경험", "MSA 설계 경험", "협업 및 커뮤니케이션 능력"],
  preferredQualifications: ["오픈소스 기여 경험", "기술 블로그 운영", "컨퍼런스 발표 경험"],
}

export function JDAnalysisPanel({ application }: { application: Application }) {
  const [jdText, setJdText] = useState(application.rawJd || "")
  
  // Try to parse aiInsight if it's JSON
  let aiData: any = null
  try {
    if (application.aiInsight) {
      aiData = JSON.parse(application.aiInsight)
    }
  } catch (e) {
    console.warn("Failed to parse aiInsight JSON:", e)
  }

  const isAnalyzed = !!application.aiInsight

  return (
    <div className="space-y-6">
      <div>
        <label className="text-sm font-medium mb-2 block">채용 공고 원문 (JD)</label>
        <Textarea
          placeholder="채용 공고 내용을 붙여넣기 하세요..."
          className="min-h-[160px] resize-none border-primary/10"
          value={jdText}
          onChange={(e) => setJdText(e.target.value)}
          readOnly
        />
        <p className="text-[10px] text-muted-foreground mt-1">※ 공고 수정은 새 공고 등록을 이용해주세요.</p>
      </div>

      {isAnalyzed ? (
        <div className="space-y-5 pt-4 border-t border-border">
          {aiData?.requirements && (
            <>
              <div>
                <h4 className="text-sm font-semibold mb-3 flex items-center gap-2">
                  <div className="size-2 rounded-full bg-primary" />
                  필수 기술 스택
                </h4>
                <div className="flex flex-wrap gap-2">
                  {(aiData.requirements.technicalSkills || []).map((skill: string) => (
                    <Badge key={skill} className="bg-primary/10 text-primary hover:bg-primary/20 border-0">
                      {skill}
                    </Badge>
                  ))}
                </div>
              </div>

              <div>
                <h4 className="text-sm font-semibold mb-3 flex items-center gap-2">
                  <div className="size-2 rounded-full bg-chart-2" />
                  핵심 요구 역량
                </h4>
                <div className="space-y-2">
                  {(aiData.requirements.coreCompetencies || []).map((comp: string, idx: number) => (
                    <div key={idx} className="flex items-start gap-2 text-sm">
                      <span className="size-5 shrink-0 rounded bg-chart-2/10 text-chart-2 flex items-center justify-center text-xs font-medium">
                        {idx + 1}
                      </span>
                      <span className="text-muted-foreground">{comp}</span>
                    </div>
                  ))}
                </div>
              </div>

              {aiData.requirements.preferredExperience && (
                <div>
                  <h4 className="text-sm font-semibold mb-3 flex items-center gap-2">
                    <div className="size-2 rounded-full bg-chart-3" />
                    우대 사항
                  </h4>
                  <div className="flex flex-wrap gap-2">
                    {(aiData.requirements.preferredExperience || []).map((qual: string) => (
                      <Badge key={qual} variant="outline" className="text-xs">
                        {qual}
                      </Badge>
                    ))}
                  </div>
                </div>
              )}
            </>
          )}
          
          {!aiData?.requirements && application.aiInsight && (
            <div className="bg-muted/30 p-4 rounded-xl text-sm leading-relaxed whitespace-pre-line">
              {application.aiInsight}
            </div>
          )}
        </div>
      ) : (
        <div className="py-10 text-center text-muted-foreground border-t border-dashed border-border mt-4">
          <Sparkles className="size-8 mx-auto mb-2 opacity-20" />
          <p className="text-sm">분석된 AI 데이터가 없습니다.</p>
        </div>
      )}
    </div>
  )
}
