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

  const sourceTitle = getLeadingBracketTitle(sourceText);
  if (!sourceTitle) {
    return taggedHtml;
  }

  const plainTagged = taggedHtml.replace(/<mark\b[^>]*>/g, "").replace(/<\/mark>/g, "");
  const plainTaggedTrimmed = plainTagged.trimStart();
  const leadingPlainOffset = plainTagged.length - plainTaggedTrimmed.length;
  const taggedTitle = getLeadingBracketTitle(plainTaggedTrimmed);
  const escapedTitle = escapeHtml(sourceTitle.title);

  if (plainTaggedTrimmed.startsWith(sourceTitle.title)) {
    return taggedHtml;
  }

  if (taggedTitle) {
    return replacePlainTextRangeWithHtml(
      taggedHtml,
      leadingPlainOffset + taggedTitle.start,
      leadingPlainOffset + taggedTitle.end,
      escapedTitle
    );
  }

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
  const sanitizedWashed = sanitizeWashedText(washedKr);
  if (sanitizedWashed) {
    return sanitizedWashed;
  }

  if (!taggedWashedText) {
    return "";
  }

  return htmlToPlainText(ensureTaggedTitle(washedKr || "", taggedWashedText));
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

function getLeadingBracketTitle(text: string) {
  const titleMatch = text.match(/^\s*(\[[^\]\n]+\])/);
  if (!titleMatch) {
    return null;
  }

  const title = titleMatch[1];
  const fullMatch = titleMatch[0];
  const start = fullMatch.length - title.length;

  return {
    title,
    start,
    end: start + title.length,
  };
}

function replacePlainTextRangeWithHtml(
  html: string,
  start: number,
  end: number,
  replacementHtml: string
) {
  let plainIndex = 0;
  let htmlStart = -1;
  let htmlEnd = -1;
  let insideTag = false;

  for (let index = 0; index < html.length; index++) {
    const char = html[index];

    if (char === "<") {
      insideTag = true;
      continue;
    }

    if (insideTag) {
      if (char === ">") {
        insideTag = false;
      }
      continue;
    }

    if (plainIndex === start && htmlStart === -1) {
      htmlStart = index;
    }

    plainIndex += 1;
    if (plainIndex === end) {
      htmlEnd = index + 1;
      break;
    }
  }

  if (htmlStart < 0 || htmlEnd < 0) {
    return html;
  }

  htmlStart = expandRangeStartToMarkBoundary(html, htmlStart);
  htmlEnd = expandRangeEndToMarkBoundary(html, htmlEnd);

  return `${html.slice(0, htmlStart)}${replacementHtml}${html.slice(htmlEnd)}`;
}

function expandRangeStartToMarkBoundary(html: string, start: number) {
  let cursor = start;

  while (cursor > 0 && html[cursor - 1] === ">") {
    const tagStart = html.lastIndexOf("<", cursor - 1);
    if (tagStart < 0) {
      break;
    }

    const tag = html.slice(tagStart, cursor);
    if (!/^<\/?mark\b/i.test(tag)) {
      break;
    }

    cursor = tagStart;
  }

  return cursor;
}

function expandRangeEndToMarkBoundary(html: string, end: number) {
  let cursor = end;

  while (cursor < html.length && html[cursor] === "<") {
    const tagEnd = html.indexOf(">", cursor);
    if (tagEnd < 0) {
      break;
    }

    const tag = html.slice(cursor, tagEnd + 1);
    if (!/^<\/?mark\b/i.test(tag)) {
      break;
    }

    cursor = tagEnd + 1;
  }

  return cursor;
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
