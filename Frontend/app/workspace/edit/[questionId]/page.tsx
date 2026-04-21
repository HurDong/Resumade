"use client"

import { useRef, useMemo, useState, useCallback, useEffect } from "react"
import { useRouter, useParams } from "next/navigation"
import {
  AlignLeft,
  ArrowLeft,
  ArrowUpDown,
  BarChart2,
  Check,
  CheckCircle2,
  ChevronDown,
  ChevronUp,
  Code2,
  Copy,
  Eye,
  Flag,
  Loader2,
  Lock,
  Maximize2,
  Minimize2,
  RefreshCw,
  RotateCcw,
  Scissors,
  Send,
  Sparkles,
  Target,
  TrendingUp,
  Undo2,
  Wand2,
  X,
  Zap,
  SpellCheck,
} from "lucide-react"
import { AppSidebar } from "@/components/app-sidebar"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { ScrollArea } from "@/components/ui/scroll-area"
import { Separator } from "@/components/ui/separator"
import { SidebarInset, SidebarProvider } from "@/components/ui/sidebar"
import { Textarea } from "@/components/ui/textarea"
import {
  fetchFinalEditorData,
  saveFinalText,
  fetchTitleSuggestions,
  fetchSiblingQuestions,
  applyEditAction,
  rewashSelection,
  checkSpelling,
  type FinalEditorData,
  type TitleCandidate,
  type QuestionNavItem,
  type SpellCorrection,
} from "@/lib/api/final-editor"
import { writeLastWorkspaceContext } from "@/lib/workspace/last-workspace-context"

// ── 편집 액션 정의 ───────────────────────────────────────────────────────────
const editActions = [
  { key: "concise",   label: "간결하게",         icon: Scissors,   description: "군더더기를 제거하고 핵심만 남깁니다",              color: "text-blue-600",   bgColor: "bg-blue-50 border-blue-200 hover:bg-blue-100" },
  { key: "tech",      label: "기술 표현 명확하게", icon: Code2,       description: "기술 용어와 구체적 맥락을 강화합니다",            color: "text-violet-600", bgColor: "bg-violet-50 border-violet-200 hover:bg-violet-100" },
  { key: "impact",    label: "첫 문장 임팩트 강화",icon: Zap,        description: "문단 첫 문장을 더 강렬하게 다듬습니다",            color: "text-amber-600",  bgColor: "bg-amber-50 border-amber-200 hover:bg-amber-100" },
  { key: "plain",     label: "말투 담백하게",      icon: AlignLeft,  description: "수식어를 줄이고 직설적으로 씁니다",               color: "text-slate-600",  bgColor: "bg-slate-50 border-slate-200 hover:bg-slate-100" },
  { key: "metrics",   label: "성과 수치 강조",     icon: BarChart2,  description: "수치와 결과를 더 명확히 드러냅니다",              color: "text-emerald-600",bgColor: "bg-emerald-50 border-emerald-200 hover:bg-emerald-100" },
  { key: "flow",      label: "문장 흐름 자연화",   icon: ArrowUpDown,description: "문장 간 연결을 자연스럽게 다듬습니다",            color: "text-rose-600",   bgColor: "bg-rose-50 border-rose-200 hover:bg-rose-100" },
  { key: "active",    label: "능동형으로 전환",    icon: RotateCcw,  description: "수동형 표현을 능동형으로 바꿉니다",               color: "text-cyan-600",   bgColor: "bg-cyan-50 border-cyan-200 hover:bg-cyan-100" },
  { key: "confident", label: "자신감 있게",        icon: TrendingUp, description: "겸손·완곡 표현을 자신감 있게 바꿉니다",           color: "text-orange-600", bgColor: "bg-orange-50 border-orange-200 hover:bg-orange-100" },
  { key: "closing",   label: "마무리 보강",        icon: Flag,       description: "마지막 문장을 더 설득력 있게 다듬습니다",         color: "text-indigo-600", bgColor: "bg-indigo-50 border-indigo-200 hover:bg-indigo-100" },
  { key: "specific",  label: "구체성 강화",        icon: Target,     description: "막연한 표현을 구체적 행동·결과로 전환합니다",     color: "text-teal-600",   bgColor: "bg-teal-50 border-teal-200 hover:bg-teal-100" },
]

// ── 유틸 함수 ────────────────────────────────────────────────────────────────

function countChars(text: string) {
  return text.replace(/\r\n/g, "\n").replace(/\r/g, "\n").length
}

/**
 * 백엔드 텍스트 정규화:
 * 1. \r\n → \n 통일
 * 2. [제목]본문 → [제목]\n\n본문 (제목 뒤 줄바꿈 누락 보정)
 */
function normalizeDisplayText(text: string): string {
  if (!text) return text
  return text
    .replace(/\r\n/g, "\n")
    .replace(/\r/g, "\n")
    .replace(/^(\[.*?\])(?!\n)/, "$1\n\n")
}

function charCountBadgeClass(count: number, limit: number): string {
  const base = "rounded-full px-2 py-0.5 text-[11px] font-bold tabular-nums border"
  const ratio = count / limit
  if (ratio >= 1)    return `${base} bg-red-100 text-red-700 border-red-200`
  if (ratio >= 0.9)  return `${base} bg-orange-100 text-orange-700 border-orange-200`
  if (ratio >= 0.75) return `${base} bg-amber-100 text-amber-700 border-amber-200`
  return `${base} bg-secondary text-secondary-foreground border-transparent`
}

function snapToWordBoundaries(text: string, start: number, end: number): { start: number; end: number } {
  const wordBoundary = /[\s\n]/
  while (start > 0 && !wordBoundary.test(text[start - 1])) start--
  while (end < text.length && !wordBoundary.test(text[end])) end++
  return { start, end }
}

function isValidSelection(text: string): boolean {
  const trimmed = text.trim()
  return (trimmed.length >= 10 && trimmed.includes(" ")) || trimmed.length >= 20
}

function isEditableTarget(target: EventTarget | null): target is HTMLElement {
  if (!(target instanceof HTMLElement)) return false

  const tagName = target.tagName
  return (
    tagName === "INPUT" ||
    tagName === "TEXTAREA" ||
    target.isContentEditable
  )
}

// ── Diff 유틸 ────────────────────────────────────────────────────────────────

type DiffToken = { type: "equal" | "delete" | "insert"; text: string }

