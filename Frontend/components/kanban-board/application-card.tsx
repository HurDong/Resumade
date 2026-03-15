"use client"

import { Card, CardContent, CardHeader } from "@/components/ui/card"
import { Badge } from "@/components/ui/badge"
import { Progress } from "@/components/ui/progress"
import { GripVertical, PartyPopper, Trophy, XCircle, CheckCircle2 } from "lucide-react"
import { getDDay, calculateProgress, type Application } from "@/lib/mock-data"
import { Draggable } from "@hello-pangea/dnd"

export function ApplicationCard({ 
  application, 
  isPassed = false,
  onClick,
  index
}: { 
  application: Application
  isPassed?: boolean
  onClick: () => void
  index: number
}) {
  const dDay = getDDay(application.deadline)
  const progress = calculateProgress(application.questions)
  const isUrgent = dDay === "D-Day" || 
                  dDay.startsWith("오늘") || 
                  (dDay.startsWith("D-") && !isNaN(parseInt(dDay.slice(2))) && parseInt(dDay.slice(2)) <= 3)
  const isDayPast = dDay.startsWith("D+") || dDay === "마감됨"
  
  const isFailed = application.result === "fail"
  const isStagePassed = application.result === "pass" && !isPassed
  const isFinalPassed = isPassed // 'isPassed' prop comes from the 'passed' column status

  return (
    <Draggable draggableId={application.id} index={index}>
      {(provided, snapshot) => (
        <Card 
          ref={provided.innerRef}
          {...provided.draggableProps}
          {...provided.dragHandleProps}
          className={`group cursor-pointer transition-all hover:shadow-md ${
            snapshot.isDragging ? "shadow-2xl ring-2 ring-primary/20 rotate-1 scale-[1.02] z-50" : ""
          } ${
            isFailed 
              ? "grayscale opacity-70 border-dashed border-red-200 bg-red-50/10" 
              : isFinalPassed
                ? "border-2 border-amber-400 bg-gradient-to-br from-amber-50/80 to-yellow-50/50 dark:from-amber-950/30 dark:to-yellow-950/20 shadow-lg shadow-amber-200/50 dark:shadow-amber-900/30" 
                : isStagePassed || progress === 100
                  ? "border border-emerald-500/30 bg-emerald-50/10 dark:bg-emerald-950/10 shadow-sm shadow-emerald-500/10"
                  : "hover:border-primary/30"
          }`}
          onClick={onClick}
        >
          <div className="p-3">
            <div className="flex items-center gap-3 min-w-0">
              {/* Logo Section */}
              <div className={`shrink-0 size-9 rounded-md ${isFailed ? 'bg-muted' : application.logoColor} flex items-center justify-center relative shadow-sm overflow-hidden border border-border/40`}>
                {application.logoUrl ? (
                  <>
                    <img 
                      src={application.logoUrl} 
                      alt={application.company}
                      className="size-full object-cover transition-opacity duration-500 opacity-0"
                      onLoad={(e) => {
                        (e.target as HTMLImageElement).classList.remove('opacity-0');
                        if (e.currentTarget.nextElementSibling) {
                          (e.currentTarget.nextElementSibling as HTMLElement).style.display = 'none';
                        }
                      }}
                      onError={(e) => {
                        (e.currentTarget as HTMLImageElement).style.display = 'none';
                        if (e.currentTarget.nextElementSibling) {
                          (e.currentTarget.nextElementSibling as HTMLElement).style.display = 'flex';
                        }
                      }}
                    />
                    <span className="text-[13px] font-bold text-white uppercase flex items-center justify-center size-full">
                      {application.company.charAt(0)}
                    </span>
                  </>
                ) : (
                  <span className="text-[13px] font-bold text-white uppercase">
                    {application.company.charAt(0)}
                  </span>
                )}
                
                {/* Status indicator on logo */}
                {isFinalPassed && (
                  <div className="absolute -top-0.5 -right-0.5 size-3.5 bg-amber-400 rounded-full flex items-center justify-center shadow-xs border border-white dark:border-zinc-900 scale-in-center animate-in duration-300">
                    <Trophy className="size-2 text-white" />
                  </div>
                )}
                {isStagePassed && (
                  <div className="absolute -top-0.5 -right-0.5 size-3.5 bg-emerald-500 rounded-full flex items-center justify-center shadow-xs border border-white dark:border-zinc-900 scale-in-center animate-in duration-300">
                    <CheckCircle2 className="size-2 text-white" />
                  </div>
                )}
                {isFailed && (
                  <div className="absolute -top-0.5 -right-0.5 size-3.5 bg-red-400 rounded-full flex items-center justify-center shadow-xs border border-white dark:border-zinc-900 scale-in-center animate-in duration-300">
                    <XCircle className="size-2 text-white" />
                  </div>
                )}
              </div>

              {/* Info Section */}
              <div className="min-w-0 flex-1 flex flex-col justify-center">
                <div className="flex items-center justify-between gap-2">
                  <h3 className={`font-bold text-[13px] tracking-tight truncate ${isFailed ? 'text-muted-foreground/60 line-through' : 'text-foreground'}`}>
                    {application.company}
                  </h3>
                  
                  {/* Status Badge */}
                  <div className="shrink-0">
                    {isFinalPassed ? (
                      <Badge className="bg-amber-100 text-[9px] text-amber-700 border-amber-200 hover:bg-amber-100 px-1 h-4 font-bold animate-in fade-in zoom-in duration-300">
                        최종합격
                      </Badge>
                    ) : isStagePassed ? (
                      <Badge className="bg-emerald-50 text-[9px] text-emerald-600 border-emerald-100 hover:bg-emerald-50 px-1 h-4 font-bold animate-in fade-in zoom-in duration-300">
                        전형통과
                      </Badge>
                    ) : isFailed ? (
                      <Badge className="bg-red-50 text-[9px] text-red-500 border-red-100 px-1 h-4 font-bold">
                        탈락
                      </Badge>
                    ) : (
                      <Badge
                        variant="outline"
                        className={`text-[9px] px-1 h-4 font-bold ${
                          isUrgent 
                            ? "bg-red-50 text-red-600 border-red-200" 
                            : isDayPast 
                              ? "bg-zinc-50 text-zinc-400 border-zinc-200" 
                              : "bg-blue-50/50 text-primary border-blue-100"
                        }`}
                      >
                        {dDay}
                      </Badge>
                    )}
                  </div>
                </div>
                <p className="text-[11px] text-muted-foreground truncate leading-none mt-0.5">
                  {application.position}
                </p>
              </div>
            </div>

            {/* Progress Bar */}
            {!isFinalPassed && !isFailed && (
              <div className="mt-2 pt-1 border-t border-border/30">
                <div className="flex items-center justify-between mb-1">
                   <div className="flex gap-1 flex-1 h-[3px] mr-3">
                    {(application.questions || []).map((q) => {
                      const qProgress = q.maxLength ? Math.round(((q.currentLength || 0) / q.maxLength) * 100) : 0
                      return (
                        <div key={q.id} className="flex-1 rounded-full bg-zinc-100 dark:bg-zinc-800 overflow-hidden">
                          <div
                            className={`h-full transition-all ${q.isCompleted || progress === 100 || isStagePassed ? 'bg-emerald-400' : 'bg-primary/60'}`}
                            style={{ width: `${progress === 100 || isStagePassed ? 100 : qProgress}%` }}
                          />
                        </div>
                      )
                    })}
                  </div>
                  {progress === 100 || isStagePassed ? (
                    <div className="flex items-center gap-0.5 text-emerald-500 animate-in fade-in zoom-in duration-300">
                      <CheckCircle2 className="size-3" strokeWidth={3} />
                      <span className="text-[9px] font-black tracking-tighter">{isStagePassed ? "통과" : "완료"}</span>
                    </div>
                  ) : (
                    <span className="text-[9px] font-bold text-muted-foreground/70 shrink-0">{progress}%</span>
                  )}
                </div>
              </div>
            )}
            
            {isFinalPassed && (
              <div className="mt-1.5 flex items-center gap-1 text-[9px] font-bold text-amber-600/80 animate-in slide-in-from-left duration-500">
                <PartyPopper className="size-2.5" />
                <span>성공적인 도전을 축하합니다!</span>
              </div>
            )}
          </div>
        </Card>
      )}
    </Draggable>
  )
}
