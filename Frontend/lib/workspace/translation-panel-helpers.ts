import type { Mistranslation, PipelineStage } from "@/lib/workspace/types";

export type TranslationProcessingTarget = "draft" | "washed" | null;

export function getTranslationProcessingMeta(
  isProcessing: boolean,
  pipelineStage: PipelineStage
) {
  if (!isProcessing) {
    return { target: null as TranslationProcessingTarget, label: "" };
  }

  if (pipelineStage === "RAG" || pipelineStage === "DRAFT") {
    return {
      target: "draft" as TranslationProcessingTarget,
      label: getPipelineLabel(pipelineStage),
    };
  }

  return {
    target: "washed" as TranslationProcessingTarget,
    label: getPipelineLabel(pipelineStage),
  };
}

/**
 * mistranslations 배열의 original/translated 필드를 텍스트에서 직접 검색하여 하이라이팅.
 * AI의 taggedText에 의존하지 않는 안정적인 방식.
 */
export function highlightByPhraseMatching(
  text: string,
  mistranslations: Mistranslation[],
  hoveredMistranslationId: string | null,
  isOriginal: boolean
): string {
  if (!text || !mistranslations.length) return escapeHtml(text || "")

  const hasAnyHover = hoveredMistranslationId !== null

  // 긴 문구부터 먼저 교체 (겹침 방지)
  const sorted = [...mistranslations].sort((a, b) => {
    const aPhrase = isOriginal ? (a.original || "") : (a.translated || "")
    const bPhrase = isOriginal ? (b.original || "") : (b.translated || "")
    return bPhrase.length - aPhrase.length
  })

  // 매칭 위치 수집 (겹침 방지용)
  type MatchInfo = { start: number; end: number; mis: Mistranslation }
  const matches: MatchInfo[] = []

  for (const mis of sorted) {
    const phrase = isOriginal ? (mis.original || "") : (mis.translated || "")
    if (!phrase.trim()) continue

    // 2단계: 백엔드가 세탁본에서 문구를 찾지 못했다고 명시한 경우 하이라이팅 스킵
    if (!isOriginal && mis.matchConfidence === 0) continue

    const match = findNonOverlappingMatch(
      text, phrase, matches,
      isOriginal ? mis.originalStartIndex : mis.startIndex,
      isOriginal ? mis.originalEndIndex : mis.endIndex
    )
    if (match) {
      matches.push({ start: match.start, end: match.end, mis })
    }
  }

  if (matches.length === 0) return escapeHtml(text)

  // 위치 순으로 정렬 후 HTML 조립
  matches.sort((a, b) => a.start - b.start)

  let result = ""
  let cursor = 0

  for (const m of matches) {
    result += escapeHtml(text.slice(cursor, m.start))

    const isHovered = hoveredMistranslationId === m.mis.id
    const isCritical = m.mis.severity === "CRITICAL"

    const severityClass = isOriginal
      ? "text-primary ring-1 ring-primary/50"
      : isCritical
        ? "bg-red-500/15 text-red-600 dark:text-red-400 ring-1 ring-red-500/40"
        : "bg-amber-500/15 text-amber-600 dark:text-amber-400 ring-1 ring-amber-500/40"

    const hoverClass = isHovered
      ? "ring-2 ring-primary scale-105 shadow-lg z-[50] opacity-100 " + (isOriginal ? "bg-primary/30" : (isCritical ? "bg-red-500/30" : "bg-amber-500/30"))
      : hasAnyHover
        ? "opacity-30 grayscale scale-95"
        : ""

    const title = buildHighlightTitle(m.mis, isOriginal)

    result += `<mark data-mis-id="${escapeAttr(m.mis.id)}" title="${escapeAttr(title)}" class="${severityClass} ${hoverClass} px-0.5 rounded cursor-help transition-all duration-300 inline-block origin-center font-semibold">${escapeHtml(text.slice(m.start, m.end))}</mark>`
    cursor = m.end
  }

  result += escapeHtml(text.slice(cursor))
  return result
}

/**
 * 공백 정규화(연속 공백→단일 공백) 후 phrase를 text에서 검색해 겹치지 않는 첫 위치를 반환.
 * hintStart/hintEnd가 있으면 백엔드가 계산한 오프셋을 우선 시도.
 * 실제 하이라이트 범위는 원본 text 기준으로 계산한다.
 */
