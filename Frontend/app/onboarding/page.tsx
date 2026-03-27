"use client"

import { useState, useEffect, useCallback } from "react"
import { useRouter } from "next/navigation"
import {
  ArrowRight, ArrowLeft, Archive, Building2, Sparkles,
  Languages, Highlighter, CheckCheck, ChevronRight,
  FileText, Zap, UploadCloud, Search, Plus,
  Loader2, Check,
} from "lucide-react"
import { Button } from "@/components/ui/button"
import { Badge } from "@/components/ui/badge"
import { cn } from "@/lib/utils"

// ─────────────────────────────────────────────────────────
// 스텝 정의
// ─────────────────────────────────────────────────────────
const STEPS = [
  {
    id: 1,
    icon: Archive,
    color: "text-violet-500",
    bg: "bg-violet-50 dark:bg-violet-950/40",
    dotColor: "bg-violet-400",
    label: "경험 등록",
    headline: "내 경험을 AI에게 먹입니다",
    desc: "과거 프로젝트, 인턴, 수상 이력을 .md 또는 .json 파일로 업로드하세요. 자소서 작성 시 가장 관련 깊은 경험을 자동으로 꺼내 씁니다.",
    features: ["파일 한 번 업로드로 전 지원서에 재사용", "AI가 카테고리를 자동 분류", "작성 시 RAG로 최적 경험 자동 선별"],
    tip: "한 번만 올리면 모든 지원서에 재사용됩니다.",
  },
  {
    id: 2,
    icon: Building2,
    color: "text-blue-500",
    bg: "bg-blue-50 dark:bg-blue-950/40",
    dotColor: "bg-blue-400",
    label: "공고 등록",
    headline: "JD를 붙여넣으면 AI가 읽습니다",
    desc: "지원할 공고의 직무기술서(JD)를 붙여넣거나 이미지·PDF로 업로드하세요. AI가 핵심 기술 스택과 요구 역량을 자동으로 태깅합니다.",
    features: ["텍스트 붙여넣기, 이미지, PDF 모두 지원", "기술 스택·역량 키워드 자동 태깅", "칸반 보드에서 지원 현황 관리"],
    tip: "OCR을 지원해 캡처 이미지도 분석합니다.",
  },
  {
    id: 3,
    icon: Sparkles,
    color: "text-amber-500",
    bg: "bg-amber-50 dark:bg-amber-950/40",
    dotColor: "bg-amber-400",
    label: "초안 생성",
    headline: "경험 + JD = AI 초안",
    desc: "등록된 경험 중 이 공고에 가장 적합한 것을 RAG로 선별하고, GPT가 맞춤형 초안을 실시간 스트리밍으로 생성합니다.",
    features: ["RAG로 관련 경험 유사도 순 자동 선별", "GPT-4o 실시간 스트리밍 생성", "여러 문항 전략 계획 후 배치 처리"],
    tip: "문항마다 별도 전략 지시문을 줄 수 있습니다.",
  },
  {
    id: 4,
    icon: Languages,
    color: "text-emerald-500",
    bg: "bg-emerald-50 dark:bg-emerald-950/40",
    dotColor: "bg-emerald-400",
    label: "세탁",
    headline: "한→영→한 파이프라인으로 AI 냄새 제거",
    desc: "초안을 DeepL로 영어 번역한 뒤 다시 한국어로 번역합니다. 이 과정에서 기계적인 문장 구조가 자연스러운 문체로 바뀝니다.",
    features: ["DeepL로 한→영 번역", "Google Cloud로 영→한 역번역", "LLM으로 자연스러움 최종 검수"],
    tip: "AI 탐지기를 우회하는 RESUMADE의 핵심 기술입니다.",
  },
  {
    id: 5,
    icon: Highlighter,
    color: "text-orange-500",
    bg: "bg-orange-50 dark:bg-orange-950/40",
    dotColor: "bg-orange-400",
    label: "하이라이팅",
    headline: "오역을 색깔로 찾아줍니다",
    desc: "번역 과정에서 발생한 IT 도메인 오역을 자동 감지해 인라인 하이라이팅으로 표시합니다. 예: 'transaction' → '거래' 같은 오역.",
    features: ["IT 도메인 전문 용어 오역 자동 감지", "붉은색 = 명백한 오역, 노란색 = 어색한 표현", "LLM 기반 교정 제안 자동 표시"],
    tip: "원문과 세탁본을 나란히 놓고 비교할 수 있습니다.",
  },
  {
    id: 6,
    icon: CheckCheck,
    color: "text-primary",
    bg: "bg-primary/5",
    dotColor: "bg-primary",
    label: "최종 수정",
    headline: "10가지 AI 액션으로 다듬고 완성",
    desc: "최종 에디터에서 텍스트를 직접 편집하거나, '간결하게', '임팩트 강화', '수치 추가' 등 AI 액션을 적용해 완성도를 높이세요.",
    features: ["간결하게, 임팩트 강화, 수치 추가 등 10가지 액션", "1.5초 디바운스 자동 저장", "완성된 문항은 ✓ 체크로 일괄 관리"],
    tip: "버전 히스토리로 이전 상태를 언제든 복원할 수 있습니다.",
  },
]

