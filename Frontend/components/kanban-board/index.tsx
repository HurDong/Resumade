"use client"

import { useEffect, useState } from "react"
import { DragDropContext, Droppable, type DropResult } from "@hello-pangea/dnd"
import { ArchiveX, ChevronRight, ChevronsUp, Loader2, Plus } from "lucide-react"
import { Button } from "@/components/ui/button"
import { ScrollArea } from "@/components/ui/scroll-area"
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from "@/components/ui/alert-dialog"
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog"
import { mockApplications, type Application, type ApplicationStatus } from "@/lib/mock-data"
import { isFrontendOnlyMode } from "@/lib/frontend-only"
import { getCompanyLogo } from "@/lib/logo-utils"
import { countResumeCharacters } from "@/lib/text/resume-character-count"
import { KanbanColumn, type ColumnDefinition } from "./kanban-column"
import { ApplicationCard } from "./application-card"
import { ApplicationDetailSheet } from "./application-detail-sheet"
import { AddApplicationDialog } from "./add-application-dialog"

const FAILED_BIN_ID = "failed-bin"

export const columns: ColumnDefinition[] = [
  { id: "document", title: "서류 전형", color: "bg-primary" },
  { id: "aptitude", title: "인적성/코딩테스트", color: "bg-chart-2" },
  { id: "interview1", title: "1차 면접", color: "bg-chart-3" },
  { id: "interview2", title: "2차 면접", color: "bg-chart-4" },
  { id: "passed", title: "최종 합격", color: "bg-emerald-500" },
]

function mapApplicationFromApi(app: any): Application {
  return {
    id: app.id.toString(),
    company: app.companyName,
    position: app.position,
    status: app.status?.toLowerCase() || "document",
    logoColor: "bg-blue-500",
    logoUrl: app.logoUrl,
    deadline: app.deadline,
    techStack: [],
    rawJd: app.rawJd || "",
    aiInsight: app.aiInsight || "",
    companyResearch: app.companyResearch || "",
    questions: (app.questions || []).map((question: any) => ({
      id: question.id.toString(),
      title: question.title,
      maxLength: question.maxLength || 1000,
      currentLength: countResumeCharacters(question.content),
      content: question.content || "",
      isCompleted: !!question.isCompleted || !!question.completed,
    })),
    result: (app.result?.toLowerCase() || "pending") as Application["result"],
  }
}

function getApplicationProgress(application: Application) {
  const questions = application.questions || []
  if (questions.length === 0) return 0

  const totalMaxLength = questions.reduce(
    (sum, question) => sum + Math.max(question.maxLength || 0, 0),
    0
  )

  if (totalMaxLength === 0) {
    const completedCount = questions.filter((question) => question.isCompleted).length
    return Math.round((completedCount / questions.length) * 100)
  }

  const totalCurrentLength = questions.reduce(
    (sum, question) =>
      sum + Math.min(question.currentLength || 0, question.maxLength || 0),
    0
  )

  return Math.round((totalCurrentLength / totalMaxLength) * 100)
}

function getDropUpdates(destinationId: string): Partial<Application> | null {
  if (destinationId === FAILED_BIN_ID || destinationId === `${FAILED_BIN_ID}-list`) {
    return { result: "fail" }
  }

  const nextStatus = destinationId as ApplicationStatus

  if (nextStatus === "passed") {
    return { status: "passed", result: "pass" }
  }

  return { status: nextStatus, result: "pending" }
}