function computeWordDiff(before: string, after: string): DiffToken[] {
  const tokenize = (s: string) => s.split(/(\s+)/).filter(Boolean)
  const tokA = tokenize(before)
  const tokB = tokenize(after)
  const m = tokA.length
  const n = tokB.length
  const dp: number[][] = Array.from({ length: m + 1 }, () => new Array(n + 1).fill(0))
  for (let i = 1; i <= m; i++)
    for (let j = 1; j <= n; j++)
      dp[i][j] = tokA[i-1] === tokB[j-1] ? dp[i-1][j-1]+1 : Math.max(dp[i-1][j], dp[i][j-1])
  const result: DiffToken[] = []
  let i = m, j = n
  while (i > 0 || j > 0) {
    if (i > 0 && j > 0 && tokA[i-1] === tokB[j-1]) { result.unshift({ type: "equal",  text: tokA[i-1] }); i--; j-- }
    else if (j > 0 && (i === 0 || dp[i][j-1] >= dp[i-1][j])) { result.unshift({ type: "insert", text: tokB[j-1] }); j-- }
    else { result.unshift({ type: "delete", text: tokA[i-1] }); i-- }
  }
  return result
}

// ── 하이라이트 유틸 ──────────────────────────────────────────────────────────

interface SentenceRange { text: string; start: number; end: number; paraIdx: number }

function splitSentences(line: string): string[] {
  return line.split(/(?<=\.)\s+/).filter(Boolean)
}

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

function findSentenceAtCursor(ranges: SentenceRange[], cursorPos: number): SentenceRange | null {
  if (!ranges.length) return null
  for (const r of ranges) if (cursorPos >= r.start && cursorPos <= r.end) return r
  return ranges[ranges.length - 1]
}

function wordSimilarity(a: string, b: string): number {
  const tokenize = (s: string) => new Set(s.replace(/[^\w가-힣]/g, " ").split(/\s+/).filter((t) => t.length > 1))
  const setA = tokenize(a)
  const setB = tokenize(b)
  if (!setA.size || !setB.size) return 0
  let inter = 0
  for (const t of setA) if (setB.has(t)) inter++
  return inter / (setA.size + setB.size - inter)
}

// ── HighlightedText 컴포넌트 ──────────────────────────────────────────────────