// ─────────────────────────────────────────────────────────
// 목업 컴포넌트들
// ─────────────────────────────────────────────────────────
function MockVault() {
  const files = ["2024_쇼핑몰_백엔드_API.md", "2023_React_리팩토링.md"]
  const tags = [
    { label: "백엔드", color: "bg-blue-100 text-blue-700 dark:bg-blue-900 dark:text-blue-300" },
    { label: "프론트엔드", color: "bg-violet-100 text-violet-700 dark:bg-violet-900 dark:text-violet-300" },
  ]
  return (
    <div className="space-y-2.5">
      <div className="rounded-xl border-2 border-dashed border-violet-300 dark:border-violet-700 bg-violet-50/50 dark:bg-violet-950/30 p-4 flex flex-col items-center gap-1.5">
        <UploadCloud className="size-6 text-violet-400" />
        <p className="text-xs font-medium text-violet-600 dark:text-violet-300">.md / .json 드래그 업로드</p>
      </div>
      <div className="space-y-1.5">
        {files.map((f, i) => (
          <div key={i} className="flex items-center gap-2 rounded-lg border bg-background px-3 py-2 text-xs">
            <FileText className="size-3.5 text-violet-400 shrink-0" />
            <span className="flex-1 font-medium truncate">{f}</span>
            <Badge className={cn("text-[10px]", tags[i].color)}>{tags[i].label}</Badge>
            <Check className="size-3.5 text-emerald-500 shrink-0" />
          </div>
        ))}
      </div>
      <div className="rounded-lg bg-muted/50 px-3 py-2 flex items-center gap-2 text-xs text-muted-foreground">
        <Search className="size-3.5 shrink-0" />
        <span>자소서 작성 시 관련 경험 자동 선별</span>
      </div>
    </div>
  )
}

function MockJobPost() {
  return (
    <div className="rounded-xl border bg-background p-3.5 space-y-2.5">
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-2">
          <div className="flex size-7 items-center justify-center rounded-lg bg-blue-100 dark:bg-blue-900 text-xs font-bold text-blue-600 dark:text-blue-300">K</div>
          <div>
            <p className="text-sm font-semibold leading-tight">카카오</p>
            <p className="text-xs text-muted-foreground">백엔드 엔지니어</p>
          </div>
        </div>
        <Badge variant="secondary" className="text-xs">서류 전형</Badge>
      </div>
      <div className="pt-1.5 border-t space-y-1.5">
        <p className="text-[10px] font-semibold text-muted-foreground flex items-center gap-1">
          <Sparkles className="size-3 text-amber-400" /> AI 분석 기술 스택
        </p>
        <div className="flex flex-wrap gap-1">
          {["Java/Kotlin", "Spring Boot", "MySQL", "Redis", "Kafka"].map(t => (
            <span key={t} className="rounded-md bg-muted px-1.5 py-0.5 text-[10px] font-medium">{t}</span>
          ))}
        </div>
      </div>
      <div className="pt-1.5 border-t space-y-1">
        <p className="text-[10px] font-semibold text-muted-foreground">자소서 문항 3개</p>
        {["지원 동기 (700자)", "직무 역량 (1000자)", "입사 후 포부 (500자)"].map((q, i) => (
          <div key={i} className="flex items-center gap-1.5 text-xs text-muted-foreground">
            <div className="size-1.5 rounded-full bg-muted-foreground/40" />
            {q}
          </div>
        ))}
      </div>
    </div>
  )
}

