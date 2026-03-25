"use client"

import { useEffect, useState, type ReactNode } from "react"
import {
  AlertCircle,
  BookOpen,
  Building2,
  ChevronDown,
  ChevronUp,
  ExternalLink,
  FileSearch,
  Lightbulb,
  MessageSquareQuote,
  Loader2,
  Search,
  Sparkles,
  Target,
  Zap,
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
} from "../../lib/audio/completion-sound"
import {
  parseCompanyResearch,
  type CompanyResearchData,
  type TechConfidence,
  type TechStackItem,
  type SearchSource,
} from "@/lib/application-intelligence"
import { type Application } from "@/lib/mock-data"

interface CompanyResearchPanelProps {
  application: Application
  onUpdateApplication: (updates: Partial<Application>) => void
}

// 기술 스택 confidence 별 스타일
const CONFIDENCE_STYLE: Record<TechConfidence, { badge: string; label: string }> = {
  CONFIRMED: {
    badge: "border-emerald-200 bg-emerald-50 text-emerald-700",
    label: "확인됨",
  },
  INFERRED: {
    badge: "border-amber-200 bg-amber-50 text-amber-700",
    label: "추정",
  },
  UNCERTAIN: {
    badge: "border-gray-200 bg-gray-50 text-gray-500",
    label: "불확실",
  },
}

// 카테고리 표시 순서
const CATEGORY_ORDER = [
  "Backend", "Frontend", "Mobile", "Database",
  "Infrastructure", "DevOps", "AI-ML",
]

