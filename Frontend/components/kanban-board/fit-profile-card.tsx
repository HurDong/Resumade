"use client"

import { useCallback, useEffect, useState } from "react"
import {
  AlertCircle,
  CheckCircle2,
  ExternalLink,
  FileSearch,
  Loader2,
  RefreshCw,
  Sparkles,
  Trash2,
} from "lucide-react"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Card, CardContent } from "@/components/ui/card"
import { Separator } from "@/components/ui/separator"
import { Textarea } from "@/components/ui/textarea"
import { toApiUrl } from "@/lib/network/api-base"
import { streamSse } from "@/lib/network/stream-sse"
import type {
  CompanyFitProfileCandidate,
  CompanyFitProfileData,
  CompanyFitProfileRecord,
  FitProfileConfidence,
  FitProfileItem,
  FitProfileLexiconItem,
} from "@/lib/application-intelligence"
import type { Application } from "@/lib/mock-data"

const CONFIDENCE_LABEL: Record<FitProfileConfidence, string> = {
  CONFIRMED: "확인됨",
  INFERRED: "추론",
  UNCERTAIN: "불확실",
}

const CONFIDENCE_CLASS: Record<FitProfileConfidence, string> = {
  CONFIRMED: "border-emerald-200 bg-emerald-50 text-emerald-700",
  INFERRED: "border-amber-200 bg-amber-50 text-amber-700",
  UNCERTAIN: "border-gray-200 bg-gray-50 text-gray-600",
}

interface FitProfileCardProps {
  application: Application
}