function MockDraftGen() {
  const [tick, setTick] = useState(0)
  useEffect(() => {
    const id = setInterval(() => setTick(t => (t + 1) % 4), 600)
    return () => clearInterval(id)
  }, [])
  const lines = [
    "저는 카카오의 대규모 트래픽 처리 경험에 매력을 느껴",
    "지원하게 되었습니다. 쇼핑몰 프로젝트에서 Redis를",
    "활용해 일일 100만 요청을 처리하는 시스템을 구축했습니다.",
  ]
  return (
    <div className="space-y-2.5">
      <div className="rounded-lg border bg-amber-50/60 dark:bg-amber-950/30 px-3 py-2 space-y-1.5">
        <p className="text-[10px] font-semibold text-amber-700 dark:text-amber-300 flex items-center gap-1">
          <Search className="size-3" /> RAG — 관련 경험 선별
        </p>
        <div className="flex flex-wrap gap-1">
          {["쇼핑몰 백엔드 API", "Redis 캐싱"].map(e => (
            <span key={e} className="rounded-md bg-amber-100 dark:bg-amber-900 px-1.5 py-0.5 text-[10px] text-amber-700 dark:text-amber-300">{e}</span>
          ))}
        </div>
      </div>
      <div className="rounded-xl border bg-background p-3.5 space-y-2">
        <div className="flex items-center gap-2 text-xs text-amber-500 font-medium">
          <Loader2 className="size-3.5 animate-spin" />
          GPT-4o 초안 생성 중...
        </div>
        <div className="space-y-1 text-xs leading-relaxed">
          {lines.map((line, i) => (
            <p key={i} className="transition-opacity duration-500" style={{ opacity: tick > i ? 1 : 0.2 }}>{line}</p>
          ))}
          <span className="inline-block w-1.5 h-3.5 bg-amber-400 animate-pulse rounded-sm" />
        </div>
      </div>
    </div>
  )
}

function MockWash() {
  return (
    <div className="space-y-2.5">
      <div className="grid grid-cols-2 gap-2">
        <div className="rounded-xl border bg-muted/30 p-3 space-y-1.5">
          <p className="text-[10px] font-semibold text-muted-foreground flex items-center gap-1"><FileText className="size-3" /> AI 원문</p>
          <p className="text-xs leading-relaxed text-muted-foreground">저는 이 직무에 필요한 역량을 보유하고 있으며, 팀의 성과 달성에 기여할 수 있다고 확신합니다.</p>
        </div>
        <div className="rounded-xl border border-emerald-200 dark:border-emerald-800 bg-emerald-50/60 dark:bg-emerald-950/30 p-3 space-y-1.5">
          <p className="text-[10px] font-semibold text-emerald-600 flex items-center gap-1"><Languages className="size-3" /> 세탁본</p>
          <p className="text-xs leading-relaxed">이 업무에서 요구하는 역량을 갖추고 있으며, 팀이 목표를 이루는 데 실질적인 힘이 되고 싶습니다.</p>
        </div>
      </div>
      <div className="rounded-lg bg-muted/40 px-3 py-2 flex items-center justify-center gap-1 text-xs text-muted-foreground flex-wrap">
        <span className="font-medium text-foreground">한국어</span>
        <ChevronRight className="size-3" />
        <span>DeepL 영어</span>
        <ChevronRight className="size-3" />
        <span>Google 한국어</span>
        <ChevronRight className="size-3" />
        <span className="font-medium text-emerald-600">LLM 검수</span>
      </div>
    </div>
  )
}

function MockHighlight() {
  return (
    <div className="rounded-xl border bg-background p-3.5 space-y-2">
      <div className="flex items-center gap-2 text-xs font-medium">
        <Highlighter className="size-3.5 text-orange-500" />
        <span>오역 감지 완료 — 2개 발견</span>
      </div>
      <p className="text-xs leading-7">
        데이터베이스{" "}
        <mark className="bg-red-100 dark:bg-red-900/50 text-red-700 dark:text-red-300 px-1 rounded">거래 처리</mark>
        {" "}과정에서 동시성 이슈를 해결하기 위해{" "}
        <mark className="bg-yellow-100 dark:bg-yellow-900/50 text-yellow-700 dark:text-yellow-300 px-1 rounded">락 기법</mark>
        {" "}을 사용하여 무결성을 보장했습니다.
      </p>
      <div className="pt-2 border-t space-y-1.5">
        <div className="flex items-start gap-2 text-xs">
          <span className="mt-0.5 shrink-0 rounded px-1 py-0.5 bg-red-100 text-red-600 font-medium text-[10px]">오역</span>
          <span className="text-muted-foreground"><b className="text-foreground">'거래 처리'</b> → 트랜잭션 처리</span>
        </div>
        <div className="flex items-start gap-2 text-xs">
          <span className="mt-0.5 shrink-0 rounded px-1 py-0.5 bg-yellow-100 text-yellow-600 font-medium text-[10px]">어색</span>
          <span className="text-muted-foreground"><b className="text-foreground">'락 기법'</b> → Lock 메커니즘</span>
        </div>
      </div>
    </div>
  )
}

