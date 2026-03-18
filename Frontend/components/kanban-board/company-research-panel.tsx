"use client"

import { useEffect, useState, type ReactNode } from "react"
import {
  Building2,
  FileSearch,
  Lightbulb,
  Loader2,
  MessageSquareQuote,
  Sparkles,
  Target,
} from "lucide-react"
import { Button } from "@/components/ui/button"
import { Card, CardContent } from "@/components/ui/card"
import { Textarea } from "@/components/ui/textarea"
import { Badge } from "@/components/ui/badge"
import { Separator } from "@/components/ui/separator"
import { toApiUrl } from "@/lib/network/api-base"
import { streamSse } from "@/lib/network/stream-sse"
import {
  playCompletionSound,
  prepareCompletionSound,
} from "@/lib/audio/completion-sound"
import {
  parseCompanyResearch,
  type CompanyResearchData,
} from "@/lib/application-intelligence"
import { type Application } from "@/lib/mock-data"

interface CompanyResearchPanelProps {
  application: Application
  onUpdateApplication: (updates: Partial<Application>) => void
}

const DEFAULT_GOAL =
  "지원동기, 서비스 호감도, 직무 적합도를 자소서에 구체적으로 녹일 수 있을 정도로 분석합니다."

export function CompanyResearchPanel({
  application,
  onUpdateApplication,
}: CompanyResearchPanelProps) {
  const existingResearch = parseCompanyResearch(application.companyResearch)
  const [businessUnit, setBusinessUnit] = useState("")
  const [targetService, setTargetService] = useState("")
  const [focusRole, setFocusRole] = useState(application.position || "")
  const [techFocus, setTechFocus] = useState("")
  const [questionGoal, setQuestionGoal] = useState(DEFAULT_GOAL)
  const [researchData, setResearchData] = useState<CompanyResearchData | null>(existingResearch)
  const [progressMessage, setProgressMessage] = useState("")
  const [isResearching, setIsResearching] = useState(false)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    const parsed = parseCompanyResearch(application.companyResearch)
    setResearchData(parsed)
    setBusinessUnit(parsed?.focus.businessUnit || "")
    setTargetService(parsed?.focus.targetService || "")
    setFocusRole(parsed?.focus.focusRole || application.position || "")
    setTechFocus(parsed?.focus.techFocus || "")
    setQuestionGoal(parsed?.focus.questionGoal || DEFAULT_GOAL)
    setProgressMessage("")
    setError(null)
  }, [application.id, application.companyResearch, application.position])

  const handleResearch = async () => {
    await prepareCompletionSound()
    setIsResearching(true)
    setError(null)
    setProgressMessage("기업 분석 준비 중입니다.")

    let completed = false
    let streamFailed = false

    try {
      const initResponse = await fetch(
        `/api/applications/${application.id}/company-research/init`,
        {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({
            businessUnit,
            targetService,
            focusRole,
            techFocus,
            questionGoal,
          }),
        }
      )

      if (!initResponse.ok) {
        throw new Error("기업 분석 초기화에 실패했습니다.")
      }

      const { uuid } = await initResponse.json()

      await streamSse({
        url: toApiUrl(`/api/applications/company-research/stream/${uuid}`),
        onEvent: ({ event, data }) => {
          const normalizedEvent = event.toUpperCase()

          if (normalizedEvent === "START" || normalizedEvent === "ANALYZING") {
            setProgressMessage(data)
            return
          }

          if (normalizedEvent === "COMPLETE") {
            const parsed = parseCompanyResearch(data)
            if (!parsed) {
              streamFailed = true
              setError("기업 분석 결과를 해석하지 못했습니다.")
              return
            }

            const normalizedResearch = mergeResearchFocus(parsed, {
              company: application.company,
              position: application.position,
              businessUnit,
              targetService,
              focusRole,
              techFocus,
              questionGoal,
            })

            completed = true
            setResearchData(normalizedResearch)
            setProgressMessage("기업 분석이 완료되었습니다.")
            onUpdateApplication({ companyResearch: JSON.stringify(normalizedResearch) })
            void playCompletionSound()
            return
          }

          if (normalizedEvent === "ERROR") {
            streamFailed = true
            try {
              const parsedError = JSON.parse(data)
              setError(parsedError.message || "기업 분석 중 오류가 발생했습니다.")
            } catch {
              setError(data || "기업 분석 중 오류가 발생했습니다.")
            }
          }
        },
      })

      if (!completed && !streamFailed) {
        setError("기업 분석 스트림이 예상보다 일찍 종료되었습니다.")
      }
    } catch (err) {
      setError(
        err instanceof Error
          ? err.message
          : "기업 분석 요청에 실패했습니다."
      )
    } finally {
      setIsResearching(false)
    }
  }

  return (
    <div className="w-full min-w-0 max-w-full space-y-5 overflow-visible">
      <Card className="w-full min-w-0 max-w-full overflow-hidden border-primary/15 bg-gradient-to-br from-primary/5 via-background to-background shadow-none">
        <CardContent className="space-y-5 p-4 sm:p-5">
          <div className="flex min-w-0 flex-col gap-4">
            <div className="min-w-0 flex-1 space-y-2">
              <div className="flex items-center gap-2 text-primary">
                <FileSearch className="size-4 shrink-0" />
                <span className="text-sm font-black uppercase tracking-[0.18em]">
                  기업 분석
                </span>
              </div>
              <p className="break-words text-sm leading-6 text-muted-foreground">
                Gemini가 공고와 입력한 포커스를 바탕으로 회사와 직무 맥락을 자소서용으로 깊게 재구성합니다.
                필요하면 공개된 최신 정보를 바탕으로 다시 분석할 수 있습니다.
              </p>
            </div>
            <Button
              onClick={handleResearch}
              disabled={isResearching}
              className="w-full shrink-0 gap-2 self-start rounded-full px-4 shadow-sm shadow-primary/20 sm:w-auto"
            >
              {isResearching ? (
                <Loader2 className="size-4 animate-spin" />
              ) : (
                <Sparkles className="size-4" />
              )}
              {researchData ? "다시 분석" : "분석 시작"}
            </Button>
          </div>

          <div className="grid min-w-0 gap-3 lg:grid-cols-2">
            <FieldBlock
              label="사업부 / 팀"
              value={businessUnit}
              onChange={setBusinessUnit}
              placeholder="예: MX사업부, 병원 전산팀, AI 플랫폼 조직"
            />
            <FieldBlock
              label="서비스 / 제품"
              value={targetService}
              onChange={setTargetService}
              placeholder="예: 모바일 플랫폼, 병원 정보시스템, AI 지원 서비스"
            />
            <FieldBlock
              label="직무 포커스"
              value={focusRole}
              onChange={setFocusRole}
              placeholder="예: Java 백엔드, 의료 AI 운영, 플랫폼 자동화"
            />
            <FieldBlock
              label="기술 포커스"
              value={techFocus}
              onChange={setTechFocus}
              placeholder="예: Java, Spring, 보안, 인프라, 데이터 파이프라인, MLOps"
            />
          </div>

          <div className="min-w-0 space-y-1.5">
            <p className="text-[11px] font-black uppercase tracking-wider text-muted-foreground">
              분석 목표
            </p>
            <Textarea
              value={questionGoal}
              onChange={(event) => setQuestionGoal(event.target.value)}
              placeholder="이 분석에서 어떤 답을 얻고 싶은지 적어주세요."
              className="min-h-[110px] max-w-full resize-none overflow-hidden whitespace-pre-wrap break-all bg-background/80 text-sm leading-6 [overflow-wrap:anywhere]"
            />
          </div>

          {(progressMessage || error) && (
            <div
              className={`rounded-2xl border px-4 py-3 text-sm leading-6 ${
                error
                  ? "border-destructive/20 bg-destructive/5 text-destructive"
                  : "border-primary/10 bg-primary/5 text-foreground"
              }`}
            >
              {error || progressMessage}
            </div>
          )}
        </CardContent>
      </Card>

      {researchData && (
        <div className="w-full min-w-0 max-w-full space-y-4 overflow-visible">
          <Card className="w-full min-w-0 max-w-full overflow-hidden border-none bg-muted/30 shadow-none">
            <CardContent className="space-y-4 p-4 sm:p-5">
              <div className="flex min-w-0 items-center gap-2">
                <Building2 className="size-4 shrink-0 text-primary" />
                <span className="text-sm font-black uppercase tracking-[0.16em] text-primary">
                  핵심 요약
                </span>
              </div>
              <p className="whitespace-pre-wrap break-words text-sm leading-7 text-foreground/90">
                {researchData.executiveSummary}
              </p>
              <div className="flex flex-wrap gap-2">
                {Array.from(
                  new Set(
                    [
                      researchData.focus.businessUnit,
                      researchData.focus.targetService,
                      researchData.focus.focusRole,
                      researchData.focus.techFocus,
                    ].filter(Boolean),
                  ),
                ).map((item, index) => (
                    <Badge
                      key={`${item}-${index}`}
                      variant="outline"
                      className="max-w-full whitespace-normal break-all border-primary/20 bg-background px-3 py-1 text-primary"
                    >
                      {item}
                    </Badge>
                  ))}
              </div>
            </CardContent>
          </Card>

          <div className="grid min-w-0 gap-4">
            <InsightListCard
              icon={<Target className="size-4 text-primary" />}
              title="경영/사업 맥락"
              items={researchData.businessContext}
            />
            <InsightListCard
              icon={<Sparkles className="size-4 text-primary" />}
              title="서비스/제품 맥락"
              items={researchData.serviceLandscape}
            />
            <InsightListCard
              icon={<Building2 className="size-4 text-primary" />}
              title="직무 범위"
              items={researchData.roleScope}
            />
            <InsightListCard
              icon={<FileSearch className="size-4 text-primary" />}
              title="기술 시그널"
              items={researchData.techSignals}
            />
          </div>

          <Card className="w-full min-w-0 max-w-full overflow-hidden border-primary/10 bg-primary/[0.03] shadow-none">
            <CardContent className="space-y-5 p-4 sm:p-5">
              <div className="flex min-w-0 items-center gap-2">
                <MessageSquareQuote className="size-4 shrink-0 text-primary" />
                <span className="text-sm font-black uppercase tracking-[0.16em] text-primary">
                  자소서 연결 포인트
                </span>
              </div>

              <div className="grid gap-4">
                <MiniList title="지원동기 포인트" items={researchData.motivationHooks} />
                <MiniList title="서비스 관심 포인트" items={researchData.serviceHooks} />
                <MiniList title="자소서 연결 각도" items={researchData.resumeAngles} />
                <MiniList title="면접 예상 포인트" items={researchData.interviewSignals} />
              </div>

              {researchData.recommendedNarrative && (
                <>
                  <Separator className="bg-primary/10" />
                  <div className="space-y-2">
                    <p className="text-[11px] font-black uppercase tracking-wider text-muted-foreground">
                      추천 서술 방향
                    </p>
                    <p className="whitespace-pre-wrap break-words rounded-2xl bg-background p-4 text-sm leading-7 text-foreground/90 shadow-sm">
                      {researchData.recommendedNarrative}
                    </p>
                  </div>
                </>
              )}
            </CardContent>
          </Card>

          <div className="grid min-w-0 gap-4">
            <InsightListCard
              icon={<Lightbulb className="size-4 text-primary" />}
              title="후속 질문"
              items={researchData.followUpQuestions}
            />
            <InsightListCard
              icon={<FileSearch className="size-4 text-primary" />}
              title="신뢰도 메모"
              items={researchData.confidenceNotes}
            />
          </div>
        </div>
      )}
    </div>
  )
}