export function FitProfileCard({ application }: FitProfileCardProps) {
  const [activeProfile, setActiveProfile] = useState<CompanyFitProfileRecord | null>(null)
  const [candidate, setCandidate] = useState<CompanyFitProfileCandidate | null>(null)
  const [additionalFocus, setAdditionalFocus] = useState("")
  const [reviewNote, setReviewNote] = useState("")
  const [progressMessage, setProgressMessage] = useState("")
  const [error, setError] = useState<string | null>(null)
  const [isLoadingActive, setIsLoadingActive] = useState(false)
  const [isGenerating, setIsGenerating] = useState(false)
  const [isActivating, setIsActivating] = useState(false)
  const [isDiscarding, setIsDiscarding] = useState(false)
  const [isSavingNote, setIsSavingNote] = useState(false)

  const applicationId = String(application.id)
  const canUseApi = /^\d+$/.test(applicationId)

  const fetchActiveProfile = useCallback(async () => {
    if (!canUseApi) {
      setActiveProfile(null)
      return
    }

    setIsLoadingActive(true)
    try {
      const response = await fetch(toApiUrl(`/api/applications/${applicationId}/fit-profile`), {
        cache: "no-store",
      })
      if (response.status === 204) {
        setActiveProfile(null)
        setReviewNote("")
        return
      }
      if (!response.ok) {
        throw new Error("기업 Fit 프로필을 불러오지 못했습니다.")
      }
      const data = (await response.json()) as CompanyFitProfileRecord
      setActiveProfile(data)
      setReviewNote(data.reviewNote ?? "")
    } catch (err) {
      setError(err instanceof Error ? err.message : "기업 Fit 프로필 조회에 실패했습니다.")
    } finally {
      setIsLoadingActive(false)
    }
  }, [applicationId, canUseApi])

  useEffect(() => {
    setCandidate(null)
    setProgressMessage("")
    setError(null)
    void fetchActiveProfile()
  }, [fetchActiveProfile])

  const handleGenerate = async () => {
    if (!canUseApi) return

    setIsGenerating(true)
    setCandidate(null)
    setError(null)
    setProgressMessage("기업 Fit 프로필 생성을 준비하고 있습니다.")

    try {
      const initResponse = await fetch(toApiUrl(`/api/applications/${applicationId}/fit-profile/init`), {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ additionalFocus: additionalFocus.trim() || null }),
      })
      if (!initResponse.ok) {
        throw new Error("기업 Fit 프로필 초기화에 실패했습니다.")
      }

      const { uuid } = (await initResponse.json()) as { uuid: string }
      let completed = false

      await streamSse({
        url: toApiUrl(`/api/applications/fit-profile/stream/${uuid}`),
        onEvent: ({ event, data }) => {
          const normalizedEvent = event.toUpperCase()
          if (normalizedEvent === "START" || normalizedEvent === "SEARCHING" || normalizedEvent === "STRUCTURING") {
            setProgressMessage(data)
            return
          }
          if (normalizedEvent === "COMPLETE") {
            const parsed = JSON.parse(data) as CompanyFitProfileCandidate
            completed = true
            setCandidate(parsed)
            setProgressMessage("프로필 후보가 생성되었습니다. 확인 후 등록하거나 폐기할 수 있습니다.")
            return
          }
          if (normalizedEvent === "ERROR") {
            const parsedError = safeParseError(data)
            throw new Error(parsedError)
          }
        },
      })

      if (!completed) {
        throw new Error("기업 Fit 프로필 스트림이 완료되지 않았습니다.")
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : "기업 Fit 프로필 생성에 실패했습니다.")
    } finally {
      setIsGenerating(false)
    }
  }

  const handleActivate = async () => {
    if (!candidate || !canUseApi) return

    setIsActivating(true)
    setError(null)
    try {
      const response = await fetch(toApiUrl(`/api/applications/${applicationId}/fit-profile/activate`), {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ uuid: candidate.uuid }),
      })
      if (!response.ok) {
        const body = await response.text().catch(() => "")
        throw new Error(body || "프로필 후보 등록에 실패했습니다.")
      }

      const data = (await response.json()) as CompanyFitProfileRecord
      setActiveProfile(data)
      setReviewNote(data.reviewNote ?? "")
      setCandidate(null)
      setProgressMessage("기업 Fit 프로필이 등록되었습니다.")
    } catch (err) {
      setError(err instanceof Error ? err.message : "프로필 후보 등록에 실패했습니다.")
    } finally {
      setIsActivating(false)
    }
  }

  const handleDiscard = async () => {
    if (!candidate || !canUseApi) return

    setIsDiscarding(true)
    setError(null)
    try {
      await fetch(toApiUrl(`/api/applications/${applicationId}/fit-profile/candidate/${candidate.uuid}`), {
        method: "DELETE",
      })
      setCandidate(null)
      setProgressMessage(activeProfile ? "후보를 폐기했습니다. 기존 등록 프로필은 유지됩니다." : "후보를 폐기했습니다.")
    } catch (err) {
      setError(err instanceof Error ? err.message : "프로필 후보 폐기에 실패했습니다.")
    } finally {
      setIsDiscarding(false)
    }
  }

  const handleSaveReviewNote = async () => {
    if (!activeProfile || !canUseApi) return

    setIsSavingNote(true)
    setError(null)
    try {
      const response = await fetch(toApiUrl(`/api/applications/${applicationId}/fit-profile/review-note`), {
        method: "PATCH",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ reviewNote }),
      })
      if (!response.ok) {
        throw new Error("검수 메모 저장에 실패했습니다.")
      }
      const data = (await response.json()) as CompanyFitProfileRecord
      setActiveProfile(data)
      setReviewNote(data.reviewNote ?? "")
      setProgressMessage("검수 메모를 저장했습니다.")
    } catch (err) {
      setError(err instanceof Error ? err.message : "검수 메모 저장에 실패했습니다.")
    } finally {
      setIsSavingNote(false)
    }
  }

  const visibleProfile = candidate?.profile ?? activeProfile?.profile ?? null
  const isCandidateMode = Boolean(candidate)

  return (
    <Card className="w-full min-w-0 max-w-full overflow-hidden border-primary/15 bg-primary/[0.03] shadow-none">
      <CardContent className="space-y-4 p-4 sm:p-5">
        <div className="flex min-w-0 flex-col gap-3">
          <div className="flex items-center gap-2 text-primary">
            <Sparkles className="size-4 shrink-0" />
            <span className="text-sm font-black uppercase tracking-[0.16em]">기업 Fit 프로필</span>
            {activeProfile && !candidate ? (
              <Badge variant="outline" className="ml-auto border-emerald-200 bg-emerald-50 text-emerald-700">
                등록됨
              </Badge>
            ) : null}
          </div>

          <p className="text-sm leading-6 text-muted-foreground">
            Gemini 검색으로 공고와 공개 근거를 확인해 지원 전략 가설을 만듭니다. 결과는 등록 전까지 초안 생성에 사용되지 않습니다.
          </p>

          <div className="space-y-2">
            <Textarea
              value={additionalFocus}
              onChange={(event) => setAdditionalFocus(event.target.value)}
              placeholder="추가로 보고 싶은 관점이 있다면 적어주세요. 예: 결제/정산 도메인 중심, 기술블로그 근거 위주"
              className="min-h-[74px] resize-none bg-background text-sm leading-6"
              disabled={isGenerating}
            />
            <div className="flex flex-wrap gap-2">
              <Button onClick={handleGenerate} disabled={!canUseApi || isGenerating || isActivating} className="gap-2 rounded-full px-4">
                {isGenerating ? <Loader2 className="size-4 animate-spin" /> : <FileSearch className="size-4" />}
                {candidate ? "다시 생성" : activeProfile ? "새 후보 생성" : "기업 Fit 프로필 생성"}
              </Button>
              {!activeProfile && !candidate ? (
                <span className="self-center text-xs text-muted-foreground">
                  프로필 없이도 JD/RAG 기반 작성은 계속 사용할 수 있습니다.
                </span>
              ) : null}
            </div>
          </div>

          {(progressMessage || error || isLoadingActive) ? (
            <div
              className={`flex items-start gap-2 rounded-2xl border px-4 py-3 text-sm leading-6 ${
                error
                  ? "border-destructive/20 bg-destructive/5 text-destructive"
                  : "border-primary/10 bg-background text-foreground"
              }`}
            >
              {error ? <AlertCircle className="mt-0.5 size-4 shrink-0" /> : null}
              {isLoadingActive ? <Loader2 className="mt-0.5 size-4 shrink-0 animate-spin" /> : null}
              <span>{error || (isLoadingActive ? "등록된 Fit 프로필을 확인하고 있습니다." : progressMessage)}</span>
            </div>
          ) : null}
        </div>

        {visibleProfile ? (
          <ProfilePreview
            profile={visibleProfile}
            status={candidate?.groundingStatus ?? activeProfile?.groundingStatus}
            modelName={candidate?.modelName ?? activeProfile?.modelName}
            isCandidateMode={isCandidateMode}
          />
        ) : null}

        {candidate ? (
          <div className="flex flex-wrap justify-end gap-2 border-t border-border/60 pt-4">
            <Button variant="ghost" onClick={handleDiscard} disabled={isDiscarding || isActivating} className="gap-2">
              {isDiscarding ? <Loader2 className="size-4 animate-spin" /> : <Trash2 className="size-4" />}
              폐기
            </Button>
            <Button onClick={handleActivate} disabled={isActivating || isDiscarding} className="gap-2">
              {isActivating ? <Loader2 className="size-4 animate-spin" /> : <CheckCircle2 className="size-4" />}
              등록
            </Button>
          </div>
        ) : null}

        {activeProfile && !candidate ? (
          <div className="space-y-2 border-t border-border/60 pt-4">
            <div className="flex items-center justify-between gap-3">
              <p className="text-[11px] font-black uppercase tracking-wider text-muted-foreground">검수 메모</p>
              <Button size="sm" variant="outline" onClick={handleSaveReviewNote} disabled={isSavingNote} className="h-8 gap-1.5">
                {isSavingNote ? <Loader2 className="size-3.5 animate-spin" /> : <RefreshCw className="size-3.5" />}
                저장
              </Button>
            </div>
            <Textarea
              value={reviewNote}
              onChange={(event) => setReviewNote(event.target.value)}
              placeholder="AI 가설 중 틀린 부분이나 강조하고 싶은 검수 내용을 남겨두세요."
              className="min-h-[82px] resize-none bg-background text-sm leading-6"
            />
          </div>
        ) : null}
      </CardContent>
    </Card>
  )
}

