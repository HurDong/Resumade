import type {
  AiReviewReport,
  Mistranslation,
  WorkspaceQuestion,
} from "@/lib/workspace/types";

export function shouldSyncQuestion(updates: Partial<WorkspaceQuestion>) {
  return (
    updates.title !== undefined ||
    updates.maxLength !== undefined ||
    updates.content !== undefined ||
    updates.washedKr !== undefined ||
    updates.mistranslations !== undefined ||
    updates.userDirective !== undefined ||
    updates.batchStrategyDirective !== undefined ||
    updates.isCompleted !== undefined
  );
}

export function createQuestionRequestBody(question: WorkspaceQuestion) {
  return {
    title: question.title,
    maxLength: question.maxLength,
    userDirective: question.userDirective,
    batchStrategyDirective: question.batchStrategyDirective ?? "",
    content: question.content,
    washedKr: question.washedKr,
    mistranslations: JSON.stringify(question.mistranslations),
    aiReview: JSON.stringify(question.aiReviewReport),
    isCompleted: question.isCompleted,
  };
}

export function mapStoredQuestion(question: any): WorkspaceQuestion {
  return {
    id: `q-${question.id}`,
    dbId: question.id,
    title: question.title,
    maxLength: question.maxLength,
    lengthTarget: null,
    userDirective: question.userDirective || "",
    batchStrategyDirective: question.batchStrategyDirective || "",
    content: question.content || "",
    washedKr: question.washedKr || "",
    mistranslations: parseStoredMistranslations(question.mistranslations),
    aiReviewReport: parseStoredAiReview(question.aiReview),
    isCompleted: !!question.completed || !!question.isCompleted,
    category: question.category ?? null,
  };
}

export function normalizeCompletionMistranslations(items: any[] = []): Mistranslation[] {
  return items.map((item, index) => ({
    ...item,
    id: item.id || `mis-${index}`,
    suggestion: item.suggestion ?? item.translated ?? "",
  }));
}

export function normalizeLengthTarget(
  requestedLengthTarget: number | null | undefined,
  maxLength: number
) {
  if (!requestedLengthTarget || requestedLengthTarget <= 0) {
    return null;
  }

  return Math.min(
    Math.max(1, Math.floor(requestedLengthTarget)),
    Math.max(1, maxLength || requestedLengthTarget)
  );
}

export function applySuggestionToQuestion(
  question: WorkspaceQuestion,
  mistranslationId: string
) {
  const mistranslation = question.mistranslations.find((item) => item.id === mistranslationId);
  if (!mistranslation) {
    return null;
  }

  const normalizedSuggestion = mistranslation.suggestion?.trim() || "";
  const normalizedTranslated = mistranslation.translated?.trim() || "";
  const isReverting = normalizedSuggestion === normalizedTranslated;
  const finalSuggestion = isReverting ? mistranslation.original : normalizedSuggestion;

  const sentenceReplacement = buildSentenceReplacementText(
    question.washedKr,
    mistranslation,
    isReverting
  );
  const updatedText =
    sentenceReplacement ??
    replaceTranslatedPhrase(question.washedKr, mistranslation.translated, finalSuggestion);

  if (updatedText === null) {
    return null;
  }

  return {
    washedKr: updatedText,
    mistranslations: question.mistranslations.filter((item) => item.id !== mistranslationId),
  };
}

function buildSentenceReplacementSpec(
  mistranslation: Mistranslation,
  isReverting: boolean
) {
  if (!mistranslation.translatedSentence || !mistranslation.suggestedSentence) {
    return null;
  }

  const finalReplacement = isReverting
    ? mistranslation.suggestedSentence.replace(
        mistranslation.translated,
        mistranslation.original
      )
    : mistranslation.suggestedSentence;

  return {
    targetSentence: mistranslation.translatedSentence,
    finalReplacement,
  };
}