function mergeResearchFocus(
  research: CompanyResearchData,
  overrides: {
    company?: string
    position?: string
    businessUnit?: string
    targetService?: string
    focusRole?: string
    techFocus?: string
    questionGoal?: string
  },
): CompanyResearchData {
  return {
    ...research,
    focus: {
      ...research.focus,
      company: research.focus.company || overrides.company || "",
      position: research.focus.position || overrides.position || "",
      businessUnit: overrides.businessUnit?.trim() || research.focus.businessUnit || "",
      targetService: overrides.targetService?.trim() || research.focus.targetService || "",
      focusRole: overrides.focusRole?.trim() || research.focus.focusRole || overrides.position || "",
      techFocus: overrides.techFocus?.trim() || research.focus.techFocus || "",
      questionGoal: overrides.questionGoal?.trim() || research.focus.questionGoal || DEFAULT_GOAL,
    },
  }
}

function FieldBlock({
  label,
  value,
  onChange,
  placeholder,
}: {
  label: string
  value: string
  onChange: (value: string) => void
  placeholder: string
}) {
  return (
    <div className="min-w-0 space-y-1.5">
      <p className="break-words text-[11px] font-black uppercase tracking-wider text-muted-foreground">
        {label}
      </p>
      <Textarea
        value={value}
        onChange={(event) => onChange(event.target.value)}
        placeholder={placeholder}
        className="min-h-[84px] max-w-full resize-none overflow-hidden whitespace-pre-wrap break-all bg-background/80 text-sm leading-6 [overflow-wrap:anywhere]"
      />
    </div>
  )
}

