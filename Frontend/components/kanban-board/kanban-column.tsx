"use client"

import { ScrollArea } from "@/components/ui/scroll-area"
import { Badge } from "@/components/ui/badge"
import { Trophy } from "lucide-react"
import { type Application, type ApplicationStatus } from "@/lib/mock-data"
import { ApplicationCard } from "./application-card"

export interface ColumnDefinition {
  id: ApplicationStatus;
  title: string;
  color: string;
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

  return (
    <div className={`flex w-72 shrink-0 flex-col rounded-xl border ${
      isPassed 
        ? "bg-gradient-to-b from-amber-50/50 to-card border-amber-200 dark:from-amber-950/20 dark:border-amber-800" 
        : "bg-card border-border"
    }`}>
      <div className={`flex items-center gap-3 p-4 border-b ${
        isPassed ? "border-amber-200 dark:border-amber-800" : "border-border"
      }`}>
        {isPassed ? (
          <Trophy className="size-5 text-amber-500" />
        ) : (
          <div className={`size-3 rounded-full ${column.color}`} />
        )}
        <h2 className={`font-semibold text-sm ${isPassed ? "text-amber-700 dark:text-amber-400" : ""}`}>
          {column.title}
        </h2>
        <Badge variant={isPassed ? "default" : "secondary"} className={`ml-auto text-xs ${isPassed ? "bg-amber-500" : ""}`}>
          {applications.length}
        </Badge>
      </div>
      <ScrollArea className="flex-1 p-3 max-h-[calc(100vh-280px)]">
        <div className="space-y-3">
          {applications.map((app) => (
            <ApplicationCard 
              key={app.id} 
              application={app} 
              isPassed={isPassed}
              onClick={() => onCardClick(app)}
            />
          ))}
          {applications.length === 0 && (
            <div className={`flex h-32 items-center justify-center rounded-lg border border-dashed ${
              isPassed ? "border-amber-300 dark:border-amber-700" : "border-border"
            }`}>
              <p className="text-sm text-muted-foreground">
                {isPassed ? "아직 합격 없음" : "지원 내역 없음"}
              </p>
            </div>
          )}
        </div>
      </ScrollArea>
    </div>
  )
}
