"use client"

import { useRef, useMemo, useState, useCallback } from "react"
import Link from "next/link"
import {
  AlignLeft,
  ArrowRight,
  BarChart2,
  CheckCircle2,
  Code2,
  Copy,
  Eye,
  Loader2,
  MousePointer2,
  RefreshCw,
  Scissors,
  Sparkles,
  Wand2,
  Zap,
  ArrowUpDown,
  Lock,
} from "lucide-react"
import { AppSidebar } from "@/components/app-sidebar"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from "@/components/ui/card"
import { ScrollArea } from "@/components/ui/scroll-area"
import { Separator } from "@/components/ui/separator"
import { SidebarInset, SidebarProvider } from "@/components/ui/sidebar"
import { Textarea } from "@/components/ui/textarea"

// ── 데이터 ──────────────────────────────────────────────────────────────
const originalDraft = `[결제 흐름의 병목을 안정성 과제로 바꾼 경험]
SSAFY 공통 프로젝트에서 결제 서버를 맡았을 때, 저는 기능 구현보다 먼저 실패 지점을 추적 가능한 구조로 바꾸는 데 집중했습니다. 당시 결제 승인과 재시도 로직이 한 서비스에 섞여 있어 장애가 나면 원인 파악에 시간이 오래 걸렸고, 운영 관점에서도 불안정했습니다.

저는 승인, 검증, 재시도 흐름을 역할별로 분리하고 트랜잭션 경계를 다시 설계했습니다. 또한 Redis 기반 임시 상태 저장과 로그 추적 포인트를 추가해 실패 요청이 어디에서 끊겼는지 바로 확인할 수 있게 했습니다. 그 결과 QA 단계에서 반복되던 결제 누락 이슈를 사전에 줄일 수 있었고, 팀원들도 장애 상황을 훨씬 빠르게 재현하고 대응할 수 있었습니다.

이 경험을 통해 저는 백엔드 개발에서 중요한 것은 단순한 정상 동작이 아니라, 문제가 생겼을 때 빠르게 원인을 좁히고 안정적으로 복구할 수 있는 구조를 만드는 일이라는 점을 배웠습니다.`

const washedDraft = `[장애 추적이 가능한 결제 흐름으로 안정성을 높인 경험]
SSAFY 공통 프로젝트에서 결제 서버를 담당했을 때 저는 기능 구현 자체보다 장애가 발생했을 때 빠르게 원인을 파악할 수 있는 구조를 만드는 일을 우선했습니다. 당시 결제 승인과 재시도 로직이 하나의 서비스 안에 혼재되어 있어 문제가 생기면 원인 파악과 대응 속도가 모두 느려지는 상태였습니다.

이 문제를 해결하기 위해 승인, 검증, 재시도 흐름을 역할 단위로 분리하고 트랜잭션 경계를 다시 설계했습니다. 또한 Redis 기반 임시 상태 저장과 로그 추적 지점을 추가해 실패한 요청이 어느 단계에서 끊겼는지 바로 확인할 수 있도록 정리했습니다. 그 결과 QA 단계에서 반복되던 결제 누락 이슈를 사전에 줄일 수 있었고, 팀원들도 장애 상황을 더 빠르게 재현하고 대응할 수 있었습니다.

이 경험은 제가 백엔드 개발에서 중요하게 생각하는 기준을 분명하게 만들었습니다. 정상적으로 동작하는 기능을 만드는 것에서 그치지 않고, 문제가 발생했을 때 원인을 빠르게 좁히고 안정적으로 복구할 수 있는 구조까지 설계하는 개발자가 되고자 합니다.`

const initialFinalText = `[결제 장애 원인 추적으로 운영 안정성을 끌어올린 경험]
SSAFY 공통 프로젝트에서 결제 서버를 맡았을 때 저는 기능 추가보다 장애 원인을 빠르게 좁힐 수 있는 구조를 만드는 데 먼저 집중했습니다. 당시 결제 승인과 재시도 로직이 한 서비스에 섞여 있어 문제가 생기면 로그를 여러 군데 뒤져야 했고, QA 단계에서도 결제 누락 이슈를 재현하는 데 시간이 오래 걸렸습니다.

저는 승인, 검증, 재시도 흐름을 역할별로 분리하고 트랜잭션 경계를 다시 설계했습니다. 여기에 Redis 기반 임시 상태 저장과 추적 로그 지점을 추가해 실패 요청이 어느 단계에서 끊겼는지 바로 확인할 수 있게 했습니다. 그 결과 반복되던 결제 누락 이슈를 사전에 줄였고, 팀원들도 장애 상황을 더 빠르게 재현하고 대응할 수 있었습니다.

이 경험 이후 저는 백엔드 개발을 단순히 기능이 동작하도록 만드는 일이 아니라, 운영 중 문제가 생겼을 때 빠르게 원인을 좁히고 복구 가능한 구조를 설계하는 일로 바라보고 있습니다.`