function InsightListCard({
  title,
  items,
  icon,
}: {
  title: string
  items: string[]
  icon: ReactNode
}) {
  if (items.length === 0) return null

  return (
    <Card className="w-full min-w-0 max-w-full overflow-hidden border-none bg-muted/30 shadow-none">
      <CardContent className="space-y-3 p-4 sm:p-5">
        <div className="flex min-w-0 items-center gap-2">
          {icon}
          <span className="break-words text-sm font-black uppercase tracking-[0.16em] text-primary">
            {title}
          </span>
        </div>
        <MiniList items={items} />
      </CardContent>
    </Card>
  )
}

function MiniList({ title, items }: { title?: string; items: string[] }) {
  if (items.length === 0) return null

  return (
    <div className="min-w-0 space-y-2">
      {title && (
        <p className="break-words text-[11px] font-black uppercase tracking-wider text-muted-foreground">
          {title}
        </p>
      )}
      <div className="min-w-0 space-y-2">
        {items.map((item, index) => (
          <div
            key={`${title || "item"}-${index}`}
            className="w-full whitespace-pre-wrap break-words rounded-2xl bg-background px-3 py-3 text-sm leading-6 text-foreground/90 shadow-sm"
          >
            {item}
          </div>
        ))}
      </div>
    </div>
  )
}