function ProfilePreview({
  profile,
  status,
  modelName,
  isCandidateMode,
}: {
  profile: CompanyFitProfileData
  status?: string | null
  modelName?: string | null
  isCandidateMode: boolean
}) {
  return (
    <div className="space-y-4 rounded-2xl bg-background/80 p-4 shadow-sm">
      <div className="space-y-2">
        <div className="flex flex-wrap items-center gap-2">
          <Badge variant="outline" className={status === "GROUNDED" ? "border-emerald-200 bg-emerald-50 text-emerald-700" : "border-amber-200 bg-amber-50 text-amber-700"}>
            {status === "GROUNDED" ? "검색 근거 포함" : "JD 중심"}
          </Badge>
          {modelName ? (
            <Badge variant="secondary" className="bg-muted text-muted-foreground">
              {modelName}
            </Badge>
          ) : null}
          {isCandidateMode ? (
            <Badge variant="outline" className="border-primary/20 bg-primary/5 text-primary">
              등록 전 후보
            </Badge>
          ) : null}
        </div>
        <p className="whitespace-pre-wrap text-sm font-semibold leading-7 text-foreground/90">
          {profile.summary}
        </p>
        {(profile.focus?.inferredBusinessUnit || profile.focus?.inferredProduct) ? (
          <div className="flex flex-wrap gap-2">
            {profile.focus.inferredBusinessUnit ? (
              <Badge variant="outline" className="rounded-full bg-muted/40 px-3 py-1">
                {profile.focus.inferredBusinessUnit}
              </Badge>
            ) : null}
            {profile.focus.inferredProduct ? (
              <Badge variant="outline" className="rounded-full bg-muted/40 px-3 py-1">
                {profile.focus.inferredProduct}
              </Badge>
            ) : null}
          </div>
        ) : null}
      </div>

      <Separator />

      <div className="grid gap-4 md:grid-cols-2">
        <ProfileSection title="사업/기술 방향" items={profile.businessAgenda ?? []} />
        <ProfileSection title="직무 미션" items={profile.roleMission ?? []} />
        <ProfileSection title="채용 신호" items={profile.hiringSignals ?? []} />
        <ProfileSection title="일하는 방식" items={profile.workStyle ?? []} />
      </div>

      {(profile.domainLexicon?.length ?? 0) > 0 ? (
        <div className="space-y-2">
          <p className="text-[11px] font-black uppercase tracking-wider text-muted-foreground">도메인 표현</p>
          <div className="flex flex-wrap gap-2">
            {profile.domainLexicon.map((item, index) => (
              <LexiconBadge key={`${item.term}-${index}`} item={item} />
            ))}
          </div>
        </div>
      ) : null}

      <ProfileSection title="전략 경고" items={profile.strategyWarnings ?? []} warning />

      {(profile.evidence?.length ?? 0) > 0 || (profile.searchSources?.length ?? 0) > 0 ? (
        <EvidenceList profile={profile} />
      ) : null}

      {(profile.confidenceNotes?.length ?? 0) > 0 ? (
        <div className="space-y-2">
          <p className="text-[11px] font-black uppercase tracking-wider text-muted-foreground">신뢰도 메모</p>
          <ul className="space-y-1.5 text-xs leading-5 text-muted-foreground">
            {profile.confidenceNotes.map((note, index) => (
              <li key={`${note}-${index}`} className="rounded-xl bg-muted/40 px-3 py-2">
                {note}
              </li>
            ))}
          </ul>
        </div>
      ) : null}
    </div>
  )
}

