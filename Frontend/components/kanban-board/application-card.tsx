"use client"

import { Card, CardContent, CardHeader } from "@/components/ui/card"
import { Badge } from "@/components/ui/badge"
import { Progress } from "@/components/ui/progress"
import { GripVertical, PartyPopper, Trophy } from "lucide-react"
import { getDDay, calculateProgress, type Application } from "@/lib/mock-data"

export function ApplicationCard({ 
  application, 
  isPassed = false,
  onClick
}: { 
  application: Application
  isPassed?: boolean
  onClick: () => void
}) {
  const dDay = getDDay(application.deadline)
  const progress = calculateProgress(application.questions)
  const isUrgent = dDay === "D-Day" || (dDay.startsWith("D-") && parseInt(dDay.slice(2)) <= 3)
  const isDayPast = dDay.startsWith("D+")

  return (
    <Card 
      className={`group cursor-pointer transition-all hover:shadow-md ${
        isPassed 
          ? "border-2 border-amber-400 bg-gradient-to-br from-amber-50/80 to-yellow-50/50 dark:from-amber-950/30 dark:to-yellow-950/20 shadow-lg shadow-amber-200/50 dark:shadow-amber-900/30" 
          : "hover:border-primary/30"
      }`}
      onClick={onClick}
    >
      <CardHeader className="pb-3 relative">
        <div className="absolute left-0 top-1/2 -translate-y-1/2 -translate-x-1 opacity-0 group-hover:opacity-100 transition-opacity cursor-grab active:cursor-grabbing">
          <GripVertical className="size-4 text-muted-foreground" />
        </div>
        <div className="flex items-start justify-between gap-2">
          <div className="flex items-center gap-3">
            <div className={`size-10 rounded-lg ${application.logoColor} flex items-center justify-center relative`}>
              <span className="text-sm font-bold text-white">
                {application.company.charAt(0)}
              </span>
              {isPassed && (
                <div className="absolute -top-1 -right-1 size-5 bg-amber-400 rounded-full flex items-center justify-center">
                  <Trophy className="size-3 text-white" />
                </div>
              )}
            </div>
            <div>
              <h3 className="font-semibold leading-tight">{application.company}</h3>
              <p className="text-xs text-muted-foreground">{application.position}</p>
            </div>
          </div>
          {isPassed ? (
            <Badge className="shrink-0 text-xs bg-emerald-500 hover:bg-emerald-600 text-white">
              <PartyPopper className="size-3 mr-1" />
              합격
            </Badge>
          ) : (
            <Badge
              variant={isDayPast ? "secondary" : isUrgent ? "destructive" : "outline"}
              className="shrink-0 text-xs"
            >
              {dDay}
            </Badge>
          )}
        </div>
      </CardHeader>
      <CardContent className="space-y-4">
        <div className="flex flex-wrap gap-1.5">
          {application.techStack.map((tech) => (
            <Badge key={tech} variant="secondary" className="text-xs font-normal">
              {tech}
            </Badge>
          ))}
        </div>
        {!isPassed && (
          <div className="space-y-2">
            <div className="flex items-center justify-between text-xs">
              <span className="text-muted-foreground">질문 진행도</span>
              <span className="font-medium">{progress}%</span>
            </div>
            <Progress value={progress} className="h-2" />
            <div className="flex gap-1">
              {application.questions.map((q, idx) => {
                const qProgress = Math.round((q.currentLength / q.maxLength) * 100)
                return (
                  <div
                    key={q.id}
                    className="flex-1 h-1 rounded-full bg-muted overflow-hidden"
                    title={`Q${idx + 1}: ${q.title} (${qProgress}%)`}
                  >
                    <div
                      className="h-full bg-primary transition-all"
                      style={{ width: `${qProgress}%` }}
                    />
                  </div>
                )
              })}
            </div>
          </div>
        )}
        {isPassed && (
          <div className="flex items-center justify-center gap-2 pt-2 text-sm font-medium text-amber-600 dark:text-amber-400">
            <PartyPopper className="size-4" />
            축하합니다!
            <PartyPopper className="size-4" />
          </div>
        )}
      </CardContent>
    </Card>
  )
}
