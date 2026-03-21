"use client"

import { Card } from "@/components/ui/card"
import { Badge } from "@/components/ui/badge"
import { PartyPopper, Trophy, XCircle, CheckCircle2 } from "lucide-react"
import { getDDay, calculateProgress, type Application } from "@/lib/mock-data"
import { Draggable } from "@hello-pangea/dnd"

export function ApplicationCard({
  application,
  isPassed = false,
  onClick,
  index,
}: {
  application: Application
  isPassed?: boolean
  onClick: () => void
  index: number
}) {
  const dDay = getDDay(application.deadline)
  const progress = calculateProgress(application.questions)
  const isUrgent =
    dDay === "D-Day" ||
    dDay.startsWith("오늘") ||
    (dDay.startsWith("D-") &&
      !Number.isNaN(Number.parseInt(dDay.slice(2), 10)) &&
      Number.parseInt(dDay.slice(2), 10) <= 3)
  const isDayPast = dDay.startsWith("D+") || dDay === "마감"

  const isFailed = application.result === "fail"
  const isStagePassed = application.result === "pass" && !isPassed
  const isFinalPassed = isPassed

  return (
    <Draggable draggableId={application.id} index={index}>
      {(provided, snapshot) => (
        <Card
          ref={provided.innerRef}
          {...provided.draggableProps}
          {...provided.dragHandleProps}
          className={`group cursor-pointer transition-all hover:shadow-md ${
            snapshot.isDragging ? "z-50 rotate-1 scale-[1.02] shadow-2xl ring-2 ring-primary/20" : ""
          } ${
            isFailed
              ? "border-dashed border-red-200 bg-red-50/10 grayscale opacity-70"
              : isFinalPassed
                ? "border-2 border-amber-400 bg-gradient-to-br from-amber-50/80 to-yellow-50/50 shadow-lg shadow-amber-200/50 dark:from-amber-950/30 dark:to-yellow-950/20 dark:shadow-amber-900/30"
                : isStagePassed || progress === 100
                  ? "border border-emerald-500/30 bg-emerald-50/10 shadow-sm shadow-emerald-500/10 dark:bg-emerald-950/10"
                  : "hover:border-primary/30"
          }`}
          onClick={onClick}
        >
          <div className="p-3">
            <div className="flex min-w-0 items-center gap-3">
              <div
                className={`relative flex size-9 shrink-0 items-center justify-center overflow-hidden rounded-md border border-border/40 shadow-sm ${
                  isFailed ? "bg-muted" : application.logoColor
                }`}
              >
                {application.logoUrl ? (
                  <>
                    <img
                      src={application.logoUrl}
                      alt={application.company}
                      className="size-full object-cover opacity-0 transition-opacity duration-500"
                      onLoad={(event) => {
                        event.currentTarget.classList.remove("opacity-0")
                        if (event.currentTarget.nextElementSibling) {
                          ;(event.currentTarget.nextElementSibling as HTMLElement).style.display = "none"
                        }
                      }}
                      onError={(event) => {
                        event.currentTarget.style.display = "none"
                        if (event.currentTarget.nextElementSibling) {
                          ;(event.currentTarget.nextElementSibling as HTMLElement).style.display = "flex"
                        }
                      }}
                    />
                    <span className="flex size-full items-center justify-center text-[13px] font-bold uppercase text-white">
                      {application.company.charAt(0)}
                    </span>
                  </>
                ) : (
                  <span className="text-[13px] font-bold uppercase text-white">
                    {application.company.charAt(0)}
                  </span>
                )}

                {isFinalPassed && (
                  <div className="absolute -right-0.5 -top-0.5 flex size-3.5 items-center justify-center rounded-full border border-white bg-amber-400 shadow-xs animate-in scale-in-center duration-300 dark:border-zinc-900">
                    <Trophy className="size-2 text-white" />
                  </div>
                )}
                {isStagePassed && (
                  <div className="absolute -right-0.5 -top-0.5 flex size-3.5 items-center justify-center rounded-full border border-white bg-emerald-500 shadow-xs animate-in scale-in-center duration-300 dark:border-zinc-900">
                    <CheckCircle2 className="size-2 text-white" />
                  </div>
                )}
                {isFailed && (
                  <div className="absolute -right-0.5 -top-0.5 flex size-3.5 items-center justify-center rounded-full border border-white bg-red-400 shadow-xs animate-in scale-in-center duration-300 dark:border-zinc-900">
                    <XCircle className="size-2 text-white" />
                  </div>
                )}
              </div>

              <div className="flex min-w-0 flex-1 flex-col justify-center">
                <div className="flex items-center justify-between gap-2">
                  <h3
                    className={`truncate text-[13px] font-bold tracking-tight ${
                      isFailed ? "text-muted-foreground/60 line-through" : "text-foreground"
                    }`}
                  >
                    {application.company}
                  </h3>

                  <div className="shrink-0">
                    {isFinalPassed ? (
                      <Badge className="h-4 border-amber-200 bg-amber-100 px-1 text-[9px] font-bold text-amber-700 hover:bg-amber-100 animate-in fade-in zoom-in duration-300">
                        최종 합격
                      </Badge>
                    ) : isStagePassed ? (
                      <Badge className="h-4 border-emerald-100 bg-emerald-50 px-1 text-[9px] font-bold text-emerald-600 hover:bg-emerald-50 animate-in fade-in zoom-in duration-300">
                        전형 통과
                      </Badge>
                    ) : isFailed ? (
                      <Badge className="h-4 border-red-100 bg-red-50 px-1 text-[9px] font-bold text-red-500">
                        불합격
                      </Badge>
                    ) : (
                      <Badge
                        variant="outline"
                        className={`h-4 px-1 text-[9px] font-bold ${
                          isUrgent
                            ? "border-red-200 bg-red-50 text-red-600"
                            : isDayPast
                              ? "border-zinc-200 bg-zinc-50 text-zinc-400"
                              : "border-blue-100 bg-blue-50/50 text-primary"
                        }`}
                      >
                        {dDay}
                      </Badge>
                    )}
                  </div>
                </div>
                <p className="mt-0.5 truncate text-[11px] leading-none text-muted-foreground">
                  {application.position}
                </p>
              </div>
            </div>

            {!isFinalPassed && !isFailed && (
              <div className="mt-2 border-t border-border/30 pt-1">
                <div className="mb-1 flex items-center justify-between">
                  <div className="mr-3 flex h-[3px] flex-1 gap-1">
                    {(application.questions || []).map((question) => {
                      const questionProgress = question.maxLength
                        ? Math.round(((question.currentLength || 0) / question.maxLength) * 100)
                        : 0

                      return (
                        <div
                          key={question.id}
                          className="flex-1 overflow-hidden rounded-full bg-zinc-100 dark:bg-zinc-800"
                        >
                          <div
                            className={`h-full transition-all ${
                              question.isCompleted || progress === 100 || isStagePassed
                                ? "bg-emerald-400"
                                : "bg-primary/60"
                            }`}
                            style={{ width: `${progress === 100 || isStagePassed ? 100 : questionProgress}%` }}
                          />
                        </div>
                      )
                    })}
                  </div>
                  {progress === 100 || isStagePassed ? (
                    <div className="flex items-center gap-0.5 text-emerald-500 animate-in fade-in zoom-in duration-300">
                      <CheckCircle2 className="size-3" strokeWidth={3} />
                      <span className="text-[9px] font-black tracking-tighter">
                        {isStagePassed ? "통과" : "완료"}
                      </span>
                    </div>
                  ) : (
                    <span className="shrink-0 text-[9px] font-bold text-muted-foreground/70">
                      {progress}%
                    </span>
                  )}
                </div>
              </div>
            )}

            {isFinalPassed && (
              <div className="mt-1.5 flex items-center gap-1 text-[9px] font-bold text-amber-600/80 animate-in slide-in-from-left duration-500">
                <PartyPopper className="size-2.5" />
                <span>최종 합격 상태입니다.</span>
              </div>
            )}
          </div>
        </Card>
      )}
    </Draggable>
  )
}