function ProfileSection({ title, items, warning = false }: { title: string; items: FitProfileItem[]; warning?: boolean }) {
  if (!items.length) return null

  return (
    <div className="space-y-2">
      <p className={`text-[11px] font-black uppercase tracking-wider ${warning ? "text-amber-700" : "text-muted-foreground"}`}>
        {title}
      </p>
      <div className="space-y-2">
        {items.map((item, index) => (
          <div key={`${item.title}-${index}`} className={`rounded-2xl px-3 py-2 ${warning ? "bg-amber-50 text-amber-950" : "bg-muted/35"}`}>
            <div className="flex flex-wrap items-center gap-2">
              <span className="text-sm font-semibold">{item.title}</span>
              <ConfidenceBadge confidence={item.confidence} />
            </div>
            {item.detail ? (
              <p className="mt-1 text-sm leading-6 text-muted-foreground">{item.detail}</p>
            ) : null}
          </div>
        ))}
      </div>
    </div>
  )
}

function LexiconBadge({ item }: { item: FitProfileLexiconItem }) {
  return (
    <span className="inline-flex max-w-full items-center gap-1.5 rounded-full border border-border bg-muted/30 px-3 py-1 text-xs">
      <span className="font-semibold text-foreground">{item.term}</span>
      <span className="min-w-0 truncate text-muted-foreground">{item.usage}</span>
      <ConfidenceBadge confidence={item.confidence} compact />
    </span>
  )
}

