"use client"

export const LAST_WORKSPACE_CONTEXT_KEY = "resumade:last-workspace-context"

export type LastWorkspaceContext = {
  applicationId: string
  questionDbId: number | null
  updatedAt: string
}

export function readLastWorkspaceContext(): LastWorkspaceContext | null {
  if (typeof window === "undefined") return null

  try {
    const raw = window.localStorage.getItem(LAST_WORKSPACE_CONTEXT_KEY)
    if (!raw) return null

    const parsed = JSON.parse(raw) as Partial<LastWorkspaceContext>
    if (!parsed.applicationId || typeof parsed.applicationId !== "string") return null

    return {
      applicationId: parsed.applicationId,
      questionDbId:
        typeof parsed.questionDbId === "number" && Number.isFinite(parsed.questionDbId)
          ? parsed.questionDbId
          : null,
      updatedAt:
        typeof parsed.updatedAt === "string" && parsed.updatedAt.length > 0
          ? parsed.updatedAt
          : new Date().toISOString(),
    }
  } catch {
    return null
  }
}

export function writeLastWorkspaceContext(context: {
  applicationId: string
  questionDbId?: number | null
}) {
  if (typeof window === "undefined") return

  const payload: LastWorkspaceContext = {
    applicationId: context.applicationId,
    questionDbId: typeof context.questionDbId === "number" ? context.questionDbId : null,
    updatedAt: new Date().toISOString(),
  }

  window.localStorage.setItem(LAST_WORKSPACE_CONTEXT_KEY, JSON.stringify(payload))
}
