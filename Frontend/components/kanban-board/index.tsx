"use client"

import { useState } from "react"
import { Button } from "@/components/ui/button"
import { ChevronRight, Plus } from "lucide-react"
import { mockApplications, type Application } from "@/lib/mock-data"
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

export function KanbanBoard() {
  const [selectedApplication, setSelectedApplication] = useState<Application | null>(null)
  const [isSheetOpen, setIsSheetOpen] = useState(false)

  const handleCardClick = (app: Application) => {
    setSelectedApplication(app)
    setIsSheetOpen(true)
  }

  const handleCloseSheet = () => {
    setIsSheetOpen(false)
    setSelectedApplication(null)
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
        <Button className="gap-2 shadow-md">
          <Plus className="size-4" />
          새 공고 추가
        </Button>
      </div>

      <div className="flex gap-3 overflow-x-auto pb-4 flex-1 scroll-smooth">
        {columns.map((column, index) => (
          <div key={column.id} className="flex items-start">
            <KanbanColumn
              column={column}
              applications={mockApplications.filter((app) => app.status === column.id)}
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
    </div>
  )
}
