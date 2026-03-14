"use client"

import { useState } from "react"
import { Button } from "@/components/ui/button"
import { ChevronRight, Plus, Loader2 } from "lucide-react"
import { type Application } from "@/lib/mock-data"
import { KanbanColumn, type ColumnDefinition } from "./kanban-column"
import { ApplicationDetailSheet } from "./application-detail-sheet"

export const columns: ColumnDefinition[] = [
  { id: "document", title: "서류 전형", color: "bg-primary" },
  { id: "aptitude", title: "인적성/코딩테스트", color: "bg-chart-2" },
  { id: "interview1", title: "1차 면접", color: "bg-chart-3" },
  { id: "interview2", title: "2차 면접", color: "bg-chart-4" },
  { id: "passed", title: "최종 합격", color: "bg-emerald-500" },
]

function JourneyArrow() {
  return (
    <div className="flex items-center justify-center shrink-0 -mx-1">
      <ChevronRight className="size-5 text-muted-foreground/40" />
    </div>
  )
}

import { AddApplicationDialog } from "./add-application-dialog"
import { useEffect } from "react"

export function KanbanBoard() {
  const [selectedApplication, setSelectedApplication] = useState<Application | null>(null)
  const [isSheetOpen, setIsSheetOpen] = useState(false)
  const [isAddDialogOpen, setIsAddDialogOpen] = useState(false)
  const [localApplications, setLocalApplications] = useState<Application[]>([])
  const [isLoading, setIsLoading] = useState(true)

  const fetchApplications = async () => {
    setIsLoading(true)
    try {
      const response = await fetch("/api/applications")
      const data = await response.json()
      // Map backend data to frontend Application type
      const mapped = data.map((app: any) => ({
        id: app.id.toString(),
        company: app.companyName,
        position: app.position,
        status: "document",
        logoColor: "bg-blue-500",
        deadline: new Date(),
        techStack: [],
        rawJd: app.rawJd || "",
        aiInsight: app.aiInsight || "",
        questions: (app.questions || []).map((q: any) => ({
          id: q.id.toString(),
          title: q.title,
          maxLength: q.maxLength || 1000,
          currentLength: q.content?.length || 0
        }))
      }))
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

  const handleCardClick = (app: Application) => {
    setSelectedApplication(app)
    setIsSheetOpen(true)
  }

  const handleCloseSheet = () => {
    setIsSheetOpen(false)
    setSelectedApplication(null)
  }

  const handleAddApplication = async (newApp: { company: string; position: string; rawJd: string; questions: string[] }) => {
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
      
      const application: Application = {
        id: savedApp.id.toString(),
        company: savedApp.companyName,
        position: savedApp.position,
        status: "document",
        logoColor: "bg-primary",
        deadline: new Date(),
        techStack: [],
        rawJd: savedApp.rawJd || "",
        aiInsight: savedApp.aiInsight || "",
        questions: (savedApp.questions || []).map((q: any) => ({
          id: q.id.toString(),
          title: q.title,
          maxLength: q.maxLength || 1000,
          currentLength: q.content?.length || 0
        }))
      }
      setLocalApplications([application, ...localApplications])
    } catch (err) {
      console.error(err)
      alert("공고 등록에 실패했습니다.")
    }
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
        <Button onClick={() => setIsAddDialogOpen(true)} className="gap-2 shadow-md">
          <Plus className="size-4" />
          새 공고 추가
        </Button>
      </div>

      <div className="flex gap-3 overflow-x-auto pb-4 flex-1 scroll-smooth">
        {columns.map((column, index) => (
          <div key={column.id} className="flex items-start">
            <KanbanColumn
              column={column}
              applications={localApplications.filter((app) => app.status === column.id)}
              onCardClick={handleCardClick}
            />
            {index < columns.length - 1 && <JourneyArrow />}
          </div>
        ))}
      </div>

      <ApplicationDetailSheet
        application={selectedApplication}
        isOpen={isSheetOpen}
        onClose={handleCloseSheet}
      />

      <AddApplicationDialog
        isOpen={isAddDialogOpen}
        onClose={() => setIsAddDialogOpen(false)}
        onAdd={handleAddApplication}
      />
    </div>
  )
}
