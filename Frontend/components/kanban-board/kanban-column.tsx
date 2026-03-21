"use client"

import { ScrollArea } from "@/components/ui/scroll-area"
import { Badge } from "@/components/ui/badge"
import { Trophy } from "lucide-react"
import { type Application, type ApplicationStatus } from "@/lib/mock-data"
import { ApplicationCard } from "./application-card"
import { Droppable } from "@hello-pangea/dnd"

export interface ColumnDefinition {
  id: ApplicationStatus
  title: string
  color: string
}

export function KanbanColumn({
  column,
  applications,
  onCardClick,
}: {
  column: ColumnDefinition
  applications: Application[]
  onCardClick: (app: Application) => void
}) {
  const isPassed = column.id === "passed"
  const actionableCount = applications.filter(
    (application) => application.result !== "pass" && application.result !== "fail"
  ).length

  return (
    <div
      className={`flex h-full min-h-0 min-w-[280px] flex-1 shrink-0 flex-col overflow-hidden rounded-xl border ${
        isPassed
          ? "border-amber-200 bg-gradient-to-b from-amber-50/50 to-card dark:border-amber-800 dark:from-amber-950/20"
          : "border-border bg-card"
      }`}
    >
      <div
        className={`flex items-center gap-3 border-b p-4 ${
          isPassed ? "border-amber-200 dark:border-amber-800" : "border-border"
        }`}
      >
        {isPassed ? (
          <Trophy className="size-5 text-amber-500" />
        ) : (
          <div className={`size-3 rounded-full ${column.color}`} />
        )}
        <h2 className={`text-sm font-semibold ${isPassed ? "text-amber-700 dark:text-amber-400" : ""}`}>
          {column.title}
        </h2>
        <Badge variant={isPassed ? "default" : "secondary"} className={`ml-auto text-xs ${isPassed ? "bg-amber-500" : ""}`}>
          {applications.length}
        </Badge>
      </div>

      <div className="flex items-center justify-between border-b border-border/60 px-4 py-2 text-[11px] text-muted-foreground">
        <span>우선순위: 미완료, 마감 임박 순</span>
        <span>{actionableCount}개 진행 필요</span>
      </div>

      <ScrollArea className="min-h-0 flex-1">
        <Droppable droppableId={column.id}>
          {(provided) => (
            <div
              {...provided.droppableProps}
              ref={provided.innerRef}
              className="min-h-full space-y-3 p-3"
            >
              {applications.map((app, index) => (
                <ApplicationCard
                  key={app.id}
                  application={app}
                  isPassed={isPassed}
                  onClick={() => onCardClick(app)}
                  index={index}
                />
              ))}
              {provided.placeholder}
              {applications.length === 0 && (
                <div
                  className={`flex h-32 items-center justify-center rounded-lg border border-dashed ${
                    isPassed ? "border-amber-300 dark:border-amber-700" : "border-border"
                  }`}
                >
                  <p className="text-sm text-muted-foreground">
                    {isPassed ? "아직 최종 합격 공고가 없습니다." : "현재 이 단계의 공고가 없습니다."}
                  </p>
                </div>
              )}
            </div>
          )}
        </Droppable>
      </ScrollArea>
    </div>
  )
}
