import type { Mistranslation, PipelineStage } from "@/lib/workspace/types";

export type TranslationProcessingTarget = "draft" | "washed" | null;

export function getTranslationProcessingMeta(
  isProcessing: boolean,
  pipelineStage: PipelineStage
) {
  if (!isProcessing) {
    return { target: null as TranslationProcessingTarget, label: "" };
  }

  if (pipelineStage === "ANALYSIS" || pipelineStage === "RAG" || pipelineStage === "DRAFT") {
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
 * 세탁본(washedKr) 텍스트에서 mistranslations의 translated 단어를 찾아 <mark> 하이라이팅.
 * 같은 단어가 여러 번 등장하면 first-unused-occurrence 선택.
 */
export function highlightByPhraseMatching(
  text: string,
  mistranslations: Mistranslation[],
  hoveredMistranslationId: string | null,
): string {
  if (!text || !mistranslations.length) return escapeHtml(text || "")

  const hasAnyHover = hoveredMistranslationId !== null

  type MatchInfo = { start: number; end: number; mis: Mistranslation }
  const matches: MatchInfo[] = []

  for (const mis of mistranslations) {
    const phrase = mis.translated || ""
    if (!phrase.trim()) continue

    const match = findPhraseMatch(text, phrase, matches)
    if (match) {
      matches.push({ start: match.start, end: match.end, mis })
    }
  }

  if (matches.length === 0) return escapeHtml(text)

  matches.sort((a, b) => a.start - b.start)

  let result = ""
  let cursor = 0

  for (const m of matches) {
    result += escapeHtml(text.slice(cursor, m.start))

    const isHovered = hoveredMistranslationId === m.mis.id
    const isCritical = m.mis.severity === "CRITICAL"

    const baseClass = isCritical
      ? "bg-red-500/15 text-red-600 dark:text-red-400 ring-1 ring-red-500/40"
      : "bg-amber-500/15 text-amber-600 dark:text-amber-400 ring-1 ring-amber-500/40"

    const hoverClass = isHovered
      ? `ring-2 scale-105 shadow-md z-[50] opacity-100 ${isCritical ? "ring-red-500 bg-red-500/25" : "ring-amber-500 bg-amber-500/25"}`
      : hasAnyHover
        ? "opacity-25 saturate-0 scale-95"
        : ""

    const title = buildHighlightTitle(m.mis)

    result += `<mark data-mis-id="${escapeAttr(m.mis.id)}" title="${escapeAttr(title)}" class="${baseClass} ${hoverClass} px-0.5 rounded cursor-help transition-colors transition-transform transition-opacity duration-200 inline-block origin-center font-semibold">${escapeHtml(text.slice(m.start, m.end))}</mark>`
    cursor = m.end
  }

  result += escapeHtml(text.slice(cursor))
  return result
}

/**
 * phrase를 text에서 찾아 겹치지 않는 첫 번째 occurrence를 반환.
 * 같은 단어가 여러 번 등장할 때 이미 사용된 위치를 건너뛰고 다음 위치를 반환.
 */
function findPhraseMatch(
  text: string,
  phrase: string,
  existing: { start: number; end: number }[]
): { start: number; end: number } | null {
  if (!phrase) return null

  let searchFrom = 0
  while (searchFrom <= text.length - phrase.length) {
    const idx = text.indexOf(phrase, searchFrom)
    if (idx === -1) return null

    const end = idx + phrase.length
    const overlaps = existing.some(e => idx < e.end && end > e.start)
    if (!overlaps) return { start: idx, end }

    searchFrom = idx + 1
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
  if (pipelineStage === "ANALYSIS") return "문항 분석 중";
  if (pipelineStage === "RAG") return "경험 컨텍스트 준비 중";
  if (pipelineStage === "DRAFT") return "초안 다듬는 중";
  if (pipelineStage === "WASH") return "세탁본 생성 중";
  if (pipelineStage === "PATCH") return "휴먼 패치 분석 중";
  return "파이프라인 실행 중";
}

function buildHighlightTitle(mistranslation: Mistranslation) {
  const prefix = mistranslation.severity === "CRITICAL" ? "필수 교정" : "검토 권장";
  return `${prefix}${mistranslation.reason ? `: ${mistranslation.reason}` : ""}`;
}