function parseStoredMistranslations(raw: string | null | undefined): Mistranslation[] {
  const parsed = parseJson<any[]>(raw);
  if (!parsed) {
    return [];
  }

  return parsed.map((item, index) => ({
    ...item,
    id: item.id || `mis-${index}`,
    suggestion: item.suggestion ?? item.translated ?? "",
  }));
}

function parseStoredAiReview(raw: string | null | undefined): AiReviewReport | null {
  return parseJson<AiReviewReport>(raw);
}

function parseJson<T>(raw: string | null | undefined): T | null {
  if (!raw) {
    return null;
  }

  try {
    return JSON.parse(raw) as T;
  } catch {
    return null;
  }
}

function replaceTranslatedPhrase(
  source: string,
  translated: string,
  finalSuggestion: string
) {
  if (!translated.trim()) {
    return null;
  }

  const searchTerms = [translated];
  if (translated.includes(" ")) {
    searchTerms.push(translated.replace(/\s+/g, ""));
  }

  for (const term of searchTerms) {
    const replaced = replaceOnce(source, term, finalSuggestion);
    if (replaced !== null) {
      return replaced;
    }
  }

  return null;
}

function replaceOnce(source: string, searchValue: string, replacement: string) {
  const index = source.indexOf(searchValue);
  if (index < 0) {
    return null;
  }

  return source.slice(0, index) + replacement + source.slice(index + searchValue.length);
}

function findSentenceFallback(source: string, targetSentence: string) {
  const normalizedTarget = targetSentence.normalize("NFC").trim();
  if (!normalizedTarget) {
    return null;
  }

  const exactIndex = source.indexOf(normalizedTarget);
  if (exactIndex >= 0) {
    return { start: exactIndex, end: exactIndex + normalizedTarget.length };
  }

  const sentences = source.match(/[^\n.!?]+[.!?\n]?/g) ?? [];
  let cursor = 0;
  let best: { start: number; end: number; score: number } | null = null;
  const targetTokens = new Set(tokenizeSentence(normalizedTarget));

  for (const sentence of sentences) {
    const start = source.indexOf(sentence, cursor);
    if (start < 0) {
      continue;
    }

    const end = start + sentence.length;
    cursor = end;

    const sentenceTokens = new Set(tokenizeSentence(sentence));
    if (!targetTokens.size || !sentenceTokens.size) {
      continue;
    }

    const overlap = [...targetTokens].filter((token) => sentenceTokens.has(token)).length;
    const union = new Set([...targetTokens, ...sentenceTokens]).size;
    const score = union === 0 ? 0 : overlap / union;
    if (!best || score > best.score) {
      best = { start, end, score };
    }
  }

  return best && best.score >= 0.45 ? { start: best.start, end: best.end } : null;
}

function tokenizeSentence(value: string) {
  return value
    .toLowerCase()
    .split(/\s+/)
    .map((token) => token.replace(/[^\p{L}\p{N}-]/gu, ""))
    .filter((token) => token.length > 1);
}

function replaceSentence(source: string, targetSentence: string, replacement: string) {
  const exactReplacement = replaceOnce(source, targetSentence, replacement);
  if (exactReplacement !== null) {
    return exactReplacement;
  }

  const sentenceRange = findSentenceFallback(source, targetSentence);
  if (!sentenceRange) {
    return null;
  }

  return (
    source.slice(0, sentenceRange.start) +
    replacement +
    source.slice(sentenceRange.end)
  );
}

function buildSentenceReplacementResult(
  source: string,
  sentenceReplacement: { targetSentence: string; finalReplacement: string } | null
) {
  if (!sentenceReplacement) {
    return null;
  }

  return replaceSentence(
    source,
    sentenceReplacement.targetSentence,
    sentenceReplacement.finalReplacement
  );
}

function buildSentenceReplacementText(
  source: string,
  mistranslation: Mistranslation,
  isReverting: boolean
) {
  const replacement = buildSentenceReplacementSpec(mistranslation, isReverting);
  if (!replacement) {
    return null;
  }

  return buildSentenceReplacementResult(source, replacement);
}