function HighlightedText({ text, activeParagraphIdx, activeSentenceText, className }: {
  text: string; activeParagraphIdx: number | null; activeSentenceText: string | null; className?: string
}) {
  const chunks = text.split(/\n\n/)
  return (
    <div className={className}>
      {chunks.map((chunk, chunkIdx) => {
        const isActivePara = chunkIdx === activeParagraphIdx
        const lines = chunk.split("\n")
        const allSentences = lines.flatMap((l) => (l.trim() ? splitSentences(l) : []))
        const sims = allSentences.map((s) => isActivePara && activeSentenceText ? wordSimilarity(s, activeSentenceText) : 0)
        const maxSim = Math.max(...sims, 0)
        const bestIdx = maxSim > 0.12 ? sims.indexOf(maxSim) : -1
        return (
          <div key={chunkIdx} className={`rounded transition-colors duration-200 ${isActivePara ? "bg-amber-50" : ""} ${chunkIdx > 0 ? "mt-3" : ""}`}>
            {lines.map((line, li) => {
              if (!line.trim()) return null
              const sentences = splitSentences(line)
              const lineOffset = lines.slice(0, li).flatMap((l) => (l.trim() ? splitSentences(l) : [])).length
              return (
                <span key={li} className={li > 0 ? "block mt-1" : "block"}>
                  {sentences.map((sent, si) => {
                    const isActiveSent = lineOffset + si === bestIdx
                    return (
                      <span key={si}>
                        {si > 0 && " "}
                        <span className={`transition-colors duration-150 rounded-sm ${isActiveSent ? "bg-amber-300/70" : ""}`}>{sent}</span>
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

// ── Diff 패널 컴포넌트 ────────────────────────────────────────────────────────

const DIFF_DURATION_SEC = 15

function DiffPanel({ tokens, label, onClose, onUndo }: {
  tokens: DiffToken[]
  label: string
  onClose: () => void
  onUndo?: () => void
}) {
  const [progress, setProgress] = useState(100)

  useEffect(() => {
    const start = Date.now()
    const id = setInterval(() => {
      const pct = Math.max(0, 100 - ((Date.now() - start) / (DIFF_DURATION_SEC * 1000)) * 100)
      setProgress(pct)
      if (pct === 0) { clearInterval(id); onClose() }
    }, 80)
    return () => clearInterval(id)
  }, [onClose])

  const deleteCount = tokens.filter((t) => t.type === "delete").length
  const insertCount = tokens.filter((t) => t.type === "insert").length

  return (
    <div className="shrink-0 border-t-2 border-emerald-300 bg-emerald-50 px-5 pt-4 pb-3 space-y-3">

      {/* 헤더 */}
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-2">
          <div className="flex items-center justify-center size-6 rounded-full bg-emerald-100 border border-emerald-300">
            <Sparkles className="size-3.5 text-emerald-600" />
          </div>
          <span className="text-[12px] font-bold text-emerald-800">
            &#39;{label}&#39; 적용 완료
          </span>
          <div className="flex items-center gap-1.5 ml-1">
            {deleteCount > 0 && (
              <span className="rounded-full bg-red-100 border border-red-200 px-2 py-0.5 text-[10px] font-bold text-red-700">
                -{deleteCount}
              </span>
            )}
            {insertCount > 0 && (
              <span className="rounded-full bg-emerald-100 border border-emerald-300 px-2 py-0.5 text-[10px] font-bold text-emerald-700">
                +{insertCount}
              </span>
            )}
          </div>
        </div>
        <div className="flex items-center gap-1.5">
          {onUndo && (
            <button
              type="button"
              onClick={() => { onUndo(); onClose() }}
              className="flex items-center gap-1 rounded-full border border-amber-300 bg-amber-50 px-2.5 py-1 text-[11px] font-bold text-amber-700 transition-colors hover:bg-amber-100"
            >
              <Undo2 className="size-3" />
              되돌리기
            </button>
          )}
          <button
            type="button"
            onClick={onClose}
            className="flex items-center justify-center size-6 rounded-full text-emerald-500 hover:bg-emerald-100 hover:text-emerald-700 transition-colors"
          >
            <X className="size-3.5" />
          </button>
        </div>
      </div>

      {/* Diff 본문 */}
      <div className="rounded-xl border border-emerald-200 bg-white shadow-sm px-4 py-3 text-[13px] leading-7 select-none">
        {tokens.map((tok, i) => {
          if (tok.type === "equal")
            return <span key={i} className="text-foreground/50">{tok.text}</span>
          if (tok.type === "delete")
            return (
              <span key={i} className="mx-0.5 rounded-md bg-red-100 px-1.5 py-0.5 font-semibold text-red-700 line-through border border-red-200">
                {tok.text}
              </span>
            )
          return (
            <span key={i} className="mx-0.5 rounded-md bg-emerald-100 px-1.5 py-0.5 font-semibold text-emerald-800 border border-emerald-200">
              {tok.text}
            </span>
          )
        })}
      </div>

      {/* 카운트다운 프로그레스바 */}
      <div className="flex items-center gap-2">
        <div className="flex-1 h-1 rounded-full bg-emerald-100 overflow-hidden">
          <div
            className="h-full rounded-full bg-emerald-400 transition-none"
            style={{ width: `${progress}%` }}
          />
        </div>
        <span className="text-[10px] text-emerald-600 tabular-nums shrink-0">
          {Math.ceil(progress / 100 * DIFF_DURATION_SEC)}초
        </span>
      </div>

    </div>
  )
}

// ── 로딩/에러 화면 ────────────────────────────────────────────────────────────

function LoadingScreen() {
  return (
    <SidebarProvider>
      <AppSidebar />
      <SidebarInset className="min-h-0">
        <div className="flex h-screen items-center justify-center">
          <div className="flex flex-col items-center gap-3 text-muted-foreground">
            <Loader2 className="size-8 animate-spin" />
            <p className="text-sm">최종 편집기 불러오는 중...</p>
          </div>
        </div>
      </SidebarInset>
    </SidebarProvider>
  )
}

function ErrorScreen({ message, onBack }: { message: string; onBack: () => void }) {
  return (
    <SidebarProvider>
      <AppSidebar />
      <SidebarInset className="min-h-0">
        <div className="flex h-screen items-center justify-center">
          <div className="flex flex-col items-center gap-4 text-center">
            <p className="text-sm text-destructive">{message}</p>
            <Button variant="outline" size="sm" onClick={onBack}>
              <ArrowLeft className="size-3.5" /> 돌아가기
            </Button>
          </div>
        </div>
      </SidebarInset>
    </SidebarProvider>
  )
}

// ── 메인 컴포넌트 ────────────────────────────────────────────────────────────

export default function FinalEditorPage() {
  const router = useRouter()
  const params = useParams()
  const questionId = Number(params.questionId)
  const textareaRef = useRef<HTMLTextAreaElement>(null)
  const spellResultRef = useRef<HTMLDivElement>(null)
  // 초기 로드 완료 여부 — true 이후부터만 자동저장 활성화
  const isInitialized = useRef(false)

  // ── 서버 데이터 ──────────────────────────────────────────────────────────
  const [data, setData]         = useState<FinalEditorData | null>(null)
  const [isLoading, setIsLoading] = useState(true)
  const [loadError, setLoadError] = useState<string | null>(null)

  // ── 편집 상태 ────────────────────────────────────────────────────────────
  const [finalText, setFinalText]   = useState("")
  const [selection, setSelection]   = useState<{ text: string; start: number; end: number } | null>(null)
  const [applyingAction, setApplyingAction] = useState<string | null>(null)
  const [undoStack, setUndoStack]   = useState<string[]>([])
  const [customPromptText, setCustomPromptText] = useState("")
  const [isRewashing, setIsRewashing] = useState(false)

  // ── 맞춤법 검사 ──────────────────────────────────────────────────────────
  const [isSpellChecking, setIsSpellChecking]       = useState(false)
  const [spellCorrections, setSpellCorrections]     = useState<SpellCorrection[] | null>(null)

  // ── 제목 추천 ────────────────────────────────────────────────────────────
  const [titleCandidates, setTitleCandidates]     = useState<TitleCandidate[]>([])
  const [selectedTitle, setSelectedTitle]         = useState<string>("")
  const [isRefreshingTitle, setIsRefreshingTitle] = useState(false)
  const [isTitleLoading, setIsTitleLoading]       = useState(false)

  // ── 하이라이트 ───────────────────────────────────────────────────────────
  const [activeParagraphIdx, setActiveParagraphIdx] = useState<number | null>(null)
  const [activeSentenceText, setActiveSentenceText] = useState<string | null>(null)

  // ── UI 상태 ──────────────────────────────────────────────────────────────
  const [focusMode, setFocusMode]       = useState(false)
  const [showQuestion, setShowQuestion] = useState(false)
  const [saveStatus, setSaveStatus]     = useState<"idle" | "saving" | "saved">("idle")
  const [lastSavedAt, setLastSavedAt]   = useState<Date | null>(null)
  const [tick, setTick]                 = useState(0)
  const [diffView, setDiffView]         = useState<{ tokens: DiffToken[]; label: string } | null>(null)

  // ── 문항 네비게이터 ──────────────────────────────────────────────────────
  const [siblings, setSiblings]           = useState<QuestionNavItem[]>([])

  // ── 초기 데이터 로드 ─────────────────────────────────────────────────────
  useEffect(() => {
    if (!questionId) return
    setIsLoading(true)
    fetchFinalEditorData(questionId)
      .then((d) => {
        setData(d)
        // 백엔드 텍스트 정규화 적용 (줄바꿈·제목 구분 보정)
        setFinalText(normalizeDisplayText(d.finalText ?? d.washedDraft ?? ""))
        setSelectedTitle(d.selectedTitle ?? "")
        if (d.titleCandidates && d.titleCandidates.length > 0) {
          setTitleCandidates(d.titleCandidates)
        }
        setLoadError(null)
      })
      .then(() => {
        // 로드 완료 후 다음 마이크로태스크에서 자동저장 활성화
        // (동일 tick에서 설정하면 초기 로드값이 즉시 저장되는 race condition 발생)
        isInitialized.current = true
      })
      .catch((e) => setLoadError(e.message))
      .finally(() => setIsLoading(false))
  }, [questionId])

  // ── 형제 문항 목록 로드 ──────────────────────────────────────────────────
  useEffect(() => {
    if (!questionId) return
    fetchSiblingQuestions(questionId)
      .then(setSiblings)
      .catch(() => { /* 네비게이터 없어도 편집기는 정상 동작 */ })
  }, [questionId])

  // ── 상대 시간 ticker (30초) ──────────────────────────────────────────────
  useEffect(() => {
    const id = setInterval(() => setTick((t) => t + 1), 30_000)
    return () => clearInterval(id)
  }, [])

  useEffect(() => {
    if (!data?.applicationId) return

    writeLastWorkspaceContext({
      applicationId: String(data.applicationId),
      questionDbId: questionId,
    })
  }, [data?.applicationId, questionId])

  const relativeTime = useMemo(() => {
    if (!lastSavedAt) return null
    const diffSec = Math.floor((Date.now() - lastSavedAt.getTime()) / 1000)
    if (diffSec < 60) return "방금 전"
    const diffMin = Math.floor(diffSec / 60)
    if (diffMin < 60) return `${diffMin}분 전`
    return `${Math.floor(diffMin / 60)}시간 전`
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [lastSavedAt, tick])

  // ── 자동저장 (1.5초 디바운스) ────────────────────────────────────────────
  useEffect(() => {
    // 초기 로드 값 세팅은 저장하지 않음 (불필요한 덮어쓰기 방지)
    if (!questionId || !isInitialized.current) return
    const timer = setTimeout(() => {
      setSaveStatus("saving")
      saveFinalText(questionId, finalText, selectedTitle)
        .then(() => {
          setSaveStatus("saved")
          setLastSavedAt(new Date()) // 서버 timestamp 대신 저장 성공 시점 클라이언트 시간 사용
        })
        .catch(() => {
          // 저장 실패 시 상태 복원
          setSaveStatus("idle")
        })
    }, 1500)
    return () => clearTimeout(timer)
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [finalText, selectedTitle])

  // ── Derived ──────────────────────────────────────────────────────────────
  const charCount           = useMemo(() => countChars(finalText), [finalText])
  const maxLength           = data?.maxLength ?? 1000
  const sentenceRanges      = useMemo(() => parseSentenceRanges(finalText), [finalText])

  // ── 제목 즉시 적용 ───────────────────────────────────────────────────────
  const applyTitle = useCallback((title: string) => {
    setSelectedTitle(title)
    setFinalText((prev) => {
      const match = prev.match(/^\[.*?\]/)
      if (match) return title + prev.slice(match[0].length)
      return title + "\n" + prev
    })
  }, [])

  // ── 제목 다시 추천 ───────────────────────────────────────────────────────
  const handleRefreshTitle = async () => {
    setIsRefreshingTitle(true)
    try {
      const d = await fetchTitleSuggestions(questionId)
      setTitleCandidates(d.candidates ?? [])
    } finally {
      setIsRefreshingTitle(false)
    }
  }

  // ── 하이라이트 ───────────────────────────────────────────────────────────
  const updateActiveHighlight = useCallback(() => {
    const ta = textareaRef.current
    if (!ta) return
    const active = findSentenceAtCursor(sentenceRanges, ta.selectionStart)
    if (active) { setActiveParagraphIdx(active.paraIdx); setActiveSentenceText(active.text) }
  }, [sentenceRanges])

  const clearActiveHighlight = useCallback(() => {
    setActiveParagraphIdx(null); setActiveSentenceText(null)
  }, [])

  // ── 선택 (어절 경계 스냅) ────────────────────────────────────────────────
  const handleSelect = useCallback(() => {
    const ta = textareaRef.current
    if (!ta) return
    const rawStart = ta.selectionStart
    const rawEnd   = ta.selectionEnd
    if (rawStart < rawEnd) {
      const { start, end } = snapToWordBoundaries(ta.value, rawStart, rawEnd)
      const text = ta.value.substring(start, end).trim()
      if (isValidSelection(text)) setSelection({ text, start, end })
      else setSelection(null)
    } else {
      setSelection(null)
    }
  }, [])

  // ── AI 편집 액션 ─────────────────────────────────────────────────────────
  const applyAction = useCallback(async (actionKey: string, customPrompt?: string) => {
    if (!selection || applyingAction) return
    const actionMeta = editActions.find((a) => a.key === actionKey)
    setApplyingAction(actionKey)
    try {
      const { transformedText } = await applyEditAction(questionId, selection.text, actionKey, customPrompt)
      const before = selection.text
      // undo 스택에 현재 전체 텍스트 저장 (최대 20단계)
      setUndoStack((prev) => [...prev.slice(-19), finalText])
      setFinalText((prev) => prev.substring(0, selection.start) + transformedText + prev.substring(selection.end))
      setSelection(null)

      const tokens = computeWordDiff(before, transformedText)
      if (tokens.some((t) => t.type !== "equal")) {
        setDiffView({ tokens, label: actionMeta?.label ?? (actionKey === "custom" ? "직접 지시" : actionKey) })
      }
    } catch {
      // 실패 시 원문 유지
    } finally {
      setApplyingAction(null)
    }
  }, [questionId, selection, applyingAction, finalText])

  // ── 되돌리기 ─────────────────────────────────────────────────────────────
  const handleUndo = useCallback(() => {
    setUndoStack((prev) => {
      if (prev.length === 0) return prev
      setFinalText(prev[prev.length - 1])
      setDiffView(null)
      return prev.slice(0, -1)
    })
  }, [])

  // ── 선택 텍스트 세탁 (번역 왕복) ─────────────────────────────────────────
  const handleRewashSelection = useCallback(async () => {
    if (!selection || isRewashing) return
    setIsRewashing(true)
    try {
      const { washedText } = await rewashSelection(questionId, selection.text)
      setUndoStack((prev) => [...prev.slice(-19), finalText])
      setFinalText((prev) => prev.substring(0, selection.start) + washedText + prev.substring(selection.end))
      const tokens = computeWordDiff(selection.text, washedText)
      if (tokens.some((t) => t.type !== "equal")) {
        setDiffView({ tokens, label: "선택 세탁" })
      }
      setSelection(null)
    } catch {
      // 실패 시 원문 유지
    } finally {
      setIsRewashing(false)
    }
  }, [selection, isRewashing, questionId, finalText])

  // ── 맞춤법 결과 자동 스크롤 ─────────────────────────────────────────────
  useEffect(() => {
    if (spellCorrections !== null && spellResultRef.current) {
      spellResultRef.current.scrollIntoView({ behavior: "smooth", block: "nearest" })
    }
  }, [spellCorrections])

  // ── 맞춤법 검사 ─────────────────────────────────────────────────────────
  const handleSpellCheck = useCallback(async () => {
    if (isSpellChecking || !finalText.trim()) return
    setIsSpellChecking(true)
    setSpellCorrections(null)
    try {
      const result = await checkSpelling(questionId, finalText)
      setSpellCorrections(result.corrections)
    } catch {
      setSpellCorrections([])
    } finally {
      setIsSpellChecking(false)
    }
  }, [questionId, finalText, isSpellChecking])

  const applyCorrection = useCallback((correction: SpellCorrection) => {
    setUndoStack((prev) => [...prev.slice(-19), finalText])
    setFinalText((prev) => prev.replace(correction.errorWord, correction.suggestedWord))
    setSpellCorrections((prev) => prev ? prev.filter((c) => c.errorWord !== correction.errorWord) : null)
  }, [finalText])

  const applyAllCorrections = useCallback(() => {
    if (!spellCorrections?.length) return
    setUndoStack((prev) => [...prev.slice(-19), finalText])
    let patched = finalText
    for (const c of spellCorrections) {
      patched = patched.replace(c.errorWord, c.suggestedWord)
    }
    setFinalText(patched)
    setSpellCorrections([])
  }, [finalText, spellCorrections])

  // ── 문항 이동 (저장 후 라우팅) ───────────────────────────────────────────
  const navigateToQuestion = useCallback(async (targetId: number) => {
    if (targetId === questionId) return
    // 현재 편집본 즉시 저장 (디바운스 우회)
    setSaveStatus("saving")
    try {
      await saveFinalText(questionId, finalText, selectedTitle)
      setSaveStatus("saved")
      setLastSavedAt(new Date())
    } catch {
      // 저장 실패해도 이동은 허용
      setSaveStatus("idle")
    }
    router.push(`/workspace/edit/${targetId}`)
  }, [questionId, finalText, selectedTitle, router])

  // ── 완료하고 나가기 ──────────────────────────────────────────────────────
  const handleComplete = () => {
    if (data?.applicationId) {
      router.push(`/workspace/${data.applicationId}?questionId=${questionId}`)
    } else {
      router.push("/workspace")
    }
  }

  const blurActiveEditor = useCallback(() => {
    const activeElement = document.activeElement

    if (activeElement instanceof HTMLInputElement || activeElement instanceof HTMLTextAreaElement) {
      const caret = activeElement.selectionEnd ?? activeElement.selectionStart ?? 0
      activeElement.setSelectionRange(caret, caret)
      activeElement.blur()
    } else if (activeElement instanceof HTMLElement) {
      activeElement.blur()
    }

    clearActiveHighlight()
    setSelection(null)
  }, [clearActiveHighlight])

  const handleEscapeShortcut = useCallback(() => {
    const activeElement = document.activeElement
    const isDialogActive =
      activeElement instanceof HTMLElement && activeElement.closest('[role="dialog"]')

    if (isDialogActive) return

    if (isEditableTarget(activeElement)) {
      blurActiveEditor()
      return
    }

    if (selection) {
      setSelection(null)
      clearActiveHighlight()
      return
    }

    if (diffView) {
      setDiffView(null)
      return
    }

    if (spellCorrections !== null) {
      setSpellCorrections(null)
      return
    }

    if (showQuestion) {
      setShowQuestion(false)
      return
    }

    handleComplete()
  }, [
    blurActiveEditor,
    clearActiveHighlight,
    diffView,
    handleComplete,
    selection,
    showQuestion,
    spellCorrections,
  ])

  const handleDigitShortcut = useCallback(
    (digit: string) => {
      if (siblings.length < 2) return

      const activeElement = document.activeElement
      const isDialogActive =
        activeElement instanceof HTMLElement && activeElement.closest('[role="dialog"]')

      if (isDialogActive || isEditableTarget(activeElement)) return

      const targetIndex = Number(digit)
      const target = siblings.find((item) => item.index === targetIndex)
      if (!target || target.id === questionId) return

      void navigateToQuestion(target.id)
    },
    [navigateToQuestion, questionId, siblings],
  )

  useEffect(() => {
    const handleWindowKeyDown = (event: KeyboardEvent) => {
      if (event.defaultPrevented || event.isComposing) return

      if (event.key === "Escape") {
        handleEscapeShortcut()
        return
      }

      if (
        !event.metaKey &&
        !event.ctrlKey &&
        !event.altKey &&
        !event.shiftKey &&
        /^[1-9]$/.test(event.key)
      ) {
        handleDigitShortcut(event.key)
      }
    }

    window.addEventListener("keydown", handleWindowKeyDown)
    return () => window.removeEventListener("keydown", handleWindowKeyDown)
  }, [handleDigitShortcut, handleEscapeShortcut])

  // ── 로딩 / 에러 ──────────────────────────────────────────────────────────
  if (isLoading) return <LoadingScreen />
  if (loadError || !data) return <ErrorScreen message={loadError ?? "데이터를 불러올 수 없습니다."} onBack={handleComplete} />

  // ── 렌더 ─────────────────────────────────────────────────────────────────
  return (
    <SidebarProvider open={focusMode ? false : undefined} onOpenChange={focusMode ? () => {} : undefined}>
      <AppSidebar />
      <SidebarInset className="min-h-0">
        <div className="flex h-screen min-h-0 flex-col">

          {/* ── 헤더 ── */}
          <header className="flex h-14 shrink-0 items-center justify-between gap-4 border-b border-border bg-background px-6">
            <div className="flex items-center gap-3 min-w-0">
              <h1 className="text-base font-semibold tracking-tight shrink-0">작업실</h1>
              <Separator orientation="vertical" className="h-4 shrink-0" />
              <span className="text-sm text-muted-foreground shrink-0">
                {[data.companyName, data.position].filter(Boolean).join(" · ")}
              </span>

              {/* 문항 네비게이터 */}
              {siblings.length > 1 && (
                <div className="flex shrink-0 items-center gap-1.5">
                  <Separator orientation="vertical" className="h-4 shrink-0" />
                  {siblings.map((item) => {
                    const isCurrent = item.id === questionId
                    return (
                      <button
                        key={item.id}
                        type="button"
                        title={item.title}
                        onClick={() => void navigateToQuestion(item.id)}
                        className={`relative flex size-6 items-center justify-center rounded-full text-[11px] font-bold transition-all duration-200 ${
                          isCurrent
                            ? "bg-primary text-white shadow-sm scale-110"
                            : "bg-muted/70 text-muted-foreground hover:bg-muted hover:text-foreground hover:scale-105"
                        }`}
                      >
                        {item.index}
                        {!item.hasWashedDraft && (
                          <span className="absolute -right-0.5 -top-0.5 size-1.5 rounded-full bg-amber-400 ring-1 ring-background" />
                        )}
                      </button>
                    )
                  })}
                </div>
              )}
            </div>
            <div className="flex shrink-0 items-center gap-3">
              {/* 자동저장 상태 */}
              <div className="flex items-center gap-1.5 text-xs">
                {saveStatus === "saving" ? (
                  <><Loader2 className="size-3 animate-spin text-muted-foreground" /><span className="text-muted-foreground">저장 중...</span></>
                ) : lastSavedAt ? (
                  <><Check className="size-3 text-emerald-500" /><span className="text-emerald-600">저장됨 · {relativeTime}</span></>
                ) : null}
              </div>
              <Button
                variant="outline"
                size="sm"
                className={`rounded-full text-xs gap-1.5 transition-colors ${focusMode ? "border-primary text-primary hover:bg-primary/5" : ""}`}
                onClick={() => setFocusMode((v) => !v)}
                title={focusMode ? "일반 모드로 돌아가기" : "편집기만 집중해서 보기"}
              >
                {focusMode ? <Minimize2 className="size-3.5" /> : <Maximize2 className="size-3.5" />}
                {focusMode ? "일반 모드" : "집중 모드"}
              </Button>
              <Button size="sm" className="rounded-full text-xs gap-1.5" onClick={handleComplete}>
                <ArrowLeft className="size-3.5" />
                완료하고 나가기
              </Button>
            </div>
          </header>

          {/* ── 메인 ── */}
          <main className={`grid min-h-0 flex-1 overflow-hidden bg-muted/30 p-3 gap-3 transition-[grid-template-columns] duration-300 ${focusMode ? "grid-cols-[1fr]" : "grid-cols-[2fr_2.5fr_2fr]"}`}>

            {/* ── 왼쪽: AI 보조 패널 ── */}
            <div className={`flex flex-col overflow-hidden rounded-2xl border border-border bg-background transition-opacity duration-200 ${focusMode ? "hidden" : ""}`}>
              {/* 패널 헤더 */}
              <div className="shrink-0 border-b border-border px-4 py-3">
                <div className="flex items-center gap-2">
                  <Sparkles className="size-4 text-primary" />
                  <span className="text-sm font-semibold">AI 보조</span>
                </div>
              </div>

              {/* ── 제목 추천 (고정 높이) ── */}
              <div className="shrink-0 border-b border-border px-4 py-4 space-y-3">
                <div className="flex items-center justify-between">
                  <p className="text-xs font-semibold uppercase tracking-wider text-muted-foreground">제목 추천</p>
                  <Badge variant="secondary" className="rounded-full text-[10px]">현재 본문 기준</Badge>
                </div>

                {/* 3개 카드 영역 */}
                <div>
                  {isTitleLoading ? (
                    <div className="space-y-2">
                      {[0, 1, 2].map((i) => (
                        <div key={i} className="h-[92px] rounded-xl border border-border/40 bg-muted/10 animate-pulse" />
                      ))}
                    </div>
                  ) : titleCandidates.length > 0 ? (
                    <div className="space-y-2">
                      {titleCandidates.slice(0, 3).map((candidate, idx) => {
                        const isSelected = selectedTitle === candidate.title
                        const tag = candidate.recommended ? "추천" : `${idx + 1}안`
                        return (
                          <button
                            key={candidate.title}
                            type="button"
                            onClick={() => applyTitle(candidate.title)}
                            className={`w-full rounded-xl border px-3 py-3 text-left transition-all ${
                              isSelected
                                ? "border-primary bg-primary/5 shadow-sm"
                                : "border-border/60 bg-muted/20 hover:border-primary/30 hover:bg-muted/40"
                            }`}
                          >
                            <div className="flex items-center justify-between gap-1.5 mb-1.5">
                              <Badge
                                variant={candidate.recommended ? "default" : "secondary"}
                                className="rounded-full text-[10px] px-2 py-0.5"
                              >
                                {tag}
                              </Badge>
                              {isSelected && <CheckCircle2 className="size-3.5 text-primary shrink-0" />}
                            </div>
                            <p className="text-sm font-semibold leading-5 text-foreground">{candidate.title}</p>
                            <p className="mt-1 text-xs leading-5 text-muted-foreground line-clamp-2">{candidate.reason}</p>
                          </button>
                        )
                      })}
                    </div>
                  ) : (
                    <div className="min-h-[72px] flex items-center justify-center rounded-xl border border-dashed border-border/50 bg-muted/10">
                      <p className="text-xs text-muted-foreground px-4 text-center">
                        아직 초안이 없거나 제목 추천에 실패했습니다.
                      </p>
                    </div>
                  )}
                </div>

                <Button
                  variant="outline"
                  className="w-full rounded-xl text-xs"
                  size="sm"
                  onClick={handleRefreshTitle}
                  disabled={isRefreshingTitle}
                >
                  {isRefreshingTitle ? <Loader2 className="size-3.5 animate-spin" /> : <RefreshCw className="size-3.5" />}
                  {titleCandidates.length > 0 ? "AI 제목 다시 추천" : "AI 제목 추천"}
                </Button>
              </div>

              {/* ── 본문 편집 (나머지 공간, 독립 스크롤) ── */}
              <div className="flex min-h-0 flex-1 flex-col">
                <div className="shrink-0 px-4 pb-2 pt-4">
                  <p className="text-xs font-semibold uppercase tracking-wider text-muted-foreground">본문 편집</p>
                </div>
                <ScrollArea className="flex-1">
                  <div className="space-y-3 px-4 pb-4">
                    {/* 드래그 안내 힌트 */}
                    <div className={`rounded-xl border px-3 py-2 transition-all ${
                      selection ? "border-primary/40 bg-primary/5" : "border-dashed border-border/50 bg-muted/10"
                    }`}>
                      {selection ? (
                        <p className="text-[11px] font-medium text-primary">
                          {selection.text.length}자 선택됨 — 아래 액션을 적용하세요
                        </p>
                      ) : (
                        <p className="text-[11px] text-muted-foreground">
                          본문에서 한 문장 이상 드래그하면 액션이 활성화됩니다
                        </p>
                      )}
                    </div>

                    {/* 액션 버튼 */}
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
                            {isApplying
                              ? <Loader2 className="size-3.5 shrink-0 animate-spin text-muted-foreground" />
                              : <Icon className={`size-3.5 shrink-0 ${isDisabled ? "text-muted-foreground" : action.color}`} />
                            }
                            <div className="min-w-0">
                              <p className={`text-sm font-semibold ${isDisabled ? "text-muted-foreground" : "text-foreground"}`}>
                                {isApplying ? "적용 중..." : action.label}
                              </p>
                              <p className="text-xs leading-4 text-muted-foreground truncate">{action.description}</p>
                            </div>
                          </button>
                        )
                      })}
                    </div>

                    {/* ── 맞춤법 교정 섹션 ── */}
                    <div className="pt-1">
                      <Separator />
                    </div>
                    <div className="space-y-2">
                      <div className="flex items-center justify-between">
                        <p className="text-xs font-semibold uppercase tracking-wider text-muted-foreground">교정</p>
                        {spellCorrections !== null && (
                          <button
                            type="button"
                            onClick={() => setSpellCorrections(null)}
                            className="flex items-center justify-center size-4 rounded-full text-muted-foreground hover:text-foreground transition-colors"
                          >
                            <X className="size-3" />
                          </button>
                        )}
                      </div>

                      {/* 검사 버튼 */}
                      <button
                        type="button"
                        onClick={() => void handleSpellCheck()}
                        disabled={isSpellChecking || !finalText.trim()}
                        className={`flex w-full items-center gap-2.5 rounded-xl border px-3 py-2.5 text-left transition-all ${
                          isSpellChecking || !finalText.trim()
                            ? "cursor-not-allowed border-border/40 bg-muted/10 opacity-40"
                            : "cursor-pointer bg-rose-50 border-rose-200 hover:bg-rose-100"
                        }`}
                      >
                        {isSpellChecking
                          ? <Loader2 className="size-3.5 shrink-0 animate-spin text-muted-foreground" />
                          : <SpellCheck className="size-3.5 shrink-0 text-rose-600" />
                        }
                        <div className="min-w-0">
                          <p className={`text-sm font-semibold ${isSpellChecking || !finalText.trim() ? "text-muted-foreground" : "text-foreground"}`}>
                            {isSpellChecking ? "검사 중..." : "맞춤법 검사"}
                          </p>
                          <p className="text-xs leading-4 text-muted-foreground truncate">전체 본문의 오류를 찾아냅니다</p>
                        </div>
                      </button>

                      {/* 결과 패널 */}
                      {spellCorrections !== null && (
                        <div ref={spellResultRef} className="rounded-xl border border-border/60 bg-background overflow-hidden shadow-sm">
                          {spellCorrections.length === 0 ? (
                            <div className="flex items-center gap-2 px-3 py-3">
                              <Check className="size-3.5 text-emerald-500 shrink-0" />
                              <p className="text-xs text-muted-foreground">맞춤법 오류가 없습니다</p>
                            </div>
                          ) : (
                            <div>
                              {/* 결과 헤더 */}
                              <div className="flex items-center justify-between px-3 py-2 bg-rose-50 border-b border-rose-100">
                                <div className="flex items-center gap-1.5">
                                  <span className="flex size-4 items-center justify-center rounded-full bg-rose-500 text-[9px] font-bold text-white">
                                    {spellCorrections.length}
                                  </span>
                                  <span className="text-[11px] font-bold text-rose-700">
                                    개 오류 발견
                                  </span>
                                </div>
                                <button
                                  type="button"
                                  onClick={applyAllCorrections}
                                  className="rounded-full bg-primary px-2.5 py-1 text-[10px] font-bold text-white transition-colors hover:bg-primary/90"
                                >
                                  전체 수정
                                </button>
                              </div>

                              {/* 개별 교정 카드 */}
                              <div className="divide-y divide-border/30">
                                {spellCorrections.map((c, i) => {
                                  const reasonColor: Record<string, string> = {
                                    "맞춤법 오류":   "bg-red-100 text-red-700 border-red-200",
                                    "띄어쓰기 오류": "bg-blue-100 text-blue-700 border-blue-200",
                                    "어미 오류":     "bg-orange-100 text-orange-700 border-orange-200",
                                    "조사 오류":     "bg-violet-100 text-violet-700 border-violet-200",
                                    "비단어 오류":   "bg-rose-100 text-rose-700 border-rose-200",
                                  }
                                  const badgeClass = reasonColor[c.reason] ?? "bg-muted text-muted-foreground border-border"
                                  const isDeletion = !c.suggestedWord

                                  return (
                                    <button
                                      key={`${c.errorWord}-${i}`}
                                      type="button"
                                      onClick={() => applyCorrection(c)}
                                      className="group w-full px-3 py-3 text-left transition-colors hover:bg-muted/40"
                                    >
                                      {/* 오류 → 교정 흐름 */}
                                      <div className="flex items-center gap-2 flex-wrap">
                                        <span className="rounded bg-red-50 border border-red-200 px-1.5 py-0.5 text-xs font-bold text-red-600 line-through decoration-red-400">
                                          {c.errorWord}
                                        </span>
                                        <span className="text-[10px] text-muted-foreground shrink-0">→</span>
                                        {isDeletion ? (
                                          <span className="rounded bg-slate-100 border border-slate-200 px-1.5 py-0.5 text-[10px] font-bold text-slate-500 italic">
                                            삭제 권고
                                          </span>
                                        ) : (
                                          <span className="rounded bg-emerald-50 border border-emerald-200 px-1.5 py-0.5 text-xs font-bold text-emerald-700">
                                            {c.suggestedWord}
                                          </span>
                                        )}
                                      </div>
                                      {/* 사유 + 액션 힌트 */}
                                      <div className="mt-1.5 flex items-center justify-between gap-1.5">
                                        <span className={`rounded-full border px-2 py-0.5 text-[9px] font-bold ${badgeClass}`}>
                                          {c.reason}
                                        </span>
                                        <span className="text-[10px] text-muted-foreground/50 group-hover:text-muted-foreground transition-colors shrink-0">
                                          클릭하여 적용
                                        </span>
                                      </div>
                                    </button>
                                  )
                                })}
                              </div>
                            </div>
                          )}
                        </div>
                      )}
                    </div>
                  </div>
                </ScrollArea>
              </div>
            </div>

            {/* ── 가운데: 편집기 ── */}
            <div className="flex flex-col overflow-hidden rounded-2xl border border-border bg-background">

              {/* 편집기 헤더 */}
              <div className="shrink-0 border-b border-border bg-muted/20 px-5 py-3">
                <div className="flex items-center justify-between gap-3">
                  <div className="min-w-0">
                    <p className="text-sm font-semibold">최종 편집본</p>
                    {selectedTitle && (
                      <p className="text-xs text-muted-foreground truncate">
                        선택된 제목: <span className="text-foreground">{selectedTitle}</span>
                      </p>
                    )}
                  </div>
                  <div className="flex shrink-0 items-center gap-2">
                    <span className={charCountBadgeClass(charCount, maxLength)}>
                      {charCount} / {maxLength.toLocaleString()}자{charCount >= maxLength ? " 초과" : ""}
                    </span>
                    <Button
                      variant="outline"
                      size="sm"
                      className="rounded-full text-xs"
                      onClick={() => setFinalText(normalizeDisplayText(data.washedDraft ?? ""))}
                    >
                      <RefreshCw className="size-3.5" />
                      세탁본 복원
                    </Button>
                    <Button
                      variant="outline"
                      size="sm"
                      disabled={!selection || isRewashing}
                      className="rounded-full text-xs border-violet-200 text-violet-700 hover:bg-violet-50 disabled:opacity-40"
                      onClick={() => void handleRewashSelection()}
                    >
                      {isRewashing
                        ? <Loader2 className="size-3.5 animate-spin" />
                        : <Sparkles className="size-3.5" />
                      }
                      선택 세탁
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

              {/* 문항 Collapsible */}
              <button
                type="button"
                onClick={() => setShowQuestion((v) => !v)}
                className="flex w-full shrink-0 items-center justify-between border-b border-border/50 bg-muted/10 px-5 py-2 text-left transition-colors hover:bg-muted/20"
              >
                <div className="flex items-center gap-2 min-w-0">
                  <span className="shrink-0 text-[11px] font-semibold text-muted-foreground uppercase tracking-wider">문항</span>
                  {!showQuestion && (
                    <span className="truncate text-[11px] text-muted-foreground">{data.questionText}</span>
                  )}
                </div>
                {showQuestion
                  ? <ChevronUp className="size-3.5 shrink-0 text-muted-foreground" />
                  : <ChevronDown className="size-3.5 shrink-0 text-muted-foreground" />
                }
              </button>
              {showQuestion && (
                <div className="shrink-0 border-b border-border/50 bg-muted/10 px-5 py-3">
                  <p className="text-sm leading-6 text-foreground">{data.questionText}</p>
                </div>
              )}

              {/* 텍스트에어리어 */}
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
                  placeholder="자기소개서 본문을 입력하거나 세탁본을 가져오세요."
                />
              </div>

              {/* Diff 패널 */}
              {diffView && (
                <DiffPanel
                  key={diffView.label + diffView.tokens.length}
                  tokens={diffView.tokens}
                  label={diffView.label}
                  onClose={() => setDiffView(null)}
                  onUndo={undoStack.length > 0 ? handleUndo : undefined}
                />
              )}

              {/* 직접 지시 패널 */}
              {!diffView && (
                <div className={`shrink-0 border-t transition-colors duration-200 ${
                  selection ? "border-primary/30 bg-primary/[0.03]" : "border-border/50 bg-muted/5"
                }`}>
                  <div className="px-4 pt-3 pb-2">
                    <div className={`flex items-start gap-2.5 rounded-xl border bg-background shadow-sm transition-all duration-200 ${
                      selection
                        ? "border-primary/40 ring-1 ring-primary/10 shadow-primary/5"
                        : "border-border/50 opacity-60"
                    }`}>
                      <Wand2 className={`mt-3 ml-3 size-4 shrink-0 transition-colors ${selection ? "text-primary" : "text-muted-foreground"}`} />
                      <textarea
                        rows={2}
                        value={customPromptText}
                        onChange={(e) => setCustomPromptText(e.target.value)}
                        disabled={!selection}
                        placeholder={selection
                          ? `${selection.text.length}자 선택됨 — 어떻게 바꿀까요? (예: 더 자신감 있게, 기술 맥락 강화, 마무리 문장 개선...)`
                          : "본문에서 수정할 부분을 드래그한 뒤 지시를 입력하세요"
                        }
                        onKeyDown={(e) => {
                          if (e.key === "Enter" && (e.metaKey || e.ctrlKey) && customPromptText.trim() && selection) {
                            e.preventDefault()
                            void applyAction("custom", customPromptText.trim())
                            setCustomPromptText("")
                          }
                        }}
                        className="min-h-0 flex-1 resize-none bg-transparent py-3 pr-2 text-[12.5px] leading-5 text-foreground placeholder:text-muted-foreground/60 focus:outline-none disabled:cursor-not-allowed"
                      />
                      <button
                        type="button"
                        disabled={!selection || !customPromptText.trim() || !!applyingAction}
                        onClick={() => {
                          void applyAction("custom", customPromptText.trim())
                          setCustomPromptText("")
                        }}
                        className="mr-2 mt-2.5 flex size-7 shrink-0 items-center justify-center rounded-lg bg-primary text-white shadow-sm transition-all hover:bg-primary/90 disabled:cursor-not-allowed disabled:opacity-30"
                      >
                        {applyingAction === "custom"
                          ? <Loader2 className="size-3.5 animate-spin" />
                          : <Send className="size-3.5" />
                        }
                      </button>
                    </div>
                    <p className="mt-1.5 text-[10px] text-muted-foreground/60">
                      <kbd className="rounded bg-muted px-1 font-mono text-[9px]">⌘ Enter</kbd> 또는 버튼으로 적용 · RAG 경험 팩트 + 공고 분석 자동 주입
                    </p>
                  </div>
                </div>
              )}

              {/* 편집기 푸터 힌트 */}
              <div className="shrink-0 border-t border-border/60 bg-muted/10 px-5 py-2">
                <p className="text-[11px] text-muted-foreground">
                  <span className="font-semibold text-foreground">Tip:</span> 문장 단위로 드래그 → 왼쪽 액션 또는 하단 직접 지시로 정밀하게 다듬으세요.
                </p>
              </div>
            </div>

            {/* ── 오른쪽: 원본 초안 + 세탁본 비교 ── */}
            <div className={`flex flex-col overflow-hidden rounded-2xl border border-border bg-background transition-opacity duration-200 ${focusMode ? "hidden" : ""}`}>

              <div className="shrink-0 border-b border-border bg-muted/10 px-4 py-3">
                <div className="flex items-center justify-between">
                  <div className="flex items-center gap-2">
                    <Eye className="size-4 text-muted-foreground" />
                    <span className="text-sm font-semibold">초안 비교</span>
                  </div>
                  <div className="flex items-center gap-1.5">
                    <Lock className="size-3 text-muted-foreground" />
                    <span className="text-[10px] text-muted-foreground">읽기 전용</span>
                  </div>
                </div>
              </div>

              {/* 원본 초안 — 상단 */}
              <div className="flex min-h-0 flex-1 flex-col overflow-hidden border-b border-border">
                <div className="shrink-0 flex items-center justify-between px-4 py-2.5 bg-accent/50 border-b border-accent/70">
                  <div className="flex items-center gap-2">
                    <span className="size-2 rounded-full bg-accent-foreground/70 inline-block" />
                    <span className="text-[11px] font-bold uppercase tracking-wider text-accent-foreground">원본 초안</span>
                    <Badge className="rounded-full text-[9px] px-1.5 py-0 h-4 bg-accent text-accent-foreground border-accent/70 hover:bg-accent">
                      Original
                    </Badge>
                  </div>
                  <span className="text-[10px] tabular-nums font-semibold text-accent-foreground">
                    {countChars(normalizeDisplayText(data.originalDraft ?? "")).toLocaleString()}자
                  </span>
                </div>
                <div className="min-h-0 flex-1 overflow-y-auto overscroll-contain">
                  <div className="px-4 py-3.5">
                    <HighlightedText
                      text={normalizeDisplayText(data.originalDraft ?? "")}
                      activeParagraphIdx={activeParagraphIdx}
                      activeSentenceText={activeSentenceText}
                      className="text-[12.5px] leading-7 text-foreground/70 select-text cursor-text"
                    />
                  </div>
                </div>
              </div>

              {/* 세탁본 — 하단 */}
              <div className="flex min-h-0 flex-1 flex-col overflow-hidden">
                <div className="shrink-0 flex items-center justify-between px-4 py-2.5 bg-primary/8 border-b border-primary/15">
                  <div className="flex items-center gap-2">
                    <span className="size-2 rounded-full bg-primary inline-block" />
                    <span className="text-[11px] font-bold uppercase tracking-wider text-primary">세탁본</span>
                    <Badge className="rounded-full text-[9px] px-1.5 py-0 h-4 bg-primary/15 text-primary border-primary/30 hover:bg-primary/15">
                      Human Patch
                    </Badge>
                  </div>
                  <span className="text-[10px] tabular-nums font-semibold text-primary">
                    {countChars(normalizeDisplayText(data.washedDraft ?? "")).toLocaleString()}자
                  </span>
                </div>
                <div className="min-h-0 flex-1 overflow-y-auto overscroll-contain">
                  <div className="px-4 py-3.5">
                    <HighlightedText
                      text={normalizeDisplayText(data.washedDraft ?? "")}
                      activeParagraphIdx={activeParagraphIdx}
                      activeSentenceText={activeSentenceText}
                      className="text-[12.5px] leading-7 text-foreground select-none"
                    />
                  </div>
                </div>
              </div>

            </div>
          </main>
        </div>
      </SidebarInset>
    </SidebarProvider>
  )
}