export function KanbanBoard() {
  const [selectedApplication, setSelectedApplication] = useState<Application | null>(null)
  const [isSheetOpen, setIsSheetOpen] = useState(false)
  const [isAddDialogOpen, setIsAddDialogOpen] = useState(false)
  const [localApplications, setLocalApplications] = useState<Application[]>([])
  const [isLoading, setIsLoading] = useState(true)
  const [isFailedDrawerOpen, setIsFailedDrawerOpen] = useState(false)
  const [deleteTargetId, setDeleteTargetId] = useState<string | null>(null)

  const sortedApplications = [...localApplications].sort((a, b) => {
    const resultPriority = { pending: 0, pass: 1, fail: 2 }
    const priorityA = resultPriority[a.result || "pending"]
    const priorityB = resultPriority[b.result || "pending"]

    if (priorityA !== priorityB) return priorityA - priorityB

    const timeA = a.deadline ? new Date(a.deadline).getTime() : Number.POSITIVE_INFINITY
    const timeB = b.deadline ? new Date(b.deadline).getTime() : Number.POSITIVE_INFINITY

    if (timeA !== timeB) return timeA - timeB

    const progressA = getApplicationProgress(a)
    const progressB = getApplicationProgress(b)

    if (progressA !== progressB) return progressA - progressB

    return a.id.localeCompare(b.id)
  })

  const failedApplications = sortedApplications.filter(
    (application) => application.result === "fail"
  )

  const fetchApplications = async () => {
    setIsLoading(true)
    if (isFrontendOnlyMode()) {
      setLocalApplications(mockApplications)
      setIsLoading(false)
      return
    }

    try {
      const response = await fetch("/api/applications")
      const data = await response.json()
      if (!Array.isArray(data)) {
        console.error("Expected array from /api/applications, but got:", data)
        setLocalApplications(mockApplications)
        return
      }
      setLocalApplications(data.map(mapApplicationFromApi))
    } catch (error) {
      console.error("Failed to fetch applications:", error)
      setLocalApplications(mockApplications)
    } finally {
      setIsLoading(false)
    }
  }

  useEffect(() => {
    fetchApplications()
  }, [])

  useEffect(() => {
    if (localApplications.length === 0) return

    const fetchMissingLogos = async () => {
      let hasUpdates = false
      const updatedApplications = [...localApplications]

      for (let index = 0; index < updatedApplications.length; index += 1) {
        const application = updatedApplications[index]
        if (application.logoUrl) continue

        const logoUrl = getCompanyLogo(application.company)

        try {
          const response = await fetch(`/api/applications/${application.id}`, {
            method: "PUT",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ logoUrl }),
          })

          if (response.ok) {
            updatedApplications[index] = { ...application, logoUrl }
            hasUpdates = true
          }
        } catch (error) {
          console.error(`Failed to auto-update logo for ${application.company}`, error)
        }
      }

      if (hasUpdates) {
        setLocalApplications(updatedApplications)
      }
    }

    fetchMissingLogos()
  }, [localApplications.length])

  useEffect(() => {
    if (!selectedApplication) return

    const updatedApplication = localApplications.find(
      (application) => application.id === selectedApplication.id
    )

    if (updatedApplication) {
      setSelectedApplication(updatedApplication)
      return
    }

    if (isSheetOpen) {
      setIsSheetOpen(false)
      setSelectedApplication(null)
    }
  }, [isSheetOpen, localApplications, selectedApplication])

  const handleCardClick = (application: Application) => {
    setSelectedApplication(application)
    setIsSheetOpen(true)
  }

  const handleCloseSheet = () => {
    setIsSheetOpen(false)
    // 즉시 null로 초기화해야 같은 카드를 다시 클릭해도 열림
    setSelectedApplication(null)
  }

  const handleDeleteApplication = async (id: string) => {
    // confirm 대신 AlertDialog를 띄우기 위해 deleteTargetId만 설정
    setDeleteTargetId(id)
  }

  const confirmDelete = async () => {
    if (!deleteTargetId) return
    const id = deleteTargetId
    setDeleteTargetId(null)
    // Sheet를 먼저 닫아야 다음 카드 클릭이 즉시 동작함
    setIsSheetOpen(false)
    setSelectedApplication(null)
    try {
      const response = await fetch(`/api/applications/${id}`, { method: "DELETE" })
      if (!response.ok) throw new Error("Failed to delete application")
      setLocalApplications((prev) => prev.filter((a) => a.id !== id))
    } catch (error) {
      console.error(error)
    }
  }

  const handleAddApplication = async (newApplication: {
    company: string
    position: string
    rawJd: string
    aiInsight: string
    questions: { title: string; maxLength: number }[]
  }) => {
    try {
      const response = await fetch("/api/applications/full", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          companyName: newApplication.company,
          position: newApplication.position,
          rawJd: newApplication.rawJd,
          aiInsight: newApplication.aiInsight,
          questions: newApplication.questions,
        }),
      })

      if (!response.ok) throw new Error("Failed to create application")

      const savedApplication = await response.json()
      setLocalApplications((current) => [mapApplicationFromApi(savedApplication), ...current])
    } catch (error) {
      console.error(error)
      alert("공고 등록에 실패했습니다.")
    }
  }

  const handleUpdateApplication = async (id: string, updates: Partial<Application>) => {
    const previousApplications = localApplications

    try {
      setLocalApplications((current) =>
        current.map((application) =>
          application.id === id ? { ...application, ...updates } : application
        )
      )

      const response = await fetch(`/api/applications/${id}`, {
        method: "PUT",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(updates),
      })

      if (!response.ok) throw new Error("Failed to update application")

      const savedApplication = mapApplicationFromApi(await response.json())
      setLocalApplications((current) =>
        current.map((application) =>
          application.id === id ? { ...application, ...savedApplication } : application
        )
      )
    } catch (error) {
      console.error(error)
      setLocalApplications(previousApplications)
      await fetchApplications()
    }
  }

  const onDragEnd = (result: DropResult) => {
    const { destination, source, draggableId } = result

    if (!destination) return
    if (
      destination.droppableId === source.droppableId &&
      destination.index === source.index
    ) {
      return
    }

    const updates = getDropUpdates(destination.droppableId)
    if (!updates) return

    if (
      destination.droppableId === FAILED_BIN_ID ||
      destination.droppableId === `${FAILED_BIN_ID}-list`
    ) {
      setIsFailedDrawerOpen(true)
    }

    void handleUpdateApplication(draggableId, updates)
  }

  if (isLoading) {
    return (
      <div className="flex h-[400px] w-full items-center justify-center rounded-3xl border border-border/50 bg-background/50 shadow-inner backdrop-blur-sm">
        <div className="flex flex-col items-center gap-4">
          <div className="relative">
            <div className="absolute inset-0 scale-150 animate-ping rounded-full bg-primary/20" />
            <Loader2 className="relative size-10 animate-spin text-primary" />
          </div>
          <p className="animate-pulse text-sm font-medium text-muted-foreground">
            공고 목록을 불러오는 중...
          </p>
        </div>
      </div>
    )
  }

  return (
    <div className="flex h-full min-h-0 flex-col">
      <div className="mb-4 flex flex-wrap items-start justify-between gap-3">
        <div className="flex flex-wrap items-center gap-2">
          {columns.map((column, index) => (
            <div key={column.id} className="flex items-center">
              <span className="rounded bg-muted/50 px-2 py-1 text-xs text-muted-foreground">
                {column.title}
              </span>
              {index < columns.length - 1 && (
                <ChevronRight className="mx-1 size-3 text-muted-foreground/50" />
              )}
            </div>
          ))}
        </div>

        <Button onClick={() => setIsAddDialogOpen(true)} className="gap-2 shadow-md">
          <Plus className="size-4" />
          새 공고 추가
        </Button>
      </div>

      <DragDropContext onDragEnd={onDragEnd}>
        <div className="flex min-h-0 flex-1 gap-4 pb-4">
          {columns.map((column) => (
            <div key={column.id} className="h-full min-w-0 flex-1">
              <KanbanColumn
                column={column}
                applications={sortedApplications.filter(
                  (application) =>
                    application.status === column.id && application.result !== "fail"
                )}
                onCardClick={handleCardClick}
              />
            </div>
          ))}
        </div>

        <div className="shrink-0">
          <Droppable droppableId={FAILED_BIN_ID}>
            {(provided, snapshot) => (
              <div
                ref={provided.innerRef}
                {...provided.droppableProps}
                className={`overflow-hidden rounded-t-2xl border border-b-0 transition-all ${
                  snapshot.isDraggingOver
                    ? "border-red-300 bg-red-50/90 shadow-lg shadow-red-100"
                    : "border-border/70 bg-background/90"
                }`}
              >
                <button
                  type="button"
                  className="flex w-full items-center justify-between px-4 py-3 text-left transition-colors hover:bg-muted/40"
                  onClick={() => setIsFailedDrawerOpen((current) => !current)}
                >
                  <div className="flex items-center gap-3">
                    <div
                      className={`flex size-9 items-center justify-center rounded-xl ${
                        snapshot.isDraggingOver
                          ? "bg-red-500 text-white"
                          : "bg-red-50 text-red-500"
                      }`}
                    >
                      <ArchiveX className="size-4" />
                    </div>
                    <div>
                      <p className="text-sm font-semibold">불합격 보관함</p>
                      <p className="text-xs text-muted-foreground">
                        아래 서랍을 열어 확인하고, 카드 드롭으로 탈락 처리합니다.
                      </p>
                    </div>
                  </div>

                  <div className="flex items-center gap-3">
                    <span className="rounded-full bg-red-50 px-2.5 py-1 text-xs font-semibold text-red-600">
                      {failedApplications.length}개
                    </span>
                    <div className="flex items-center gap-1 text-xs font-semibold text-muted-foreground">
                      <ChevronsUp
                        className={`size-4 transition-transform ${
                          isFailedDrawerOpen ? "rotate-180" : ""
                        }`}
                      />
                      {isFailedDrawerOpen ? "숨기기" : "위로 펼치기"}
                    </div>
                  </div>
                </button>

                {isFailedDrawerOpen && (
                  <div className="border-t border-border/70 px-4 pb-4 pt-3">
                    <p className="mb-3 text-xs text-muted-foreground">
                      전형 컬럼으로 다시 드래그하면 진행 중으로 복구됩니다.
                    </p>

                    <ScrollArea className="max-h-[220px] rounded-2xl border border-border/70 bg-background">
                      <Droppable droppableId={`${FAILED_BIN_ID}-list`} direction="horizontal">
                        {(listProvided) => (
                          <div
                            ref={listProvided.innerRef}
                            {...listProvided.droppableProps}
                            className="flex min-h-[140px] gap-3 p-3"
                          >
                            {failedApplications.map((application, index) => (
                              <div key={application.id} className="w-[260px] shrink-0">
                                <ApplicationCard
                                  application={application}
                                  index={index}
                                  onClick={() => handleCardClick(application)}
                                />
                              </div>
                            ))}
                            {listProvided.placeholder}
                            {failedApplications.length === 0 && (
                              <div className="flex h-[120px] w-full items-center justify-center rounded-xl border border-dashed border-border text-sm text-muted-foreground">
                                아직 보관된 불합격 공고가 없습니다.
                              </div>
                            )}
                          </div>
                        )}
                      </Droppable>
                    </ScrollArea>
                  </div>
                )}
                {provided.placeholder}
              </div>
            )}
          </Droppable>
        </div>
      </DragDropContext>

      <ApplicationDetailSheet
        application={selectedApplication}
        isOpen={isSheetOpen}
        onClose={handleCloseSheet}
        onRefresh={fetchApplications}
        onUpdateApplication={async (updates) => {
          if (!selectedApplication) return
          await handleUpdateApplication(selectedApplication.id, updates)
        }}
        onDelete={handleDeleteApplication}
      />

      <AddApplicationDialog
        isOpen={isAddDialogOpen}
        onClose={() => setIsAddDialogOpen(false)}
        onAdd={handleAddApplication}
      />

      {/* 삭제 확인 Dialog */}
      <Dialog open={!!deleteTargetId} onOpenChange={(open) => !open && setDeleteTargetId(null)}>
        <DialogContent className="sm:max-w-sm">
          <DialogHeader>
            <DialogTitle>공고를 삭제할까요?</DialogTitle>
            <DialogDescription>
              연관된 자소서 문항과 데이터가 모두 사라집니다.
            </DialogDescription>
          </DialogHeader>
          <DialogFooter className="gap-2 sm:gap-2">
            <Button variant="outline" onClick={() => setDeleteTargetId(null)}>취소</Button>
            <Button
              onClick={confirmDelete}
              className="bg-destructive text-white hover:bg-destructive/90"
            >
              삭제
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  )
}
