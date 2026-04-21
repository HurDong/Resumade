import { toApiUrl } from "@/lib/network/api-base"
import { streamSse } from "@/lib/network/stream-sse"

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

interface StreamDraftWashOptions {
  draftText: string
  signal?: AbortSignal
  onProgress: (progress: DraftWashProgress) => void
  onComplete: (result: DraftWashResult) => void
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