// 제목 후보 세트 (버튼 클릭 시 순환)
const titleCandidateSets = [
  [
    {
      title: "[결제 장애 원인 추적으로 운영 안정성을 끌어올린 경험]",
      reason: "원인 추적과 운영 대응력을 가장 직접적으로 강조합니다.",
      tag: "추천",
    },
    {
      title: "[트랜잭션 경계 재설계로 결제 누락 이슈를 줄인 경험]",
      reason: "기술 액션이 분명하지만 운영 관점은 조금 약합니다.",
      tag: "2안",
    },
    {
      title: "[재현 가능한 장애 대응 구조를 만든 백엔드 경험]",
      reason: "질문 의도에 맞지만 표현이 다소 설명적입니다.",
      tag: "3안",
    },
  ],
  [
    {
      title: "[결제 흐름 재설계로 장애 대응 속도를 높인 경험]",
      reason: "운영 관점과 기술 행동이 균형 있게 담겼습니다.",
      tag: "추천",
    },
    {
      title: "[Redis와 추적 로그로 결제 안정성을 개선한 경험]",
      reason: "기술 스택이 명확하게 드러납니다.",
      tag: "2안",
    },
    {
      title: "[서비스 분리로 결제 장애를 선제적으로 막은 경험]",
      reason: "사전 예방 관점이 강조되어 인상적입니다.",
      tag: "3안",
    },
  ],
  [
    {
      title: "[장애 원인 가시성 확보로 결제 신뢰성을 높인 경험]",
      reason: "'가시성'이라는 키워드가 운영 성숙도를 잘 표현합니다.",
      tag: "추천",
    },
    {
      title: "[결제 서버 역할 분리로 운영 복잡도를 낮춘 경험]",
      reason: "역할 분리와 운영 관점이 자연스럽게 연결됩니다.",
      tag: "2안",
    },
    {
      title: "[QA 반복 이슈를 구조적으로 해결한 결제 개선 경험]",
      reason: "실제 문제 해결 사례를 전면에 내세운 구성입니다.",
      tag: "3안",
    },
  ],
]

// 편집 액션 정의
const editActions = [
  {
    key: "concise",
    label: "간결하게",
    icon: Scissors,
    description: "군더더기를 제거하고 핵심만 남깁니다",
    color: "text-blue-600",
    bgColor: "bg-blue-50 border-blue-200 hover:bg-blue-100",
  },
  {
    key: "tech",
    label: "기술 표현 명확하게",
    icon: Code2,
    description: "기술 용어와 구체적 맥락을 강화합니다",
    color: "text-violet-600",
    bgColor: "bg-violet-50 border-violet-200 hover:bg-violet-100",
  },
  {
    key: "impact",
    label: "첫 문장 임팩트 강화",
    icon: Zap,
    description: "문단 첫 문장을 더 강렬하게 다듬습니다",
    color: "text-amber-600",
    bgColor: "bg-amber-50 border-amber-200 hover:bg-amber-100",
  },
  {
    key: "plain",
    label: "말투 담백하게",
    icon: AlignLeft,
    description: "수식어를 줄이고 직설적으로 씁니다",
    color: "text-slate-600",
    bgColor: "bg-slate-50 border-slate-200 hover:bg-slate-100",
  },
  {
    key: "metrics",
    label: "성과 수치 강조",
    icon: BarChart2,
    description: "수치와 결과를 더 명확히 드러냅니다",
    color: "text-emerald-600",
    bgColor: "bg-emerald-50 border-emerald-200 hover:bg-emerald-100",
  },
  {
    key: "flow",
    label: "문장 흐름 자연화",
    icon: ArrowUpDown,
    description: "문장 간 연결을 자연스럽게 다듬습니다",
    color: "text-rose-600",
    bgColor: "bg-rose-50 border-rose-200 hover:bg-rose-100",
  },
]