function MockFinalEditor() {
  const actions = ["간결하게", "임팩트 강화", "수치 추가", "능동태로", "자신감 있게"]
  return (
    <div className="rounded-xl border bg-background p-3.5 space-y-2.5">
      <div className="flex flex-wrap gap-1.5">
        {actions.map((a, i) => (
          <button key={i} className="rounded-lg border bg-muted/50 px-2 py-1 text-[11px] font-medium hover:bg-primary/10 hover:border-primary/30 hover:text-primary transition-colors">
            {a}
          </button>
        ))}
      </div>
      <textarea
        readOnly
        className="w-full resize-none rounded-lg border bg-muted/20 px-3 py-2 text-xs leading-relaxed outline-none"
        rows={3}
        value={"트랜잭션 처리 과정의 동시성 이슈를 Lock 메커니즘으로 해결했습니다. 그 결과 처리 오류율을 94% 감소시켰습니다."}
      />
      <div className="flex items-center justify-between text-[10px] text-muted-foreground border-t pt-2">
        <span>127 / 700자</span>
        <div className="flex items-center gap-1">
          <Check className="size-3 text-emerald-500" />
          <span className="text-emerald-600 font-medium">자동 저장됨</span>
        </div>
      </div>
    </div>
  )
}

const MOCKS = [MockVault, MockJobPost, MockDraftGen, MockWash, MockHighlight, MockFinalEditor]

