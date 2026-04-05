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
 * - 페이지에 있을 때 SSE가 정상 동작하면 이 훅은 아무것도 하지 않는다.
 * - 페이지에 없는 동안 작업이 완료됐다면, 복귀 시 결과를 자동으로 적용한다.
 *
 * ⚠️ isProcessing을 deps에 넣지 않는 것이 의도적인 설계다.
 *    폴러 자신이 isProcessing을 true로 바꾸면 effect가 재실행돼
 *    클린업이 interval을 제거하는 self-kill 버그가 발생하기 때문이다.
 *    대신 interval 콜백 내부에서 직접 상태를 읽어 SSE 진입 여부를 판단한다.
 */
export function useBackgroundTaskPoller(questionId: number | undefined) {
  const { unregister, getPendingForQuestion } = useBackgroundTaskStore()
  const attemptRef = useRef(0)

  useEffect(() => {
    if (!questionId) return

    const task = getPendingForQuestion(questionId)
    if (!task) return

    // 마운트 시점에 이미 SSE 파이프라인이 실행 중이면 폴링 불필요
    const { isProcessing } = useWorkspaceStore.getState()
    if (isProcessing) return

    attemptRef.current = 0

    useWorkspaceStore.setState({
      isProcessing: true,
      pipelineStage: "RAG",
      progressMessage: "백그라운드에서 작업이 진행 중입니다...",
    })

    const intervalId = setInterval(async () => {
      attemptRef.current += 1

      // 외부에서 SSE 스트림이 새로 시작된 경우 폴링 중단 (register 후 새 SSE 시작)
      const currentTask = getPendingForQuestion(questionId)
      if (!currentTask) {
        // 이미 completeProcessing 또는 cancelSingle에서 unregister된 경우
        clearInterval(intervalId)
        return
      }

      if (attemptRef.current > MAX_POLL_ATTEMPTS) {
        clearInterval(intervalId)
        unregister(questionId)
        useWorkspaceStore.getState().setError("백그라운드 작업이 시간 초과되었습니다.")
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
          useWorkspaceStore.getState().setError("백그라운드 작업 상태를 확인할 수 없습니다.")
          return
        }

        const data = await res.json()

        if (data.status === "COMPLETE") {
          clearInterval(intervalId)
          unregister(questionId)
          useWorkspaceStore.getState().completeProcessing(data.payload)
          toast.success("백그라운드 작업이 완료됐습니다.", {
            description: "다른 곳에서 작업하는 동안 초안 생성이 완료됐습니다.",
          })
        } else if (data.status === "ERROR") {
          clearInterval(intervalId)
          unregister(questionId)
          useWorkspaceStore.getState().setError(data.errorMessage || "백그라운드 작업 중 오류가 발생했습니다.")
        }
        // status === "RUNNING" → 계속 폴링
      } catch {
        // 네트워크 오류 → 계속 폴링
      }
    }, POLL_INTERVAL_MS)

    return () => clearInterval(intervalId)
    // questionId가 바뀔 때만 재실행. isProcessing은 의도적으로 제외 (위 주석 참고).
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [questionId])
}
