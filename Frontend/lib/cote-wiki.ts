export const COTE_WIKI_FILTERS = ["전체", "문법", "알고리즘"] as const

export type CoteWikiCategory = "문법" | "알고리즘"
export type CoteWikiFilter = (typeof COTE_WIKI_FILTERS)[number]

export type CoteWikiNote = {
  id: number
  title: string
  category: CoteWikiCategory
  content: string
  sortOrder: number
  createdAt: string | null
  updatedAt: string | null
}

export type CoteWikiDraft = Pick<CoteWikiNote, "title" | "category" | "content">

export function getCoteWikiBadgeClassName(category: CoteWikiCategory) {
  return category === "문법"
    ? "border-sky-200 bg-sky-50 text-sky-700 dark:border-sky-900/60 dark:bg-sky-950/40 dark:text-sky-300"
    : "border-emerald-200 bg-emerald-50 text-emerald-700 dark:border-emerald-900/60 dark:bg-emerald-950/40 dark:text-emerald-300"
}

export function buildCoteWikiPayload(draft: CoteWikiDraft) {
  return {
    title: draft.title.trim(),
    category: draft.category,
    content: draft.content,
  }
}

export function getCoteWikiUpdatedLabel(note: Pick<CoteWikiNote, "updatedAt" | "createdAt">) {
  const value = note.updatedAt ?? note.createdAt
  if (!value) {
    return "방금 만든 카드"
  }

  const date = new Date(value)
  if (Number.isNaN(date.getTime())) {
    return "최근 저장한 카드"
  }

  return new Intl.DateTimeFormat("ko-KR", {
    month: "numeric",
    day: "numeric",
    hour: "numeric",
    minute: "2-digit",
  }).format(date)
}
