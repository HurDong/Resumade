"use client"

import { useEffect, useRef } from "react"
import { toast } from "sonner"
import { toApiUrl } from "@/lib/network/api-base"
import { useBackgroundTaskStore } from "@/lib/store/background-task-store"
import { useWorkspaceStore } from "@/lib/store/workspace-store"

const POLL_INTERVAL_MS = 2_000
const MAX_POLL_ATTEMPTS = 150 // ~5분

/**
 * 페이지 이탈 중 진행된 백그라운드 LLM 파이프라인 결과를 폴링한다.
 * - 페이지에 있을 때는 SSE가 정상 동작하므로 이 훅은 아무것도 하지 않는다.
 * - 페이지에 없는 동안 작업이 완료됐다면, 복귀 시 결과를 자동으로 적용한다.
 */
export function useBackgroundTaskPoller(questionId: number | undefined) {
  const { unregister, getPendingForQuestion } = useBackgroundTaskStore()
  const { isProcessing, completeProcessing, setError } = useWorkspaceStore()
  const attemptRef = useRef(0)

  useEffect(() => {
    if (!questionId) return

    const task = getPendingForQuestion(questionId)
    // 이미 페이지에서 SSE 처리 중이면 폴링 불필요
    if (!task || isProcessing) return

    attemptRef.current = 0

    // 페이지가 이탈 중 작업이 진행됐음을 사용자에게 알림
    useWorkspaceStore.setState({
      isProcessing: true,
      pipelineStage: "RAG",
      progressMessage: "백그라운드에서 작업이 진행 중입니다...",
    })

    const intervalId = setInterval(async () => {
      attemptRef.current += 1

      if (attemptRef.current > MAX_POLL_ATTEMPTS) {
        clearInterval(intervalId)
        unregister(questionId)
        setError("백그라운드 작업이 시간 초과되었습니다.")
        return
      }

      try {
        const res = await fetch(toApiUrl(`/api/workspace/task-status/${questionId}`))

        if (res.status === 404) {
          // 아직 Redis에 상태가 없음 — 계속 폴링
          return
        }

        if (!res.ok) {
          clearInterval(intervalId)
          unregister(questionId)
          setError("백그라운드 작업 상태를 확인할 수 없습니다.")
          return
        }

        const data = await res.json()

        if (data.status === "COMPLETE") {
          clearInterval(intervalId)
          unregister(questionId)
          completeProcessing(data.payload)
          toast.success("백그라운드 작업이 완료됐습니다.", {
            description: "다른 곳에서 작업하는 동안 초안 생성이 완료됐습니다.",
          })
        } else if (data.status === "ERROR") {
          clearInterval(intervalId)
          unregister(questionId)
          setError(data.errorMessage || "백그라운드 작업 중 오류가 발생했습니다.")
        }
        // status === "RUNNING" → 계속 폴링
      } catch {
        // 네트워크 오류 → 계속 폴링
      }
    }, POLL_INTERVAL_MS)

    return () => clearInterval(intervalId)
  // isProcessing이 바뀌면 재평가 (SSE가 시작되면 폴링 중단)
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [questionId, isProcessing])
}
