import { create } from "zustand"
import { persist } from "zustand/middleware"

export type BackgroundTaskType = "generate" | "refine" | "rewash" | "repatch"

export interface BackgroundTask {
  questionId: number
  applicationId: string
  taskType: BackgroundTaskType
  startedAt: number
}

interface BackgroundTaskState {
  tasks: Record<number, BackgroundTask>

  register: (task: BackgroundTask) => void
  unregister: (questionId: number) => void
  getPendingForQuestion: (questionId: number) => BackgroundTask | undefined
  clearAll: () => void
}

export const useBackgroundTaskStore = create<BackgroundTaskState>()(
  persist(
    (set, get) => ({
      tasks: {},

      register: (task) =>
        set((state) => ({
          tasks: { ...state.tasks, [task.questionId]: task },
        })),

      unregister: (questionId) =>
        set((state) => {
          const next = { ...state.tasks }
          delete next[questionId]
          return { tasks: next }
        }),

      getPendingForQuestion: (questionId) => get().tasks[questionId],

      clearAll: () => set({ tasks: {} }),
    }),
    {
      name: "resumade-background-tasks",
    }
  )
)
