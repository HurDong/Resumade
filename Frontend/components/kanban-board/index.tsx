"use client"

import { useState } from "react"
import { Button } from "@/components/ui/button"
import { ChevronRight, Plus, Loader2, Eye, EyeOff } from "lucide-react"
import { type Application, type ApplicationStatus } from "@/lib/mock-data"
import { getCompanyLogo } from "@/lib/logo-utils"
import { countResumeCharacters } from "@/lib/text/resume-character-count"
import { KanbanColumn, type ColumnDefinition } from "./kanban-column"
import { ApplicationDetailSheet } from "./application-detail-sheet"
import { DragDropContext, DropResult } from "@hello-pangea/dnd"

export const columns: ColumnDefinition[] = [
  { id: "document", title: "서류 전형", color: "bg-primary" },
  { id: "aptitude", title: "인적성/코딩테스트", color: "bg-chart-2" },
  { id: "interview1", title: "1차 면접", color: "bg-chart-3" },
  { id: "interview2", title: "2차 면접", color: "bg-chart-4" },
  { id: "passed", title: "최종 합격", color: "bg-emerald-500" },
]

import { AddApplicationDialog } from "./add-application-dialog"
import { useEffect } from "react"

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
    questions: (app.questions || []).map((q: any) => ({
      id: q.id.toString(),
      title: q.title,
      maxLength: q.maxLength || 1000,
      currentLength: countResumeCharacters(q.content),
      content: q.content || "",
      isCompleted: !!q.isCompleted || !!q.completed
    })),
    result: (app.result?.toLowerCase() || "pending") as any
  }
}