// 목업 변환 함수
function mockTransform(text: string, actionKey: string): string {
  switch (actionKey) {
    case "concise": {
      return text
        .replace(/저는\s*/g, "")
        .replace(/\s*했습니다\./g, "했습니다.")
        .replace(/이\s*경험을\s*통해\s*/g, "")
        .replace(/단순히\s*/g, "")
        .trim()
    }
    case "tech": {
      return text
        .replace(/Redis 기반/g, "Redis 기반 TTL 관리")
        .replace(/로그 추적/g, "구조화된 로그 추적(Trace ID 기반)")
        .replace(/트랜잭션/g, "DB 트랜잭션(ACID)")
        .trim()
    }
    case "impact": {
      const sentences = text.split(/(?<=\. )/)
      if (sentences.length > 1) {
        // Move last sentence to front for impact
        return sentences[sentences.length - 1].trim() + " " + sentences.slice(0, -1).join(" ")
      }
      return "결제 장애의 근본 원인을 추적 가능한 구조로 재설계했습니다. " + text
    }
    case "plain": {
      return text
        .replace(/저는\s*/g, "")
        .replace(/\s*하고\s*있습니다/g, "합니다")
        .replace(/\s*바라보고\s*있습니다/g, "생각합니다")
        .replace(/~\s*일이라는\s*점을\s*배웠습니다/g, "입니다")
        .trim()
    }
    case "metrics": {
      return text
        .replace(/빠르게/g, "50% 이상 빠르게")
        .replace(/줄였고/g, "20% 이상 줄였고")
        .replace(/반복되던/g, "QA 6회 반복되던")
        .trim()
    }
    case "flow": {
      return text
        .replace(/그 결과/g, "이러한 개선을 통해")
        .replace(/여기에/g, "이에 더해")
        .replace(/또한/g, "함께")
        .trim()
    }
    default:
      return text
  }
}

function countChars(text: string) {
  return text.replace(/\r\n/g, "\n").replace(/\r/g, "\n").length
}

// ── 하이라이트 유틸 ──────────────────────────────────────────────────────

interface SentenceRange {
  text: string
  start: number
  end: number
  paraIdx: number
}

/** 마침표 뒤 공백 기준으로 문장 분리 */
function splitSentences(line: string): string[] {
  return line.split(/(?<=\.)\s+/).filter(Boolean)
}

/** 텍스트 전체를 { text, start, end, paraIdx } 목록으로 파싱 */
function parseSentenceRanges(text: string): SentenceRange[] {
  const result: SentenceRange[] = []
  const chunks = text.split(/\n\n/)
  let globalOffset = 0

  for (let paraIdx = 0; paraIdx < chunks.length; paraIdx++) {
    const chunk = chunks[paraIdx]
    const lines = chunk.split("\n")
    let lineOffset = 0

    for (let li = 0; li < lines.length; li++) {
      const line = lines[li]
      const sentences = splitSentences(line)
      let sentOffset = 0

      for (let si = 0; si < sentences.length; si++) {
        const sent = sentences[si]
        const start = globalOffset + lineOffset + sentOffset
        result.push({ text: sent, start, end: start + sent.length, paraIdx })
        sentOffset += sent.length + (si < sentences.length - 1 ? 1 : 0)
      }

      lineOffset += line.length + (li < lines.length - 1 ? 1 : 0)
    }

    globalOffset += chunk.length + (paraIdx < chunks.length - 1 ? 2 : 0)
  }

  return result
}

/** 커서 위치에서 해당 SentenceRange 찾기 */
function findSentenceAtCursor(ranges: SentenceRange[], cursorPos: number): SentenceRange | null {
  if (!ranges.length) return null
  for (const r of ranges) {
    if (cursorPos >= r.start && cursorPos <= r.end) return r
  }
  return ranges[ranges.length - 1]
}

/** Jaccard 유사도 (한국어 단어 토큰 기준) */
function wordSimilarity(a: string, b: string): number {
  const tokenize = (s: string) =>
    new Set(s.replace(/[^\w가-힣]/g, " ").split(/\s+/).filter(t => t.length > 1))
  const setA = tokenize(a)
  const setB = tokenize(b)
  if (!setA.size || !setB.size) return 0
  let inter = 0
  for (const t of setA) if (setB.has(t)) inter++
  return inter / (setA.size + setB.size - inter)
}

// ── HighlightedText 컴포넌트 ──────────────────────────────────────────────

