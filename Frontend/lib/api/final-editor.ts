import { toApiUrl } from "@/lib/network/api-base"

// ── 타입 정의 ──────────────────────────────────────────────────────────────

export interface FinalEditorData {
  questionId: number
  questionText: string
  maxLength: number
  originalDraft: string
  washedDraft: string
  finalText: string | null
  savedFinalText?: string | null
  selectedTitle: string | null
  companyName: string | null
  position: string | null
  applicationId: number | null
  updatedAt: string | null
  titleCandidates?: TitleCandidate[]
}

export interface FinalSaveResponse {
  updatedAt: string
}

export interface TitleCandidate {
  title: string
  score: number
  reason: string
  recommended: boolean
}

export interface TitleSuggestionData {
  currentTitle: string
  candidates: TitleCandidate[]
}

export interface EditActionResult {
  transformedText: string
}

export interface SpellCorrection {
  errorWord: string
  suggestedWord: string
  reason: string
}

export interface SpellCheckResult {
  corrections: SpellCorrection[]
}

export interface QuestionNavItem {
  id: number
  index: number
  title: string
  hasWashedDraft: boolean
}

// ── API 함수 ───────────────────────────────────────────────────────────────

/** 최종 편집기 초기 데이터 로드 */
export async function fetchFinalEditorData(questionId: number): Promise<FinalEditorData> {
  const res = await fetch(toApiUrl(`/api/v1/workspace/final/${questionId}`))
  if (!res.ok) throw new Error(`Failed to load final editor data: ${res.status}`)
  return res.json()
}

/** 최종 텍스트 자동저장 */
export async function saveFinalText(
  questionId: number,
  finalText: string,
  selectedTitle?: string | null
): Promise<FinalSaveResponse> {
  const res = await fetch(toApiUrl(`/api/v1/workspace/final/${questionId}/save`), {
    method: "PATCH",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ finalText, selectedTitle }),
  })
  if (!res.ok) throw new Error(`Failed to save: ${res.status}`)
  return res.json()
}

/** AI 제목 추천 — 기존 workspace 엔드포인트 재사용 */
export async function fetchTitleSuggestions(questionId: number): Promise<TitleSuggestionData> {
  const res = await fetch(toApiUrl(`/api/workspace/title-suggestions/${questionId}`))
  if (!res.ok) throw new Error(`Failed to fetch title suggestions: ${res.status}`)
  return res.json()
}

/** AI 제목 추천 — 저장 전 본문을 기준으로 후보 생성 */
export async function fetchTitleSuggestionsForDraft(
  questionId: number,
  draftText: string
): Promise<TitleSuggestionData> {
  const res = await fetch(toApiUrl(`/api/workspace/title-suggestions/${questionId}`), {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ draftText }),
  })
  if (!res.ok) throw new Error(`Failed to fetch title suggestions: ${res.status}`)
  return res.json()
}

/** 동일 Application 내 형제 문항 목록 — 네비게이터용 */
export async function fetchSiblingQuestions(questionId: number): Promise<QuestionNavItem[]> {
  const res = await fetch(toApiUrl(`/api/v1/workspace/final/${questionId}/siblings`))
  if (!res.ok) throw new Error(`Failed to fetch siblings: ${res.status}`)
  return res.json()
}

/** 선택 텍스트 세탁 — 한→영→한 번역 왕복 */
export async function rewashSelection(
  questionId: number,
  selectedText: string
): Promise<{ washedText: string }> {
  const res = await fetch(toApiUrl(`/api/v1/workspace/final/${questionId}/rewash-selection`), {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ selectedText }),
  })
  if (!res.ok) throw new Error(`Rewash failed: ${res.status}`)
  return res.json()
}

/** 맞춤법 검사 — 현재 에디터 텍스트 전체의 오류 제안 목록 반환 */
export async function checkSpelling(questionId: number, text: string): Promise<SpellCheckResult> {
  const res = await fetch(toApiUrl(`/api/v1/workspace/final/${questionId}/spell-check`), {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ text }),
  })
  if (!res.ok) throw new Error(`Spell check failed: ${res.status}`)
  return res.json()
}

/** AI 편집 액션 적용 */
export async function applyEditAction(
  questionId: number,
  selectedText: string,
  actionKey: string,
  customPrompt?: string
): Promise<EditActionResult> {
  const res = await fetch(toApiUrl(`/api/v1/workspace/final/${questionId}/edit-action`), {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ selectedText, actionKey, customPrompt }),
  })
  if (!res.ok) throw new Error(`Edit action failed: ${res.status}`)
  return res.json()
}