// ─────────────────────────────────────────────────────────
// 메인
// ─────────────────────────────────────────────────────────
export default function OnboardingPage() {
  const router = useRouter()
  const [current, setCurrent] = useState(0)

  const step = STEPS[current]
  const MockUI = MOCKS[current]
  const isLast = current === STEPS.length - 1

  const next = useCallback(() => {
    if (isLast) router.push("/")
    else setCurrent(c => c + 1)
  }, [isLast, router])

  const prev = useCallback(() => {
    if (current > 0) setCurrent(c => c - 1)
  }, [current])

  useEffect(() => {
    function onKey(e: KeyboardEvent) {
      if (e.key === "ArrowRight" || e.key === "Enter") next()
      if (e.key === "ArrowLeft") prev()
    }
    window.addEventListener("keydown", onKey)
    return () => window.removeEventListener("keydown", onKey)
  }, [next, prev])

  // 파이프라인: 3개씩 2줄
  const pipelineRows = [STEPS.slice(0, 3), STEPS.slice(3, 6)]

  return (
    <div className="min-h-screen bg-gradient-to-br from-background via-muted/10 to-background flex flex-col items-center justify-center px-4 py-6">
      {/* 로고 + 건너뛰기 */}
      <div className="w-full max-w-3xl flex items-center justify-between mb-4">
        <span className="text-lg font-bold tracking-tight text-primary">RESUMADE</span>
        <button onClick={() => router.push("/")} className="text-xs text-muted-foreground hover:text-foreground transition-colors">
          건너뛰기
        </button>
      </div>

      {/* 메인 카드 */}
      <div className="w-full max-w-3xl rounded-2xl border bg-background shadow-lg overflow-hidden">

        {/* ── 카드 헤더: 프로그레스 + 스텝 라벨 ── */}
        <div className="px-6 pt-5 pb-4 border-b">
          <div className="flex items-center justify-between">
            {/* 스텝 도트 */}
            <div className="flex items-center gap-1.5">
              {STEPS.map((s, i) => (
                <button key={i} onClick={() => setCurrent(i)} className="group flex items-center">
                  <div className={cn(
                    "rounded-full transition-all duration-300",
                    i === current
                      ? cn("w-6 h-2", s.dotColor)
                      : i < current
                      ? "w-2 h-2 bg-muted-foreground/40"
                      : "w-2 h-2 bg-muted"
                  )} />
                </button>
              ))}
              <span className="ml-2 text-xs font-medium text-muted-foreground tabular-nums">
                {current + 1} / {STEPS.length}
              </span>
            </div>
            {/* 현재 스텝 라벨 */}
            <div className={cn("flex items-center gap-1.5 rounded-full px-3 py-1 text-xs font-semibold", step.bg, step.color)}>
              <step.icon className="size-3.5" />
              {step.label}
            </div>
          </div>
        </div>

        {/* ── 카드 본문: 설명 + 목업 ── */}
        <div
          key={current}
          className="grid grid-cols-2 gap-6 px-6 py-5 animate-in fade-in-0 slide-in-from-right-3 duration-300"
        >
          {/* 왼쪽: 텍스트 */}
          <div className="flex flex-col gap-3">
            <h1 className="text-2xl font-bold tracking-tight leading-snug whitespace-pre-line">
              {step.headline}
            </h1>
            <p className="text-sm text-muted-foreground leading-relaxed">
              {step.desc}
            </p>
            {/* 특징 리스트 */}
            <ul className="space-y-1.5">
              {step.features.map((f, i) => (
                <li key={i} className="flex items-start gap-2 text-sm">
                  <span className={cn("mt-1 size-1.5 rounded-full shrink-0", step.dotColor)} />
                  <span className="text-foreground/80">{f}</span>
                </li>
              ))}
            </ul>
            {/* 팁 */}
            <div className={cn("flex items-start gap-2 rounded-xl px-3 py-2.5 mt-1", step.bg)}>
              <Zap className={cn("size-3.5 shrink-0 mt-0.5", step.color)} />
              <p className={cn("text-xs font-medium leading-relaxed", step.color)}>{step.tip}</p>
            </div>
          </div>

          {/* 오른쪽: 목업 */}
          <div className={cn("rounded-xl p-4", step.bg)}>
            <div className="rounded-lg bg-background/80 border p-3 shadow-sm">
              <MockUI />
            </div>
          </div>
        </div>

        {/* ── 파이프라인 (3×2 그리드) ── */}
        <div className="px-6 pb-4">
          <div className="rounded-xl bg-muted/40 px-4 py-3 space-y-1.5">
            {pipelineRows.map((row, rowIdx) => (
              <div key={rowIdx} className="grid grid-cols-3 gap-x-1 items-center">
                {row.map((s, colIdx) => {
                  const globalIdx = rowIdx * 3 + colIdx
                  const isActive = globalIdx === current
                  const isDone = globalIdx < current
                  return (
                    <button
                      key={s.id}
                      onClick={() => setCurrent(globalIdx)}
                      className={cn(
                        "flex items-center gap-1 rounded-lg px-2.5 py-1.5 text-xs font-medium transition-all duration-200 w-full",
                        isActive
                          ? cn("text-background", s.dotColor.replace("bg-", "bg-"))
                          : isDone
                          ? "text-muted-foreground/60 line-through"
                          : "text-muted-foreground hover:text-foreground hover:bg-muted"
                      )}
                    >
                      <span className={cn(
                        "flex size-4 shrink-0 items-center justify-center rounded-full text-[10px] font-bold",
                        isActive ? "bg-white/30" : isDone ? "bg-muted" : "bg-muted"
                      )}>
                        {isDone ? <Check className="size-2.5" /> : s.id}
                      </span>
                      {s.label}
                    </button>
                  )
                })}
              </div>
            ))}
          </div>
        </div>

        {/* ── 네비게이션 버튼 (카드 하단 중앙 정렬) ── */}
        <div className="flex items-center justify-between px-6 pb-5 gap-3">
          <Button
            variant="outline"
            onClick={prev}
            disabled={current === 0}
            className={cn("gap-1.5", current === 0 && "opacity-0 pointer-events-none")}
          >
            <ArrowLeft className="size-4" />
            이전
          </Button>

          <Button onClick={next} className="gap-1.5 px-5">
            {isLast ? "RESUMADE 시작하기" : "다음"}
            <ArrowRight className="size-4" />
          </Button>
        </div>
      </div>

    </div>
  )
}