function HighlightedText({
  text,
  activeParagraphIdx,
  activeSentenceText,
  className,
}: {
  text: string
  activeParagraphIdx: number | null
  activeSentenceText: string | null
  className?: string
}) {
  const chunks = text.split(/\n\n/)

  return (
    <div className={className}>
      {chunks.map((chunk, chunkIdx) => {
        const isActivePara = chunkIdx === activeParagraphIdx
        const lines = chunk.split("\n")

        // 문단 내 모든 문장의 유사도를 미리 계산해 최고점 문장만 강조
        const allSentences = lines.flatMap(l => (l.trim() ? splitSentences(l) : []))
        const sims = allSentences.map(s =>
          isActivePara && activeSentenceText ? wordSimilarity(s, activeSentenceText) : 0,
        )
        const maxSim = Math.max(...sims, 0)
        const bestIdx = maxSim > 0.12 ? sims.indexOf(maxSim) : -1

        return (
          <div
            key={chunkIdx}
            className={`rounded transition-colors duration-200 ${isActivePara ? "bg-amber-50" : ""} ${chunkIdx > 0 ? "mt-3" : ""}`}
          >
            {lines.map((line, li) => {
              if (!line.trim()) return null
              const sentences = splitSentences(line)
              // 이 line이 시작하는 allSentences 내 offset 계산
              const lineOffset = lines
                .slice(0, li)
                .flatMap(l => (l.trim() ? splitSentences(l) : []))
                .length
              return (
                <span key={li} className={li > 0 ? "block mt-1" : "block"}>
                  {sentences.map((sent, si) => {
                    const isActiveSent = lineOffset + si === bestIdx
                    return (
                      <span key={si}>
                        {si > 0 && " "}
                        <span
                          className={`transition-colors duration-150 rounded-sm ${isActiveSent ? "bg-amber-300/70" : ""}`}
                        >
                          {sent}
                        </span>
                      </span>
                    )
                  })}
                </span>
              )
            })}
          </div>
        )
      })}
    </div>
  )
}