function findNonOverlappingMatch(
  text: string,
  phrase: string,
  existing: { start: number; end: number }[],
  hintStart?: number | null,
  hintEnd?: number | null
): { start: number; end: number } | null {
  // 2단계: 백엔드 오프셋 힌트가 있으면 먼저 시도 (원본 텍스트 기준 위치)
  if (hintStart != null && hintEnd != null && hintEnd <= text.length && hintStart >= 0) {
    const sliceNorm = text.slice(hintStart, hintEnd).replace(/\s+/g, ' ').trim()
    const phraseNorm = phrase.replace(/\s+/g, ' ').trim()
    if (sliceNorm === phraseNorm) {
      const overlaps = existing.some(e => hintStart < e.end && hintEnd > e.start)
      if (!overlaps) return { start: hintStart, end: hintEnd }
    }
  }

  // 1단계: 공백 정규화 후 indexOf로 검색, 원본 인덱스로 역매핑
  const normalizedText = text.replace(/\s+/g, ' ')
  const normalizedPhrase = phrase.replace(/\s+/g, ' ').trim()
  if (!normalizedPhrase) return null

  // normToOrig[i] = normalized text의 i번째 문자가 original text의 몇 번째 문자인지
  const normToOrig: number[] = []
  let lastWasSpace = false
  for (let i = 0; i < text.length; i++) {
    const isSpace = /\s/.test(text[i])
    if (isSpace && lastWasSpace) continue
    normToOrig.push(i)
    lastWasSpace = isSpace
  }

  let searchFrom = 0
  while (searchFrom < normalizedText.length) {
    const normIdx = normalizedText.indexOf(normalizedPhrase, searchFrom)
    if (normIdx === -1) return null

    const origStart = normToOrig[normIdx] ?? -1
    if (origStart === -1) return null

    const normEnd = normIdx + normalizedPhrase.length
    const origEnd = normEnd < normToOrig.length ? normToOrig[normEnd] : text.length

    const overlaps = existing.some(e => origStart < e.end && origEnd > e.start)
    if (!overlaps) return { start: origStart, end: origEnd }

    searchFrom = normIdx + 1
  }
  return null
}

export function htmlToPlainText(html: string) {
  if (!html) {
    return "";
  }

  if (typeof window === "undefined") {
    return html.replace(/<[^>]+>/g, "");
  }

  const container = window.document.createElement("div");
  container.innerHTML = html;
  return container.textContent ?? container.innerText ?? "";
}

export function getDisplayedWashedText(washedKr: string) {
  return sanitizeWashedText(washedKr);
}

export function sanitizeWashedText(text: string) {
  if (!text) {
    return "";
  }

  const normalized = text.replace(/\r\n/g, "\n").replace(/\r/g, "\n").trim();
  const duplicateTitleBlock = normalized.match(
    /\n\s*\n(?:\*\*[^*\n]+\*\*\s*)?(?:\[[^\]\n]+\])/
  );

  if (duplicateTitleBlock && duplicateTitleBlock.index !== undefined) {
    const leadingContent = normalized.slice(0, duplicateTitleBlock.index).trim();
    if (leadingContent.length >= 180) {
      return leadingContent;
    }
  }

  return normalized;
}

export function escapeHtml(value: string) {
  return value
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;");
}

function escapeAttr(str: string): string {
  return str
    .replace(/&/g, "&amp;")
    .replace(/"/g, "&quot;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;");
}

function getPipelineLabel(pipelineStage: PipelineStage) {
  if (pipelineStage === "RAG") return "경험 컨텍스트 준비 중";
  if (pipelineStage === "DRAFT") return "초안 다듬는 중";
  if (pipelineStage === "WASH") return "세탁본 생성 중";
  if (pipelineStage === "PATCH") return "휴먼 패치 분석 중";
  return "파이프라인 실행 중";
}

function buildHighlightTitle(mistranslation: Mistranslation, isOriginal: boolean) {
  if (isOriginal) {
    return "";
  }

  const prefix = mistranslation.severity === "CRITICAL" ? "필수 교정" : "검토 권장";
  return `${prefix}${mistranslation.reason ? `: ${mistranslation.reason}` : ""}`;
}
