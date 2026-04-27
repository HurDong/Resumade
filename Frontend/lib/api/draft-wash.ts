import { toApiUrl } from "@/lib/network/api-base"
import { streamSse } from "@/lib/network/stream-sse"
import { isWorkspaceVisibleApplication } from "@/lib/workspace/workspace-visibility"

export type DraftWashStage = "received" | "toEnglish" | "toKorean" | "ready"

export interface DraftWashProgress {
  stage: DraftWashStage
  message: string
}

export interface DraftWashResult {
  originalText: string
  englishText: string
  washedText: string
  originalChars: number
  washedChars: number
}

export interface WashQuestionSummary {
  id: string
  title: string
  maxLength: number
  content: string
  washedKr: string
  finalText: string | null
  isCompleted: boolean
}

export interface WashApplicationSummary {
  id: string
  companyName: string
  position: string
  status: string
  result: string
  questions: WashQuestionSummary[]
}

export interface QuestionWashProgress {
  stage: string
  message: string
}

export interface QuestionWashResult {
  draft?: string
  washedDraft?: string
  sourceDraft?: string
  mistranslations?: unknown[]
  aiReviewReport?: unknown
  warningMessage?: string | null
}

interface StreamDraftWashOptions {
  draftText: string
  signal?: AbortSignal
  onProgress: (progress: DraftWashProgress) => void
  onComplete: (result: DraftWashResult) => void
}

interface StreamQuestionRewashOptions {
  questionId: number
  signal?: AbortSignal
  onProgress: (progress: QuestionWashProgress) => void
  onDraftIntermediate?: (draft: string) => void
  onWashedIntermediate?: (washed: string) => void
  onComplete: (result: QuestionWashResult) => void
}

export async function fetchWashApplications(): Promise<WashApplicationSummary[]> {
  const response = await fetch(toApiUrl("/api/applications"), { cache: "no-store" })

  if (!response.ok) {
    throw new Error(`Failed to load applications: ${response.status}`)
  }

  const applications = await response.json()
  if (!Array.isArray(applications)) {
    return []
  }

  return applications
    .map((application: any) => ({
      id: String(application.id),
      companyName: application.companyName ?? application.company ?? "",
      position: application.position ?? "",
      status: (application.status ?? "document").toString().toLowerCase(),
      result: (application.result ?? "pending").toString().toLowerCase(),
      questions: Array.isArray(application.questions)
        ? application.questions.map((question: any) => ({
            id: String(question.id),
            title: question.title ?? "",
            maxLength: Number(question.maxLength ?? 1000),
            content: question.content ?? "",
            washedKr: question.washedKr ?? "",
            finalText: question.finalText ?? null,
            isCompleted: Boolean(question.completed ?? question.isCompleted),
          }))
        : [],
    }))
    .filter(isWorkspaceVisibleApplication)
}

export async function saveManualDraft(questionId: number, content: string) {
  const response = await fetch(toApiUrl(`/api/workspace/questions/${questionId}/manual-draft`), {
    method: "PATCH",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ content }),
  })

  if (!response.ok) {
    throw new Error(`Failed to save manual draft: ${response.status}`)
  }

  return response.json()
}

export async function streamDraftWash({
  draftText,
  signal,
  onProgress,
  onComplete,
}: StreamDraftWashOptions) {
  await streamSse({
    url: toApiUrl("/api/v1/draft-wash/stream"),
    method: "POST",
    headers: {
      "Content-Type": "application/json",
    },
    body: JSON.stringify({ draftText }),
    signal,
    onEvent: ({ event, data }) => {
      if (!data) return

      if (event === "PROGRESS") {
        onProgress(JSON.parse(data) as DraftWashProgress)
        return
      }

      if (event === "COMPLETE") {
        onComplete(JSON.parse(data) as DraftWashResult)
        return
      }

      if (event === "ERROR") {
        const payload = JSON.parse(data) as { message?: string }
        throw new Error(payload.message ?? "Draft wash failed")
      }
    },
  })
}

export async function streamQuestionRewash({
  questionId,
  signal,
  onProgress,
  onDraftIntermediate,
  onWashedIntermediate,
  onComplete,
}: StreamQuestionRewashOptions) {
  await streamSse({
    url: toApiUrl(`/api/workspace/rewash-stream/${questionId}`),
    signal,
    onOpen: () => {
      onProgress({ stage: "CONNECTED", message: "WASH 파이프라인에 연결했습니다." })
    },
    onEvent: ({ event, data }) => {
      const eventName = event.toLowerCase()

      if (eventName === "stage") {
        const stage = readSseStage(data)
        onProgress({ stage, message: stage })
        return
      }

      if (eventName === "progress") {
        onProgress({
          stage: readSseStage(data),
          message: readSseMessage(data) || "WASH를 진행하고 있습니다.",
        })
        return
      }

      if (eventName === "draft_intermediate") {
        onDraftIntermediate?.(readSseMessage(data))
        return
      }

      if (eventName === "washed_intermediate") {
        onWashedIntermediate?.(readSseMessage(data))
        return
      }

      if (eventName === "complete") {
        onComplete(parseSseJson<QuestionWashResult>(data) ?? {})
        return
      }

      if (eventName === "error") {
        const payload = parseSseJson<{ message?: string }>(data)
        throw new Error(payload?.message ?? readSseMessage(data) ?? "Question WASH failed")
      }
    },
  })
}

function parseSsePayload(raw: string): string | Record<string, unknown> | null {
  if (!raw) {
    return ""
  }

  try {
    const parsed: unknown = JSON.parse(raw)
    if (typeof parsed === "string") {
      return parsed
    }
    if (parsed && typeof parsed === "object") {
      return parsed as Record<string, unknown>
    }
    return parsed == null ? "" : String(parsed)
  } catch {
    return raw
  }
}

function parseSseJson<T>(raw: string): T | null {
  try {
    return JSON.parse(raw) as T
  } catch {
    return null
  }
}

function readSseMessage(raw: string) {
  const parsed = parseSsePayload(raw)
  if (!parsed) {
    return ""
  }
  if (typeof parsed === "string") {
    return parsed
  }

  const message = parsed.message ?? parsed.data ?? parsed.stage
  return typeof message === "string" ? message : ""
}

function readSseStage(raw: string) {
  const parsed = parseSsePayload(raw)
  const stage = typeof parsed === "object" && parsed && typeof parsed.stage === "string"
    ? parsed.stage
    : readSseMessage(raw)

  return stage.trim().toUpperCase() || "WASH"
}