// ── 컴포넌트 ─────────────────────────────────────────────────────────────
export default function WorkspaceMockFinalPage() {
  const textareaRef = useRef<HTMLTextAreaElement>(null)

  const [finalText, setFinalText] = useState(initialFinalText)
  const [selection, setSelection] = useState<{ text: string; start: number; end: number } | null>(null)
  const [applyingAction, setApplyingAction] = useState<string | null>(null)

  const [titleSetIndex, setTitleSetIndex] = useState(0)
  const [selectedTitle, setSelectedTitle] = useState(titleCandidateSets[0][0].title)
  const [isRefreshingTitle, setIsRefreshingTitle] = useState(false)

  // 하이라이트 상태
  const [activeParagraphIdx, setActiveParagraphIdx] = useState<number | null>(null)
  const [activeSentenceText, setActiveSentenceText] = useState<string | null>(null)

  const charCount = useMemo(() => countChars(finalText), [finalText])
  const currentTitleCandidates = titleCandidateSets[titleSetIndex]
  const sentenceRanges = useMemo(() => parseSentenceRanges(finalText), [finalText])

  // 커서 위치 → 현재 문장 업데이트
  const updateActiveHighlight = useCallback(() => {
    const ta = textareaRef.current
    if (!ta) return
    const active = findSentenceAtCursor(sentenceRanges, ta.selectionStart)
    if (active) {
      setActiveParagraphIdx(active.paraIdx)
      setActiveSentenceText(active.text)
    }
  }, [sentenceRanges])

  const clearActiveHighlight = useCallback(() => {
    setActiveParagraphIdx(null)
    setActiveSentenceText(null)
  }, [])

  // 텍스트 선택 감지 (AI 액션용)
  const handleSelect = useCallback(() => {
    const ta = textareaRef.current
    if (!ta) return
    const start = ta.selectionStart
    const end = ta.selectionEnd
    if (start < end) {
      setSelection({ text: ta.value.substring(start, end), start, end })
    } else {
      setSelection(null)
    }
  }, [])

  // 액션 적용
  const applyAction = useCallback(
    (actionKey: string) => {
      if (!selection || applyingAction) return
      setApplyingAction(actionKey)
      setTimeout(() => {
        const transformed = mockTransform(selection.text, actionKey)
        setFinalText((prev) => prev.substring(0, selection.start) + transformed + prev.substring(selection.end))
        setSelection(null)
        setApplyingAction(null)
      }, 900)
    },
    [selection, applyingAction],
  )

  // 제목 재추천
  const handleRefreshTitle = () => {
    setIsRefreshingTitle(true)
    setTimeout(() => {
      const nextIndex = (titleSetIndex + 1) % titleCandidateSets.length
      setTitleSetIndex(nextIndex)
      setSelectedTitle(titleCandidateSets[nextIndex][0].title)
      setIsRefreshingTitle(false)
    }, 800)
  }

  return (
    <SidebarProvider>
      <AppSidebar />
      <SidebarInset className="min-h-0">
        <div className="flex h-screen min-h-0 flex-col">
          {/* ── 헤더 ── */}
          <header className="flex h-14 shrink-0 items-center justify-between gap-4 border-b border-border bg-background px-6">
            <div className="flex items-center gap-3">
              <h1 className="text-base font-semibold tracking-tight">작업실</h1>
              <Separator orientation="vertical" className="h-4" />
              <span className="text-sm text-muted-foreground">Kakao · 백엔드 엔지니어 · 성장 경험</span>
            </div>
            <Button asChild variant="outline" size="sm" className="rounded-full">
              <Link href="/workspace">
                현재 작업실
                <ArrowRight className="size-3.5" />
              </Link>
            </Button>
          </header>

          {/* ── 3열 메인 ── */}
          <main className="grid min-h-0 flex-1 grid-cols-[2fr_2.5fr_2fr] overflow-hidden bg-muted/30 p-3 gap-3">
            {/* ── 왼쪽: AI 보조 패널 ── */}
            <div className="flex flex-col overflow-hidden rounded-2xl border border-border bg-background">
              <div className="shrink-0 border-b border-border px-4 py-3">
                <div className="flex items-center gap-2">
                  <Sparkles className="size-4 text-primary" />
                  <span className="text-sm font-semibold">AI 보조</span>
                </div>
              </div>
              <ScrollArea className="flex-1">
                <div className="space-y-5 p-4">
                  {/* 제목 추천 */}
                  <section className="space-y-3">
                    <div className="flex items-center justify-between">
                      <p className="text-xs font-semibold uppercase tracking-wider text-muted-foreground">제목 추천</p>
                      <Badge variant="secondary" className="rounded-full text-[10px]">현재 본문 기준</Badge>
                    </div>

                    <div className="space-y-2">
                      {currentTitleCandidates.map((candidate, idx) => {
                        const isSelected = selectedTitle === candidate.title
                        return (
                          <button
                            key={candidate.title}
                            type="button"
                            onClick={() => setSelectedTitle(candidate.title)}
                            className={`w-full rounded-xl border px-3 py-3 text-left transition-all ${
                              isSelected
                                ? "border-primary bg-primary/5 shadow-sm"
                                : "border-border/60 bg-muted/20 hover:border-primary/30 hover:bg-muted/40"
                            }`}
                          >
                            <div className="flex items-center justify-between gap-1.5 mb-1.5">
                              <Badge
                                variant={idx === 0 ? "default" : "secondary"}
                                className="rounded-full text-[10px] px-2 py-0.5"
                              >
                                {candidate.tag}
                              </Badge>
                              {isSelected && <CheckCircle2 className="size-3.5 text-primary shrink-0" />}
                            </div>
                            <p className="text-sm font-semibold leading-5 text-foreground">{candidate.title}</p>
                            <p className="mt-1 text-xs leading-5 text-muted-foreground">{candidate.reason}</p>
                          </button>
                        )
                      })}
                    </div>

                    <Button
                      variant="outline"
                      className="w-full rounded-xl text-xs"
                      size="sm"
                      onClick={handleRefreshTitle}
                      disabled={isRefreshingTitle}
                    >
                      {isRefreshingTitle ? (
                        <Loader2 className="size-3.5 animate-spin" />
                      ) : (
                        <RefreshCw className="size-3.5" />
                      )}
                      AI 제목 다시 추천
                    </Button>
                  </section>

                  <Separator />

                  {/* 본문 편집 액션 */}
                  <section className="space-y-3">
                    <div className="flex items-center justify-between">
                      <p className="text-xs font-semibold uppercase tracking-wider text-muted-foreground">본문 편집</p>
                    </div>

                    {/* 선택 텍스트 미리보기 */}
                    <div
                      className={`rounded-xl border px-3 py-2.5 transition-all ${
                        selection
                          ? "border-primary/30 bg-primary/5"
                          : "border-dashed border-border/60 bg-muted/20"
                      }`}
                    >
                      {selection ? (
                        <div className="space-y-1">
                          <div className="flex items-center gap-1.5">
                            <MousePointer2 className="size-3 text-primary" />
                            <span className="text-[10px] font-semibold text-primary">선택됨 · {selection.text.length}자</span>
                          </div>
                          <p className="text-[11px] leading-4 text-foreground line-clamp-2">
                            {selection.text}
                          </p>
                        </div>
                      ) : (
                        <div className="flex items-center gap-1.5 text-muted-foreground">
                          <MousePointer2 className="size-3" />
                          <span className="text-[11px]">가운데 본문에서 텍스트를 드래그하세요</span>
                        </div>
                      )}
                    </div>

                    {/* 액션 버튼들 */}
                    <div className="space-y-1.5">
                      {editActions.map((action) => {
                        const Icon = action.icon
                        const isApplying = applyingAction === action.key
                        const isDisabled = !selection || (!!applyingAction && !isApplying)
                        return (
                          <button
                            key={action.key}
                            type="button"
                            disabled={isDisabled}
                            onClick={() => applyAction(action.key)}
                            className={`flex w-full items-center gap-2.5 rounded-xl border px-3 py-2.5 text-left transition-all ${
                              isDisabled
                                ? "cursor-not-allowed border-border/40 bg-muted/10 opacity-40"
                                : `cursor-pointer ${action.bgColor}`
                            } ${isApplying ? "opacity-80" : ""}`}
                          >
                            {isApplying ? (
                              <Loader2 className="size-3.5 shrink-0 animate-spin text-muted-foreground" />
                            ) : (
                              <Icon className={`size-3.5 shrink-0 ${isDisabled ? "text-muted-foreground" : action.color}`} />
                            )}
                            <div className="min-w-0">
                              <p className={`text-sm font-semibold ${isDisabled ? "text-muted-foreground" : "text-foreground"}`}>
                                {isApplying ? "적용 중..." : action.label}
                              </p>
                              <p className="text-xs leading-4 text-muted-foreground truncate">
                                {action.description}
                              </p>
                            </div>
                          </button>
                        )
                      })}
                    </div>
                  </section>
                </div>
              </ScrollArea>
            </div>

            {/* ── 가운데: 편집기 ── */}
            <div className="flex flex-col overflow-hidden rounded-2xl border border-border bg-background">
              {/* 편집기 헤더 */}
              <div className="shrink-0 border-b border-border bg-muted/20 px-5 py-3">
                <div className="flex items-center justify-between gap-3">
                  <div className="min-w-0">
                    <p className="text-sm font-semibold">최종 편집본</p>
                    <p className="text-xs text-muted-foreground truncate">
                      선택된 제목: <span className="text-foreground">{selectedTitle}</span>
                    </p>
                  </div>
                  <div className="flex shrink-0 items-center gap-2">
                    <Badge variant="secondary" className="rounded-full px-2 py-0.5 text-[11px] font-bold tabular-nums">
                      {charCount} / 1000자
                    </Badge>
                    <Button
                      variant="outline"
                      size="sm"
                      className="rounded-full text-xs"
                      onClick={() => setFinalText(washedDraft)}
                    >
                      <RefreshCw className="size-3.5" />
                      세탁본으로 초기화
                    </Button>
                    <Button
                      size="sm"
                      className="rounded-full text-xs"
                      onClick={() => navigator.clipboard.writeText(finalText)}
                    >
                      <Copy className="size-3.5" />
                      복사
                    </Button>
                  </div>
                </div>
              </div>

              {/* 텍스트 에어리어 */}
              <div className="flex min-h-0 flex-1 flex-col p-4">
                <Textarea
                  ref={textareaRef}
                  value={finalText}
                  onChange={(e) => setFinalText(e.target.value)}
                  onSelect={handleSelect}
                  onClick={() => { handleSelect(); updateActiveHighlight() }}
                  onMouseUp={() => { handleSelect(); updateActiveHighlight() }}
                  onKeyUp={() => { handleSelect(); updateActiveHighlight() }}
                  onBlur={clearActiveHighlight}
                  className="h-full min-h-0 flex-1 resize-none rounded-xl border-border/60 bg-muted/10 px-5 py-4 text-sm leading-8 focus-visible:ring-1"
                  placeholder="자기소개서 본문을 입력하거나 왼쪽에서 세탁본을 가져오세요."
                />
              </div>

              {/* 편집기 푸터 힌트 */}
              <div className="shrink-0 border-t border-border/60 bg-muted/10 px-5 py-2">
                <p className="text-[11px] text-muted-foreground">
                  <span className="font-semibold text-foreground">Tip:</span> 텍스트를 드래그하면 왼쪽 AI 패널에서 선택 부분만 개선할 수 있습니다.
                </p>
              </div>
            </div>

            {/* ── 오른쪽: 세탁본 (읽기 전용) ── */}
            <div className="flex flex-col overflow-hidden rounded-2xl border border-border bg-background">
              <div className="shrink-0 border-b border-border px-4 py-3">
                <div className="flex items-center justify-between gap-2">
                  <div className="flex items-center gap-2">
                    <Eye className="size-4 text-muted-foreground" />
                    <span className="text-sm font-semibold">세탁본</span>
                  </div>
                  <div className="flex items-center gap-1.5">
                    <Lock className="size-3 text-muted-foreground" />
                    <span className="text-[10px] text-muted-foreground">읽기 전용</span>
                  </div>
                </div>
                <p className="mt-0.5 text-xs text-muted-foreground">
                  AI가 의도한 방향을 파악하고 참고용으로 활용하세요
                </p>
              </div>

              <ScrollArea className="flex-1">
                <div className="space-y-4 p-4">
                  {/* 원본 초안 */}
                  <div className="space-y-2">
                    <div className="flex items-center gap-1.5">
                      <Badge variant="outline" className="rounded-full text-[10px] px-2 py-0.5">원본 초안</Badge>
                    </div>
                    <HighlightedText
                      text={originalDraft}
                      activeParagraphIdx={activeParagraphIdx}
                      activeSentenceText={activeSentenceText}
                      className="rounded-xl border border-border/50 bg-muted/20 px-3.5 py-3 text-xs leading-6 text-muted-foreground select-none"
                    />
                  </div>

                  <Separator />

                  {/* 세탁본 */}
                  <div className="space-y-2">
                    <div className="flex items-center justify-between gap-2">
                      <Badge className="rounded-full text-[10px] px-2 py-0.5 bg-primary/10 text-primary border-primary/20 hover:bg-primary/10">
                        세탁본
                      </Badge>
                      <Button
                        variant="ghost"
                        size="sm"
                        className="h-auto rounded-full px-2 py-1 text-[10px] text-muted-foreground hover:text-foreground"
                        onClick={() => setFinalText(washedDraft)}
                      >
                        편집기로 가져오기
                        <ArrowRight className="size-3 ml-1" />
                      </Button>
                    </div>
                    <HighlightedText
                      text={washedDraft}
                      activeParagraphIdx={activeParagraphIdx}
                      activeSentenceText={activeSentenceText}
                      className="rounded-xl border border-primary/15 bg-primary/5 px-3.5 py-3 text-xs leading-6 text-foreground select-none"
                    />
                  </div>

                  {/* AI 변경 요약 */}
                  <div className="space-y-2">
                    <p className="text-[11px] font-semibold uppercase tracking-wider text-muted-foreground">AI 변경 포인트</p>
                    <div className="space-y-1.5">
                      {[
                        { point: "제목", change: "병목 → 장애 추적 가능성으로 핵심 강조 이동" },
                        { point: "1문단", change: "수동형 표현을 능동형으로 전환" },
                        { point: "2문단", change: "인과 흐름을 이 문제를 해결하기 위해로 명확화" },
                        { point: "3문단", change: "가치관 선언을 구체적 지향점으로 강화" },
                      ].map((item) => (
                        <div key={item.point} className="flex gap-2 rounded-lg border border-border/50 bg-muted/20 px-2.5 py-2">
                          <Badge variant="secondary" className="h-fit shrink-0 rounded-full text-[10px] px-1.5 py-0.5 mt-0.5">
                            {item.point}
                          </Badge>
                          <p className="text-xs leading-5 text-muted-foreground">{item.change}</p>
                        </div>
                      ))}
                    </div>
                  </div>
                </div>
              </ScrollArea>
            </div>
          </main>
        </div>
      </SidebarInset>
    </SidebarProvider>
  )
}