export function KanbanBoard() {
  const [selectedApplication, setSelectedApplication] = useState<Application | null>(null)
  const [isSheetOpen, setIsSheetOpen] = useState(false)
  const [isAddDialogOpen, setIsAddDialogOpen] = useState(false)
  const [localApplications, setLocalApplications] = useState<Application[]>([])
  const [isLoading, setIsLoading] = useState(true)
  const [showFailed, setShowFailed] = useState(true)

  // Sort applications: 1. Pass > Pending > Fail, 2. Deadline (Urgent first), 3. Stable ID
  const sortedApplications = [...localApplications].sort((a, b) => {
    const resultPriority = { pass: 0, pending: 1, fail: 2 };
    const pA = resultPriority[a.result || "pending"];
    const pB = resultPriority[b.result || "pending"];
    
    if (pA !== pB) return pA - pB;
    
    const timeA = a.deadline ? new Date(a.deadline).getTime() : Infinity;
    const timeB = b.deadline ? new Date(b.deadline).getTime() : Infinity;
    
    if (timeA !== timeB) return timeA - timeB;
    
    return a.id.localeCompare(b.id);
  });

  const fetchApplications = async () => {
    setIsLoading(true)
    try {
      const response = await fetch("/api/applications")
      const data = await response.json()
      if (!Array.isArray(data)) {
        console.error("Expected array from /api/applications, but got:", data)
        return
      }
      const mapped = data.map(mapApplicationFromApi)
      setLocalApplications(mapped)
    } catch (err) {
      console.error("Failed to fetch applications:", err)
    } finally {
      setIsLoading(false)
    }
  }

  useEffect(() => {
    fetchApplications()
  }, [])

  // Auto-fetch and save logos for applications missing them
  useEffect(() => {
    if (localApplications.length > 0) {
      const fetchMissingLogos = async () => {
        let hasUpdates = false
        const updatedApps = [...localApplications]

        for (let i = 0; i < updatedApps.length; i++) {
          const app = updatedApps[i]
          if (!app.logoUrl) {
            const logoUrl = getCompanyLogo(app.company)
            try {
              const response = await fetch(`/api/applications/${app.id}`, {
                method: "PUT",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify({ logoUrl })
              })
              if (response.ok) {
                updatedApps[i] = { ...app, logoUrl }
                hasUpdates = true
              }
            } catch (err) {
              console.error(`Failed to auto-update logo for ${app.company}`, err)
            }
          }
        }

        if (hasUpdates) {
          setLocalApplications(updatedApps)
        }
      }

      fetchMissingLogos()
    }
  }, [localApplications.length > 0]) // Run when we have applications

  const handleCardClick = (app: Application) => {
    setSelectedApplication(app)
    setIsSheetOpen(true)
  }

  const handleCloseSheet = () => {
    setIsSheetOpen(false)
    setSelectedApplication(null)
    void fetchApplications()
  }

  // 데이터 변경 시 선택된 공고 정보 동기화 (실시간 반영)
  useEffect(() => {
    if (selectedApplication) {
      const updated = localApplications.find(app => app.id === selectedApplication.id)
      if (updated) {
        // ID는 같지만 내용이 변경된 경우 업데이트
        setSelectedApplication(updated)
      } else if (isSheetOpen) {
        // 목록에서 사라진 경우 (삭제 등) 시트 닫기
        handleCloseSheet()
      }
    }
  }, [localApplications])

  const handleDeleteApplication = async (id: string) => {
    if (!confirm("정말 이 공고를 삭제하시겠습니까?\n연관된 모든 자소서 문항과 데이터가 영구적으로 삭제됩니다.")) return
    try {
      const response = await fetch(`/api/applications/${id}`, {
        method: "DELETE"
      })
      if (!response.ok) throw new Error("공고 삭제 실패")
      await fetchApplications()
    } catch (err) {
      console.error(err)
      alert("공고 삭제에 실패했습니다.")
    }
  }

  const handleAddApplication = async (newApp: { company: string; position: string; rawJd: string; aiInsight: string; questions: { title: string; maxLength: number }[] }) => {
    try {
      const response = await fetch("/api/applications/full", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          companyName: newApp.company,
          position: newApp.position,
          rawJd: newApp.rawJd,
          aiInsight: newApp.aiInsight,
          questions: newApp.questions
        })
      })

      if (!response.ok) throw new Error("공고 등록 실패")
      
      const savedApp = await response.json()
      
      const application = mapApplicationFromApi(savedApp)
      setLocalApplications([application, ...localApplications])
    } catch (err) {
      console.error(err)
      alert("공고 등록에 실패했습니다.")
    }
  }

  const handleUpdateApplication = async (id: string, updates: Partial<Application>) => {
    const previousApplications = localApplications
    try {
      setLocalApplications(prev => prev.map(app => 
        app.id === id ? { ...app, ...updates } : app
      ))

      const response = await fetch(`/api/applications/${id}`, {
        method: "PUT",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(updates)
      })
      if (!response.ok) throw new Error("Update failed")
      const savedApp = mapApplicationFromApi(await response.json())
      setLocalApplications(prev => prev.map(app => (
        app.id === id ? { ...app, ...savedApp } : app
      )))
    } catch (err) {
      console.error(err)
      setLocalApplications(previousApplications)
      await fetchApplications()
    }
  }

  const handleStatusChange = async (id: string, newStatus: ApplicationStatus) => {
    handleUpdateApplication(id, { status: newStatus })
    try {
      const response = await fetch(`/api/applications/${id}`, {
        method: "PUT",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ status: newStatus.toUpperCase() })
      })
      if (!response.ok) throw new Error("Status update failed")
    } catch (err) {
      console.error(err)
      fetchApplications()
    }
  }

  const onDragEnd = (result: DropResult) => {
    const { destination, source, draggableId } = result
    if (!destination) return
    if (destination.droppableId === source.droppableId && destination.index === source.index) return
    const newStatus = destination.droppableId as ApplicationStatus
    handleStatusChange(draggableId, newStatus)
  }

  if (isLoading) {
    return (
      <div className="flex h-[400px] w-full items-center justify-center backdrop-blur-sm bg-background/50 rounded-3xl border border-border/50 shadow-inner">
        <div className="flex flex-col items-center gap-4">
          <div className="relative">
            <div className="absolute inset-0 bg-primary/20 rounded-full animate-ping scale-150" />
            <Loader2 className="size-10 text-primary animate-spin relative" />
          </div>
          <p className="text-sm font-medium text-muted-foreground animate-pulse">공고 목록을 불러오는 중...</p>
        </div>
      </div>
    )
  }

  return (
    <div className="flex flex-col h-full">
      <div className="flex items-center justify-between mb-4">
        <div className="flex items-center gap-2">
          {columns.map((col, idx) => (
            <div key={col.id} className="flex items-center">
              <span className="text-xs text-muted-foreground px-2 py-1 rounded bg-muted/50">
                {col.title}
              </span>
              {idx < columns.length - 1 && (
                <ChevronRight className="size-3 text-muted-foreground/50 mx-1" />
              )}
            </div>
          ))}
        </div>
        <div className="flex items-center gap-4">
          <Button 
            variant="ghost" 
            size="sm" 
            onClick={() => setShowFailed(!showFailed)}
            className={`gap-2 text-[10px] uppercase font-bold tracking-tighter transition-all ${showFailed ? "text-muted-foreground" : "text-red-500 bg-red-50 hover:bg-red-100 dark:bg-red-950/20"}`}
          >
            {showFailed ? (
              <><Eye className="size-3.5" /> 불합격 공고 표시 중</>
            ) : (
              <><EyeOff className="size-3.5" /> 불합격 공고 숨김</>
            )}
          </Button>
          <Button onClick={() => setIsAddDialogOpen(true)} className="gap-2 shadow-md">
            <Plus className="size-4" />
            새 공고 추가
          </Button>
        </div>
      </div>

      <DragDropContext onDragEnd={onDragEnd}>
        <div className="flex gap-4 pb-4 flex-1 min-h-0 w-full">
          {columns.map((column) => (
            <div key={column.id} className="flex-1 min-w-0 h-full">
              <KanbanColumn
                column={column}
                applications={sortedApplications.filter((app) => 
                  app.status === column.id && (showFailed || app.result !== "fail")
                )}
                onCardClick={handleCardClick}
              />
            </div>
          ))}
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
    </div>
  )
}