function ConfidenceBadge({ confidence, compact = false }: { confidence?: FitProfileConfidence | string; compact?: boolean }) {
  const normalized = normalizeConfidence(confidence)
  return (
    <Badge variant="outline" className={`${CONFIDENCE_CLASS[normalized]} ${compact ? "px-1.5 py-0 text-[10px]" : "px-2 py-0.5 text-[10px]"}`}>
      {CONFIDENCE_LABEL[normalized]}
    </Badge>
  )
}

function EvidenceList({ profile }: { profile: CompanyFitProfileData }) {
  const evidence = profile.evidence ?? []
  const searchSources = profile.searchSources ?? []

  return (
    <div className="space-y-2">
      <p className="text-[11px] font-black uppercase tracking-wider text-muted-foreground">근거</p>
      <div className="space-y-1.5">
        {evidence.slice(0, 6).map((item, index) => (
          <EvidenceRow
            key={`${item.id}-${index}`}
            title={`${item.id ? `[${item.id}] ` : ""}${item.title}`}
            uri={item.uri}
            summary={item.summary}
          />
        ))}
        {searchSources.slice(0, 4).map((source, index) => (
          <EvidenceRow
            key={`${source.uri}-${index}`}
            title={source.title || source.uri}
            uri={source.uri}
            summary="Gemini grounding source"
          />
        ))}
      </div>
    </div>
  )
}

function EvidenceRow({ title, uri, summary }: { title: string; uri?: string; summary?: string }) {
  const content = (
    <>
      <span className="min-w-0 flex-1 break-words">{title}</span>
      {uri ? <ExternalLink className="mt-0.5 size-3 shrink-0" /> : null}
    </>
  )

  return (
    <div className="rounded-xl bg-muted/30 px-3 py-2 text-xs leading-5 text-muted-foreground">
      {uri ? (
        <a href={uri} target="_blank" rel="noopener noreferrer" className="flex items-start gap-2 hover:text-foreground">
          {content}
        </a>
      ) : (
        <div className="flex items-start gap-2">{content}</div>
      )}
      {summary ? <p className="mt-1 text-[11px] leading-5 text-muted-foreground/80">{summary}</p> : null}
    </div>
  )
}

function normalizeConfidence(confidence?: FitProfileConfidence | string): FitProfileConfidence {
  return confidence === "CONFIRMED" || confidence === "INFERRED" || confidence === "UNCERTAIN"
    ? confidence
    : "UNCERTAIN"
}

function safeParseError(data: string) {
  try {
    const parsed = JSON.parse(data) as { message?: string }
    return parsed.message || "기업 Fit 프로필 생성 중 오류가 발생했습니다."
  } catch {
    return data || "기업 Fit 프로필 생성 중 오류가 발생했습니다."
  }
}
