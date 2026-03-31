"use client"

import { format, isSameDay } from "date-fns"
import { ko } from "date-fns/locale"
import {
  CalendarClock,
  ChevronRight,
  Layers3,
  NotebookPen,
  Sparkles,
} from "lucide-react"
import { Badge } from "@/components/ui/badge"
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogTitle,
} from "@/components/ui/dialog"
import { ScrollArea } from "@/components/ui/scroll-area"
import { cn } from "@/lib/utils"
import {
  type CalendarEvent,
  eventTypeConfig,
  getEventDDay,
  statusConfig,
} from "./calendar-mock-data"
import { getDaysUntil, getTypeCounts } from "./planning-utils"

interface CalendarDayDialogProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  date: Date | null
  dailyEvents: CalendarEvent[]
  weeklyEvents: CalendarEvent[]
  onJumpToDate: (date: Date) => void
}

export function CalendarDayDialog({
  open,
  onOpenChange,
  date,
  dailyEvents,
  weeklyEvents,
  onJumpToDate,
}: CalendarDayDialogProps) {
  const counts = getTypeCounts(dailyEvents)
  const relatedEvents = weeklyEvents.filter((event) => !date || !isSameDay(event.date, date)).slice(0, 6)

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent
        showCloseButton
        className="max-h-[88vh] overflow-hidden rounded-[32px] border border-border/70 bg-background p-0 shadow-2xl sm:max-w-[1080px]"
      >
        {date ? (
          <div className="grid h-full max-h-[88vh] lg:grid-cols-[minmax(0,1.35fr)_320px]">
            <div className="flex min-h-0 flex-col">
              <div className="border-b border-border/60 px-6 py-6 sm:px-8">
                <div className="flex flex-wrap items-center gap-2">
                  <Badge variant="secondary" className="rounded-full px-3 py-1 text-[11px]">
                    Day Detail
                  </Badge>
                  <Badge variant="outline" className="rounded-full px-3 py-1 text-[11px]">
                    {dailyEvents.length}건
                  </Badge>
                </div>

                <DialogTitle className="mt-4 text-3xl font-semibold tracking-tight" style={{ textWrap: "balance" }}>
                  {format(date, "M월 d일 EEEE", { locale: ko })}
                </DialogTitle>
                <DialogDescription className="mt-3 max-w-2xl text-sm leading-6 text-muted-foreground">
                  하루 일정은 크게 띄우고, 같은 주간에 이어지는 일정은 옆에서 같이 확인할 수 있게 정리했습니다.
                </DialogDescription>
              </div>

              <ScrollArea className="min-h-0 flex-1">
                <div className="space-y-4 px-6 py-6 sm:px-8">
                  {dailyEvents.map((event) => {
                    const config = eventTypeConfig[event.type]
                    const status = statusConfig[event.status]
                    const dDay = getEventDDay(event.date)

                    return (
                      <article
                        key={event.id}
                        className="rounded-[28px] border border-border/70 bg-white px-5 py-5 shadow-[0_18px_50px_-32px_rgba(15,23,42,0.28)]"
                      >
                        <div className="flex flex-col gap-4 sm:flex-row sm:items-start sm:justify-between">
                          <div className="flex min-w-0 gap-4">
                            <div
                              className={cn(
                                "mt-0.5 flex h-12 w-12 shrink-0 items-center justify-center rounded-[18px] text-sm font-semibold text-white shadow-inner",
                                event.logoColor
                              )}
                            >
                              {event.company.slice(0, 1)}
                            </div>

                            <div className="min-w-0">
                              <div className="flex flex-wrap items-center gap-2">
                                <h4 className="truncate text-lg font-semibold text-foreground">{event.company}</h4>
                                <Badge
                                  variant="outline"
                                  className={cn(
                                    "rounded-full px-2.5 py-1 text-[11px] font-medium",
                                    config.borderColor,
                                    config.color,
                                    config.bgColor
                                  )}
                                >
                                  {config.label}
                                </Badge>
                                <Badge
                                  variant="secondary"
                                  className={cn("rounded-full px-2.5 py-1 text-[11px] font-medium", status.bgColor, status.color)}
                                >
                                  {status.label}
                                </Badge>
                              </div>

                              <p className="mt-2 truncate text-sm text-muted-foreground">{event.position}</p>
                            </div>
                          </div>

                          <div className="flex shrink-0 flex-row items-center gap-2 sm:flex-col sm:items-end">
                            <Badge variant="outline" className="rounded-full px-3 py-1 text-[11px] font-semibold">
                              {dDay}
                            </Badge>
                            <span className="text-xs text-muted-foreground">
                              {format(event.date, "a h:mm", { locale: ko })}
                            </span>
                          </div>
                        </div>

                        {event.memo ? (
                          <div className="mt-4 rounded-[22px] border border-slate-200 bg-slate-50 px-4 py-3">
                            <div className="flex items-start gap-2 text-sm text-slate-600">
                              <NotebookPen className="mt-0.5 size-4 shrink-0 text-slate-400" />
                              <p className="leading-6">{event.memo}</p>
                            </div>
                          </div>
                        ) : null}
                      </article>
                    )
                  })}
                </div>
              </ScrollArea>
            </div>

            <aside className="flex min-h-0 flex-col border-t border-border/60 bg-[linear-gradient(180deg,rgba(15,23,42,0.96),rgba(30,41,59,0.98))] text-white lg:border-t-0 lg:border-l">
              <div className="border-b border-white/10 px-6 py-6">
                <p className="text-[11px] font-medium uppercase tracking-[0.24em] text-white/55">
                  Same Week
                </p>
                <h3 className="mt-3 text-lg font-semibold">같은 주간에 이어지는 일정</h3>
                <p className="mt-2 text-sm leading-6 text-white/70">
                  선택한 날짜 전후로 겹치는 일정까지 같이 보면 글쓰기와 테스트 준비 블록을 더 정확하게 나눌 수 있습니다.
                </p>
              </div>

              <div className="grid grid-cols-2 gap-2 px-6 py-5">
                {(["deadline", "codingTest", "aptitude", "interview"] as const).map((type) => (
                  <div key={type} className="rounded-[20px] border border-white/10 bg-white/5 px-3 py-3">
                    <p className="text-[10px] uppercase tracking-[0.18em] text-white/50">
                      {eventTypeConfig[type].label}
                    </p>
                    <p className="mt-2 text-xl font-semibold">{counts[type]}</p>
                  </div>
                ))}
              </div>

              <ScrollArea className="min-h-0 flex-1 px-4 pb-4">
                {relatedEvents.length > 0 ? (
                  <div className="space-y-2 px-2">
                    {relatedEvents.map((event) => (
                      <button
                        key={event.id}
                        type="button"
                        onClick={() => onJumpToDate(event.date)}
                        className="w-full rounded-[22px] border border-white/10 bg-white/5 px-4 py-3 text-left transition-colors hover:bg-white/10"
                      >
                        <div className="flex items-center justify-between gap-3">
                          <div className="min-w-0">
                            <p className="truncate text-sm font-semibold text-white">{event.company}</p>
                            <p className="mt-1 truncate text-xs text-white/60">
                              {format(event.date, "M월 d일 EEE a h:mm", { locale: ko })}
                            </p>
                          </div>
                          <ChevronRight className="size-4 shrink-0 text-white/50" />
                        </div>
                      </button>
                    ))}
                  </div>
                ) : (
                  <div className="px-2 py-6 text-sm text-white/55">
                    같은 주간 추가 일정은 없습니다.
                  </div>
                )}
              </ScrollArea>

              <div className="border-t border-white/10 px-6 py-5">
                <div className="flex items-start gap-3 rounded-[22px] border border-sky-400/20 bg-sky-400/10 px-4 py-4">
                  <Sparkles className="mt-0.5 size-4 shrink-0 text-sky-300" />
                  <div>
                    <p className="text-sm font-medium">일정 간격</p>
                    <p className="mt-1 text-xs leading-5 text-white/70">
                      다음 일정까지 {relatedEvents[0] ? Math.max(getDaysUntil(relatedEvents[0].date, date), 0) : 0}일 남았습니다.
                    </p>
                  </div>
                </div>
                <div className="mt-3 flex items-center gap-2 text-xs text-white/55">
                  <Layers3 className="size-3.5" />
                  <span>날짜를 바꾸면 모달 내용도 바로 갱신됩니다.</span>
                </div>
              </div>
            </aside>
          </div>
        ) : (
          <div className="p-6">
            <CalendarClock className="size-5 text-muted-foreground" />
          </div>
        )}
      </DialogContent>
    </Dialog>
  )
}