export function CompanyResearchPanel({
  application,
  onUpdateApplication,
}: CompanyResearchPanelProps) {
  const existingResearch = parseCompanyResearch(application.companyResearch)
  const [additionalFocus, setAdditionalFocus] = useState("")
  const [showFocusInput, setShowFocusInput] = useState(false)
  const [researchData, setResearchData] = useState<CompanyResearchData | null>(existingResearch)
  const [progressMessage, setProgressMessage] = useState("")
  const [isResearching, setIsResearching] = useState(false)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    const parsed = parseCompanyResearch(application.companyResearch)
    setResearchData(parsed)
    setProgressMessage("")
    setError(null)
  }, [application.id, application.companyResearch])

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
          body: JSON.stringify({ additionalFocus: additionalFocus.trim() || null }),
        }
      )

      if (!initResponse.ok) throw new Error("기업 분석 초기화에 실패했습니다.")

      const { uuid } = await initResponse.json() as { uuid: string }

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
            completed = true
            setResearchData(parsed)
            setProgressMessage("기업 분석이 완료되었습니다.")
            onUpdateApplication({ companyResearch: JSON.stringify(parsed) })
            void playCompletionSound()
            return
          }

          if (normalizedEvent === "ERROR") {
            streamFailed = true
            try {
              const parsedError = JSON.parse(data) as { message?: string }
              setError(parsedError.message ?? "기업 분석 중 오류가 발생했습니다.")
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
      setError(err instanceof Error ? err.message : "기업 분석 요청에 실패했습니다.")
    } finally {
      setIsResearching(false)
    }
  }

  return (
    <div className="w-full min-w-0 max-w-full space-y-5 overflow-visible">
      {/* ── 분석 시작 카드 ── */}
      <Card className="w-full min-w-0 max-w-full overflow-hidden border-primary/15 bg-gradient-to-br from-primary/5 via-background to-background shadow-none">
        <CardContent className="space-y-4 p-4 sm:p-5">
          <div className="flex min-w-0 flex-col gap-3">
            <div className="flex items-center gap-2 text-primary">
              <FileSearch className="size-4 shrink-0" />
              <span className="text-sm font-black uppercase tracking-[0.18em]">기업 분석</span>
            </div>
            <p className="break-words text-sm leading-6 text-muted-foreground">
              JD를 바탕으로 AI가 사업부·제품을 자동 추론하고, 경력 공고·기술 블로그를 검색하여
              실제 기술 스택과 자소서 전략을 도출합니다.
            </p>

            <div className="flex flex-wrap items-center gap-2">
              <Button
                onClick={handleResearch}
                disabled={isResearching}
                className="gap-2 rounded-full px-5 shadow-sm shadow-primary/20"
              >
                {isResearching ? (
                  <Loader2 className="size-4 animate-spin" />
                ) : (
                  <Sparkles className="size-4" />
                )}
                {researchData ? "다시 분석" : "분석 시작"}
              </Button>

              <Button
                variant="ghost"
                size="sm"
                className="gap-1.5 text-muted-foreground"
                onClick={() => setShowFocusInput((prev) => !prev)}
              >
                {showFocusInput ? (
                  <ChevronUp className="size-3.5" />
                ) : (
                  <ChevronDown className="size-3.5" />
                )}
                {showFocusInput ? "포커스 닫기" : "추가 포커스 설정"}
              </Button>
            </div>

            {showFocusInput && (
              <div className="space-y-1.5 animate-in fade-in slide-in-from-top-1 duration-200">
                <p className="text-[11px] font-black uppercase tracking-wider text-muted-foreground">
                  분석 지시사항 <span className="font-normal normal-case">(선택 · 포함/제외 모두 가능)</span>
                </p>
                <Textarea
                  value={additionalFocus}
                  onChange={(e) => setAdditionalFocus(e.target.value)}
                  placeholder={"예: 클라우드 인프라 쪽이 특히 궁금해요\n예: 모바일 관련은 찾지 말아줘\n예: AI 실제 프로덕트 위주로만 분석해줘"}
                  className="min-h-[90px] resize-none bg-background/80 text-sm leading-6"
                />
              </div>
            )}

            {(progressMessage || error) && (
              <div
                className={`flex items-start gap-2 rounded-2xl border px-4 py-3 text-sm leading-6 ${
                  error
                    ? "border-destructive/20 bg-destructive/5 text-destructive"
                    : "border-primary/10 bg-primary/5 text-foreground"
                }`}
              >
                {error && <AlertCircle className="mt-0.5 size-4 shrink-0" />}
                <span>{error || progressMessage}</span>
              </div>
            )}
          </div>
        </CardContent>
      </Card>

      {researchData && (
        <div className="w-full min-w-0 max-w-full space-y-4 overflow-visible">

          {/* ── 자동 발견 맥락 + 핵심 요약 ── */}
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

              {/* AI 자동 발견 맥락 배지 */}
              {researchData.discoveredContext && (
                <div className="space-y-2">
                  <p className="text-[11px] font-black uppercase tracking-wider text-muted-foreground">
                    AI 자동 추론
                  </p>
                  <div className="flex flex-wrap gap-2">
                    {researchData.discoveredContext.businessUnit && (
                      <Badge variant="outline" className="border-primary/20 bg-background px-3 py-1 text-primary">
                        🏢 {researchData.discoveredContext.businessUnit}
                      </Badge>
                    )}
                    {researchData.discoveredContext.product && (
                      <Badge variant="outline" className="border-primary/20 bg-background px-3 py-1 text-primary">
                        📦 {researchData.discoveredContext.product}
                      </Badge>
                    )}
                  </div>
                  {(researchData.discoveredContext.evidenceSources?.length ?? 0) > 0 && (
                    <p className="text-xs text-muted-foreground">
                      출처: {researchData.discoveredContext!.evidenceSources!.join(" · ")}
                    </p>
                  )}
                </div>
              )}
            </CardContent>
          </Card>

          {/* ── 실제 검색 출처 ── */}
          {((researchData.searchSources?.length ?? 0) > 0 ||
            (researchData.searchQueries?.length ?? 0) > 0) && (
            <SourcesCard
              queries={researchData.searchQueries ?? []}
              sources={researchData.searchSources ?? []}
            />
          )}

          {/* ── 기술 스택 (신규) ── */}
          {(researchData.techStack?.length ?? 0) > 0 && (
            <TechStackCard techStack={researchData.techStack!} />
          )}

          {/* ── 최근 기술 작업 (신규) ── */}
          {(researchData.recentTechWork?.length ?? 0) > 0 && (
            <Card className="w-full min-w-0 max-w-full overflow-hidden border-none bg-muted/30 shadow-none">
              <CardContent className="space-y-3 p-4 sm:p-5">
                <div className="flex min-w-0 items-center gap-2">
                  <Zap className="size-4 shrink-0 text-primary" />
                  <span className="text-sm font-black uppercase tracking-[0.16em] text-primary">
                    최근 기술 작업
                  </span>
                </div>
                <div className="space-y-3">
                  {researchData.recentTechWork!.map((fact, index) => (
                    <div
                      key={index}
                      className="rounded-2xl bg-background px-4 py-3 shadow-sm space-y-1"
                    >
                      <p className="text-sm font-semibold text-foreground/90">{fact.summary}</p>
                      {fact.detail && (
                        <p className="text-sm leading-6 text-muted-foreground">{fact.detail}</p>
                      )}
                      {fact.source && (
                        <p className="text-xs text-muted-foreground/70">📎 {fact.source}</p>
                      )}
                    </div>
                  ))}
                </div>
              </CardContent>
            </Card>
          )}

          {/* ── Fit 분석 (신규) ── */}
          {researchData.fitAnalysis && (
            <FitAnalysisCard fitAnalysis={researchData.fitAnalysis} />
          )}

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
          </div>

          {/* ── 자소서 연결 포인트 ── */}
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

// ── 실제 검색 출처 카드 ────────────────────────────────────────────

function SourcesCard({
  queries,
  sources,
}: {
  queries: string[]
  sources: SearchSource[]
}) {
  const [expanded, setExpanded] = useState(false)

  return (
    <Card className="w-full min-w-0 max-w-full overflow-hidden border-none bg-muted/20 shadow-none">
      <CardContent className="space-y-3 p-4 sm:p-5">
        <button
          onClick={() => setExpanded((prev) => !prev)}
          className="flex w-full min-w-0 items-center justify-between gap-2 text-left"
        >
          <div className="flex items-center gap-2">
            <Search className="size-3.5 shrink-0 text-muted-foreground" />
            <span className="text-xs font-bold uppercase tracking-wider text-muted-foreground">
              Gemini가 실제로 참고한 출처
            </span>
            <span className="rounded-full bg-muted px-2 py-0.5 text-[10px] font-bold text-muted-foreground">
              {sources.length}
            </span>
          </div>
          {expanded ? (
            <ChevronUp className="size-3.5 shrink-0 text-muted-foreground" />
          ) : (
            <ChevronDown className="size-3.5 shrink-0 text-muted-foreground" />
          )}
        </button>

        {expanded && (
          <div className="space-y-3 animate-in fade-in slide-in-from-top-1 duration-200">
            {/* 실제 검색 쿼리 */}
            {queries.length > 0 && (
              <div className="space-y-1.5">
                <p className="text-[10px] font-bold uppercase tracking-wider text-muted-foreground">
                  검색어
                </p>
                <div className="flex flex-wrap gap-1.5">
                  {queries.map((q, i) => (
                    <span
                      key={i}
                      className="rounded-full border border-border bg-background px-2.5 py-0.5 text-[11px] text-muted-foreground"
                    >
                      {q}
                    </span>
                  ))}
                </div>
              </div>
            )}

            {/* 실제 참고 URL */}
            {sources.length > 0 && (
              <div className="space-y-1.5">
                <p className="text-[10px] font-bold uppercase tracking-wider text-muted-foreground">
                  참고 페이지
                </p>
                <div className="space-y-1">
                  {sources.map((src, i) => (
                    <a
                      key={i}
                      href={src.uri}
                      target="_blank"
                      rel="noopener noreferrer"
                      className="flex items-start gap-2 rounded-xl px-3 py-2 text-xs leading-5 text-muted-foreground transition-colors hover:bg-muted hover:text-foreground"
                    >
                      <ExternalLink className="mt-0.5 size-3 shrink-0" />
                      <span className="min-w-0 break-all">{src.title || src.uri}</span>
                    </a>
                  ))}
                </div>
              </div>
            )}
          </div>
        )}
      </CardContent>
    </Card>
  )
}

// ── 기술 스택 카드 ──────────────────────────────────────────────────

function TechStackCard({ techStack }: { techStack: TechStackItem[] }) {
  // 카테고리별 그룹핑
  const grouped = CATEGORY_ORDER.reduce<Record<string, TechStackItem[]>>((acc, cat) => {
    const items = techStack.filter((t) => t.category === cat)
    if (items.length > 0) acc[cat] = items
    return acc
  }, {})

  // CATEGORY_ORDER에 없는 기타 카테고리
  const others = techStack.filter((t) => !CATEGORY_ORDER.includes(t.category))
  if (others.length > 0) grouped["기타"] = others

  const CATEGORY_LABEL: Record<string, string> = {
    Backend: "백엔드", Frontend: "프론트엔드", Mobile: "모바일",
    Database: "데이터베이스", Infrastructure: "인프라", DevOps: "DevOps", "AI-ML": "AI / ML",
  }

  return (
    <Card className="w-full min-w-0 max-w-full overflow-hidden border-none bg-muted/30 shadow-none">
      <CardContent className="space-y-4 p-4 sm:p-5">
        <div className="flex min-w-0 items-center justify-between gap-2">
          <div className="flex items-center gap-2">
            <BookOpen className="size-4 shrink-0 text-primary" />
            <span className="text-sm font-black uppercase tracking-[0.16em] text-primary">
              기술 스택
            </span>
          </div>
          <div className="flex items-center gap-3 text-[10px] text-muted-foreground">
            <span className="flex items-center gap-1">
              <span className="inline-block size-2 rounded-full bg-emerald-400" />확인됨
            </span>
            <span className="flex items-center gap-1">
              <span className="inline-block size-2 rounded-full bg-amber-400" />추정
            </span>
            <span className="flex items-center gap-1">
              <span className="inline-block size-2 rounded-full bg-gray-300" />불확실
            </span>
          </div>
        </div>

        <div className="space-y-3">
          {Object.entries(grouped).map(([category, items]) => (
            <div key={category} className="space-y-1.5">
              <p className="text-[11px] font-bold uppercase tracking-wider text-muted-foreground">
                {CATEGORY_LABEL[category] ?? category}
              </p>
              <div className="flex flex-wrap gap-1.5">
                {items.map((tech, i) => {
                  const style = CONFIDENCE_STYLE[tech.confidence] ?? CONFIDENCE_STYLE.UNCERTAIN
                  return (
                    <span
                      key={i}
                      title={`${style.label} · ${tech.source}`}
                      className={`inline-flex cursor-default items-center rounded-full border px-2.5 py-0.5 text-xs font-medium transition-opacity hover:opacity-80 ${style.badge}`}
                    >
                      {tech.name}
                    </span>
                  )
                })}
              </div>
            </div>
          ))}
        </div>
      </CardContent>
    </Card>
  )
}

// ── Fit 분석 카드 ───────────────────────────────────────────────────

function FitAnalysisCard({ fitAnalysis }: { fitAnalysis: NonNullable<CompanyResearchData["fitAnalysis"]> }) {
  return (
    <Card className="w-full min-w-0 max-w-full overflow-hidden border-amber-100 bg-amber-50/40 shadow-none">
      <CardContent className="space-y-4 p-4 sm:p-5">
        <div className="flex min-w-0 items-center gap-2">
          <Target className="size-4 shrink-0 text-amber-600" />
          <span className="text-sm font-black uppercase tracking-[0.16em] text-amber-700">
            Fit 분석
          </span>
        </div>

        {fitAnalysis.gapAnalysis && (
          <p className="whitespace-pre-wrap break-words rounded-2xl bg-background px-4 py-3 text-sm leading-7 text-foreground/90 shadow-sm">
            {fitAnalysis.gapAnalysis}
          </p>
        )}

        <div className="grid gap-3 sm:grid-cols-2">
          {fitAnalysis.jdStatedRequirements.length > 0 && (
            <div className="space-y-1.5">
              <p className="text-[11px] font-black uppercase tracking-wider text-muted-foreground">
                JD 명시 요구사항
              </p>
              <div className="space-y-1.5">
                {fitAnalysis.jdStatedRequirements.map((item, i) => (
                  <div key={i} className="rounded-xl bg-background px-3 py-2 text-xs leading-5 text-foreground/80 shadow-sm">
                    {item}
                  </div>
                ))}
              </div>
            </div>
          )}

          {fitAnalysis.actualTechStack.length > 0 && (
            <div className="space-y-1.5">
              <p className="text-[11px] font-black uppercase tracking-wider text-muted-foreground">
                실제 파악된 기술
              </p>
              <div className="space-y-1.5">
                {fitAnalysis.actualTechStack.map((item, i) => (
                  <div key={i} className="rounded-xl bg-background px-3 py-2 text-xs leading-5 text-foreground/80 shadow-sm">
                    {item}
                  </div>
                ))}
              </div>
            </div>
          )}
        </div>

        {fitAnalysis.coverLetterHints.length > 0 && (
          <div className="space-y-1.5">
            <p className="text-[11px] font-black uppercase tracking-wider text-muted-foreground">
              자소서 강조 포인트
            </p>
            <div className="space-y-1.5">
              {fitAnalysis.coverLetterHints.map((hint, i) => (
                <div key={i} className="flex items-start gap-2 rounded-xl bg-background px-3 py-2.5 text-sm leading-6 shadow-sm">
                  <span className="mt-0.5 text-amber-500">✦</span>
                  <span className="text-foreground/90">{hint}</span>
                </div>
              ))}
            </div>
          </div>
        )}
      </CardContent>
    </Card>
  )
}

// ── 공통 서브 컴포넌트 ──────────────────────────────────────────────

function InsightListCard({ title, items, icon }: { title: string; items: string[]; icon: ReactNode }) {
  if (items.length === 0) return null
  return (
    <Card className="w-full min-w-0 max-w-full overflow-hidden border-none bg-muted/30 shadow-none">
      <CardContent className="space-y-3 p-4 sm:p-5">
        <div className="flex min-w-0 items-center gap-2">
          {icon}
          <span className="break-words text-sm font-black uppercase tracking-[0.16em] text-primary">{title}</span>
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
        <p className="break-words text-[11px] font-black uppercase tracking-wider text-muted-foreground">{title}</p>
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
