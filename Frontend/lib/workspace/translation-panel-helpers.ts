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

export function injectHighlightTags(
  html: string,
  mistranslations: Mistranslation[],
  hoveredMistranslationId: string | null,
  isOriginal: boolean
) {
  if (!html || !mistranslations.length) {
    return html;
  }

  const hasAnyHover = hoveredMistranslationId !== null;
  const mistranslationMap = new Map(
    mistranslations.map((mistranslation) => [mistranslation.id, mistranslation])
  );

  return html.replace(/<mark data-mis-id="([^"]+)">([\s\S]*?)<\/mark>/g, (match, misId, content) => {
    const mistranslation = mistranslationMap.get(misId);
    if (!mistranslation) {
      return match;
    }

    const isHovered = hoveredMistranslationId === misId;
    const isCritical = mistranslation.severity === "CRITICAL";
    const severityClass = isOriginal
      ? "text-primary hover:text-primary ring-1 ring-primary/50"
      : isCritical
        ? "bg-red-500/15 text-red-600 dark:text-red-400 ring-1 ring-red-500/40"
        : "bg-amber-500/15 text-amber-600 dark:text-amber-400 ring-1 ring-amber-500/40";

    return `<mark data-mis-id="${misId}" title="${buildHighlightTitle(
      mistranslation,
      isOriginal
    )}" class="${severityClass} ${buildHoverClasses(
      isOriginal,
      isHovered,
      hasAnyHover
    )} px-0.5 rounded cursor-help transition-all duration-300 inline-block origin-center font-semibold">${content}</mark>`;
  });
}

export function ensureTaggedTitle(sourceText: string, taggedHtml: string) {
  if (!sourceText || !taggedHtml) {
    return taggedHtml;
  }

  const titleMatch = sourceText.match(/^\s*(\[[^\]\n]+\])/);
  if (!titleMatch) {
    return taggedHtml;
  }

  const title = titleMatch[1];
  const plainTagged = taggedHtml.replace(/<mark\b[^>]*>/g, "").replace(/<\/mark>/g, "").trimStart();

  if (plainTagged.startsWith(title)) {
    return taggedHtml;
  }

  const escapedTitle = escapeHtml(title);
  return `${escapedTitle}\n\n${taggedHtml.trimStart()}`;
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

export function getDisplayedWashedText(
  washedKr: string,
  taggedWashedText?: string
) {
  if (!taggedWashedText) {
    return washedKr || "";
  }

  return htmlToPlainText(ensureTaggedTitle(washedKr || "", taggedWashedText));
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
  return escapeHtmlAttribute(
    `${prefix}${mistranslation.reason ? `: ${mistranslation.reason}` : ""}`
  );
}

function buildHoverClasses(
  isOriginal: boolean,
  isHovered: boolean,
  hasAnyHover: boolean
) {
  if (isOriginal) {
    if (isHovered) {
      return "ring-2 ring-primary scale-110 shadow-2xl z-[50] opacity-100 bg-primary/30";
    }

    return hasAnyHover
      ? "opacity-20 grayscale scale-95 blur-[0.5px] bg-primary/10"
      : "opacity-100 bg-primary/20 text-primary";
  }

  if (isHovered) {
    return "ring-2 ring-primary scale-110 shadow-2xl z-[50] opacity-100";
  }

  return hasAnyHover ? "opacity-20 grayscale scale-95 blur-[0.5px]" : "opacity-100";
}

function escapeHtmlAttribute(value: string) {
  return value
    .replace(/&/g, "&amp;")
    .replace(/"/g, "&quot;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;");
}

function escapeHtml(value: string) {
  return value
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;");
}
