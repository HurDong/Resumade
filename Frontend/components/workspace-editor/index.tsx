import { useCallback, useEffect } from "react"
import { useRouter } from "next/navigation"
import dynamic from "next/dynamic"
import { Skeleton } from "@/components/ui/skeleton"
import { WorkspaceSelector } from "./workspace-selector"
import { useWorkspaceStore } from "@/lib/store/workspace-store"
import { writeLastWorkspaceContext } from "@/lib/workspace/last-workspace-context"

interface WorkspaceEditorProps {
  applicationId?: string
  initialQuestionDbId?: number
}

function WorkspacePanelSkeleton() {
  return (
    <div className="flex h-full min-w-0 flex-col border-border bg-background">
      <div className="shrink-0 border-b border-border p-6">
        <Skeleton className="h-5 w-40" />
        <Skeleton className="mt-3 h-4 w-72 max-w-full" />
      </div>
      <div className="flex-1 space-y-4 p-6">
        <Skeleton className="h-12 w-full rounded-2xl" />
        <Skeleton className="h-32 w-full rounded-3xl" />
        <Skeleton className="h-64 w-full rounded-3xl" />
      </div>
    </div>
  )
}

const ContextPanel = dynamic(
  () => import("./context-panel").then((module) => module.ContextPanel),
  {
    loading: () => <WorkspacePanelSkeleton />,
  }
)

const TranslationPanel = dynamic(
  () => import("./translation-panel").then((module) => module.TranslationPanel),
  {
    loading: () => <WorkspacePanelSkeleton />,
  }
)

export function WorkspaceEditor({ applicationId, initialQuestionDbId }: WorkspaceEditorProps) {
  const router = useRouter()
  const {
    fetchApplicationData,
    questions,
    activeQuestionId,
    setActiveQuestionId,
  } = useWorkspaceStore()

  useEffect(() => {
    if (applicationId) {
      fetchApplicationData(applicationId, initialQuestionDbId)
    }
  }, [applicationId, initialQuestionDbId, fetchApplicationData])

  const activeQuestion = questions.find((question) => question.id === activeQuestionId) ?? questions[0]

  useEffect(() => {
    if (!applicationId) return

    writeLastWorkspaceContext({
      applicationId,
      questionDbId: activeQuestion?.dbId ?? null,
    })
  }, [activeQuestion?.dbId, applicationId])

  const handleWorkspaceExit = useCallback(() => {
    router.push("/")
  }, [router])

  useEffect(() => {
    if (!applicationId) return

    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.defaultPrevented || event.isComposing) return

      const activeElement = document.activeElement
      const isDialogActive =
        activeElement instanceof HTMLElement && activeElement.closest('[role="dialog"]')

      if (isDialogActive) return

      const isEditable =
        activeElement instanceof HTMLElement &&
        (activeElement.tagName === "INPUT" ||
          activeElement.tagName === "TEXTAREA" ||
          activeElement.isContentEditable)

      if ((event.metaKey || event.ctrlKey) && event.key === "Enter") {
        if (activeQuestion?.dbId) {
          event.preventDefault()
          router.push(`/workspace/edit/${activeQuestion.dbId}`)
        }
        return
      }

      if (
        !event.metaKey &&
        !event.ctrlKey &&
        !event.altKey &&
        !event.shiftKey &&
        /^[1-9]$/.test(event.key)
      ) {
        if (isEditable) return

        const targetQuestion = questions[Number(event.key) - 1]
        if (!targetQuestion || targetQuestion.id === activeQuestionId) return

        event.preventDefault()
        setActiveQuestionId(targetQuestion.id)
        return
      }

      if (event.key !== "Escape") return

      if (isEditable) {
        event.preventDefault()
        if (activeElement instanceof HTMLInputElement || activeElement instanceof HTMLTextAreaElement) {
          const caret = activeElement.selectionEnd ?? activeElement.selectionStart ?? 0
          activeElement.setSelectionRange(caret, caret)
        }
        activeElement.blur()
        return
      }

      event.preventDefault()
      handleWorkspaceExit()
    }

    window.addEventListener("keydown", handleKeyDown)
    return () => window.removeEventListener("keydown", handleKeyDown)
  }, [
    activeQuestion?.dbId,
    activeQuestionId,
    applicationId,
    handleWorkspaceExit,
    questions,
    router,
    setActiveQuestionId,
  ])

  if (!applicationId) {
    return <WorkspaceSelector />
  }

  return (
    <div className="grid h-full min-w-0 grid-cols-[minmax(0,1fr)_minmax(0,1fr)] overflow-hidden animate-in fade-in duration-500">
      <div className="min-w-0 overflow-hidden">
        <ContextPanel />
      </div>
      <div className="min-w-0 overflow-hidden">
        <TranslationPanel />
      </div>
    </div>
  )
}
