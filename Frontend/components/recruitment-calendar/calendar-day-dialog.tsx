"use client"

import { useMemo } from "react"
import {
  eachDayOfInterval,
  endOfWeek,
  format,
  isSameDay,
  isToday,
  startOfWeek,
} from "date-fns"
import { ko } from "date-fns/locale"
import {
  CalendarClock,
  ChevronRight,
  Lightbulb,
  NotebookPen,
} from "lucide-react"
import { motion } from "framer-motion"
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
import { getDaysUntil } from "./planning-utils"

interface CalendarDayDialogProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  date: Date | null
  dailyEvents: CalendarEvent[]
  weeklyEvents: CalendarEvent[]
  onJumpToDate: (date: Date) => void
}

const DAY_LABELS = ["일", "월", "화", "수", "목", "금", "토"]

/** D-Day 문자열 → 긴박도별 스타일 */
function getDDayBadgeClass(dDay: string) {
  if (dDay === "D-Day") return "border-red-300 bg-red-500 text-white"
  if (dDay === "D-1") return "border-red-200 bg-red-50 text-red-600"
  if (dDay === "D-2" || dDay === "D-3") return "border-amber-200 bg-amber-50 text-amber-700"
  if (dDay.startsWith("D+")) return "border-border bg-muted/50 text-muted-foreground"
  return "border-border bg-background text-foreground"
}

/** 이번 주 데이터 기반 전략 인사이트 생성 */
function buildWeekInsight(weeklyEvents: CalendarEvent[], selectedDate: Date) {
  const pendingEvents = weeklyEvents.filter((e) => e.result === "pending")
  const busyDayKeys = new Set(pendingEvents.map((e) => format(e.date, "yyyy-MM-dd")))
  const freeDays = 7 - busyDayKeys.size

  const urgentCount = pendingEvents.filter((e) => {
    const diff = getDaysUntil(e.date, selectedDate)
    return diff >= 0 && diff <= 3
  }).length

  const hasInterview = pendingEvents.some((e) => e.type === "interview")
  const hasDeadline = pendingEvents.some((e) => e.type === "deadline")
  const deadlineCount = pendingEvents.filter((e) => e.type === "deadline").length

  if (urgentCount >= 2)
    return {
      icon: "⚡",
      label: "긴급",
      color: "border-red-100 bg-red-50 text-red-700",
      iconColor: "text-red-400",
      text: `D-3 이내 일정이 ${urgentCount}건 임박했습니다. 오늘 안에 우선순위를 정하고 준비를 시작하세요.`,
    }

  if (hasInterview && hasDeadline)
    return {
      icon: "🎯",
      label: "집중 주간",
      color: "border-violet-100 bg-violet-50 text-violet-700",
      iconColor: "text-violet-400",
      text: `서류 마감과 면접이 겹친 주입니다. 여유일 ${freeDays}일 안에 우선순위를 명확히 잡아두세요.`,
    }

  if (hasInterview)
    return {
      icon: "💬",
      label: "면접 주간",
      color: "border-amber-100 bg-amber-50 text-amber-700",
      iconColor: "text-amber-400",
      text: `면접이 있는 주입니다. 전날은 반드시 리뷰 세션을 확보하고 당일 컨디션 관리에 집중하세요.`,
    }

  if (deadlineCount >= 2)
    return {
      icon: "📋",
      label: "마감 집중",
      color: "border-blue-100 bg-blue-50 text-blue-700",
      iconColor: "text-blue-400",
      text: `마감이 ${deadlineCount}건 몰려 있습니다. 가장 이른 마감부터 자소서 완성도를 높이세요.`,
    }

  if (freeDays >= 5)
    return {
      icon: "✨",
      label: "준비 적기",
      color: "border-emerald-100 bg-emerald-50 text-emerald-700",
      iconColor: "text-emerald-400",
      text: `이번 주는 여유롭습니다. 다음 주 마감을 앞당겨 초안을 완성해 두면 후반 압박이 줄어듭니다.`,
    }

  return {
    icon: "📅",
    label: "주간 현황",
    color: "border-border bg-muted/30 text-foreground",
    iconColor: "text-muted-foreground",
    text: `이번 주 ${weeklyEvents.length}건의 일정이 있습니다. 일정 사이 빈 블록에 자소서와 준비를 나눠 잡으세요.`,
  }
}

/** 미니 주간 타임라인 */
function WeekTimeline({
  weeklyEvents,
  selectedDate,
  onJumpToDate,
}: {
  weeklyEvents: CalendarEvent[]
  selectedDate: Date
  onJumpToDate: (date: Date) => void
}) {
  const weekDays = useMemo(
    () =>
      eachDayOfInterval({
        start: startOfWeek(selectedDate, { weekStartsOn: 0 }),
        end: endOfWeek(selectedDate, { weekStartsOn: 0 }),
      }),
    [selectedDate]
  )

  return (
    <div className="grid grid-cols-7 gap-1">
      {weekDays.map((day, idx) => {
        const dayEvents = weeklyEvents.filter((e) => isSameDay(e.date, day))
        const isSelected = isSameDay(day, selectedDate)
        const isTodayDay = isToday(day)
        const hasEvents = dayEvents.length > 0

        return (
          <button
            key={day.toISOString()}
            type="button"
            onClick={() => hasEvents && !isSelected && onJumpToDate(day)}
            className={cn(
              "group flex flex-col items-center gap-1.5 rounded-xl py-2 transition-colors",
              hasEvents && !isSelected && "hover:bg-muted/60 cursor-pointer",
              !hasEvents && "cursor-default",
              isSelected && "bg-foreground/5 ring-1 ring-foreground/15"
            )}
          >
            <span
              className={cn(
                "text-[10px] font-medium leading-none",
                idx === 0 ? "text-red-400" : idx === 6 ? "text-blue-400" : "text-muted-foreground/60"
              )}
            >
              {DAY_LABELS[idx]}
            </span>
            <span
              className={cn(
                "flex h-6 w-6 items-center justify-center rounded-full text-[11px] font-semibold leading-none transition-colors",
                isTodayDay
                  ? "bg-foreground text-background"
                  : isSelected
                    ? "bg-foreground/10 text-foreground"
                    : "text-muted-foreground"
              )}
            >
              {format(day, "d")}
            </span>
            <div className="flex min-h-[10px] flex-wrap justify-center gap-0.5">
              {dayEvents.slice(0, 3).map((event) => (
                <span
                  key={event.id}
                  className={cn("size-1.5 rounded-full", eventTypeConfig[event.type].dotColor)}
                />
              ))}
              {dayEvents.length === 0 && (
                <span className="size-1 rounded-full bg-border/30" />
              )}
            </div>
          </button>
        )
      })}
    </div>
  )
}

const cardVariants = {
  hidden: { opacity: 0, y: 8 },
  visible: (i: number) => ({
    opacity: 1,
    y: 0,
    transition: { duration: 0.22, delay: i * 0.07, ease: "easeOut" },
  }),
}

export function CalendarDayDialog({
  open,
  onOpenChange,
  date,
  dailyEvents,
  weeklyEvents,
  onJumpToDate,
}: CalendarDayDialogProps) {
  const otherWeekEvents = useMemo(
    () =>
      weeklyEvents
        .filter((e) => !date || !isSameDay(e.date, date))
        .slice(0, 5),
    [weeklyEvents, date]
  )

  const insight = useMemo(
    () => (date ? buildWeekInsight(weeklyEvents, date) : null),
    [weeklyEvents, date]
  )

  const dialogSubtitle = useMemo(() => {
    if (!date || dailyEvents.length === 0) return "이번 주 주간 맥락을 함께 확인하세요"
    const urgent = dailyEvents.filter((e) => {
      const d = getDaysUntil(e.date, date)
      return e.result === "pending" && d >= 0 && d <= 3
    })
    if (urgent.length > 0)
      return `${urgent[0].company} 포함 ${urgent.length}건이 D-3 이내입니다 — 지금 준비하세요`
    return `${dailyEvents.length}건의 일정 · 이번 주 흐름을 함께 확인하세요`
  }, [date, dailyEvents])

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent
        showCloseButton
        className="max-h-[90vh] overflow-hidden rounded-[28px] border border-border/70 bg-background p-0 shadow-2xl sm:max-w-[1040px]"
      >
        {date ? (
          <div className="grid h-full max-h-[90vh] lg:grid-cols-[minmax(0,1.4fr)_300px]">

            {/* ── 왼쪽: 하루 일정 상세 ── */}
            <div className="flex min-h-0 flex-col">
              <div className="border-b border-border/60 px-6 py-5 sm:px-7">
                <div className="flex items-center gap-2">
                  <Badge
                    variant="secondary"
                    className="rounded-full px-2.5 py-0.5 text-[11px] font-medium"
                  >
                    {format(date, "M월 d일", { locale: ko })}
                  </Badge>
                  <Badge
                    variant="outline"
                    className="rounded-full px-2.5 py-0.5 text-[11px]"
                  >
                    {dailyEvents.length}건
                  </Badge>
                </div>

                <DialogTitle className="mt-3 text-2xl font-bold tracking-tight">
                  {format(date, "EEEE", { locale: ko })}의 일정
                </DialogTitle>
                <DialogDescription className="mt-1.5 text-sm text-muted-foreground">
                  {dialogSubtitle}
                </DialogDescription>
              </div>

              <ScrollArea className="min-h-0 flex-1">
                <div className="space-y-3 px-6 py-5 sm:px-7">
                  {dailyEvents.map((event, i) => {
                    const config = eventTypeConfig[event.type]
                    const status = statusConfig[event.status]
                    const dDay = getEventDDay(event.date)
                    const isUrgent =
                      dDay === "D-Day" || dDay === "D-1" || dDay === "D-2" || dDay === "D-3"

                    return (
                      <motion.article
                        key={event.id}
                        custom={i}
                        variants={cardVariants}
                        initial="hidden"
                        animate="visible"
                        className={cn(
                          "relative overflow-hidden rounded-[20px] border bg-white",
                          isUrgent
                            ? "border-amber-100 shadow-[0_4px_24px_-8px_rgba(251,191,36,0.25)]"
                            : "border-border/60 shadow-sm"
                        )}
                      >
                        {/* 이벤트 타입 색상 액센트 바 */}
                        <div
                          className={cn(
                            "absolute left-0 top-0 h-full w-1 rounded-l-[20px]",
                            config.dotColor.replace("bg-", "bg-")
                          )}
                        />

                        <div className="px-5 py-4 pl-6">
                          {/* 헤더 행 */}
                          <div className="flex items-start justify-between gap-3">
                            <div className="flex min-w-0 items-center gap-3">
                              <div
                                className={cn(
                                  "flex h-10 w-10 shrink-0 items-center justify-center rounded-[14px] text-sm font-bold text-white",
                                  event.logoColor
                                )}
                              >
                                {event.company.slice(0, 1)}
                              </div>
                              <div className="min-w-0">
                                <div className="flex flex-wrap items-center gap-1.5">
                                  <h4 className="text-base font-semibold text-foreground">
                                    {event.company}
                                  </h4>
                                  <Badge
                                    variant="outline"
                                    className={cn(
                                      "rounded-full px-2 py-0.5 text-[11px] font-medium",
                                      config.borderColor,
                                      config.color,
                                      config.bgColor
                                    )}
                                  >
                                    {config.label}
                                  </Badge>
                                  <Badge
                                    variant="secondary"
                                    className={cn(
                                      "rounded-full px-2 py-0.5 text-[11px]",
                                      status.bgColor,
                                      status.color
                                    )}
                                  >
                                    {status.label}
                                  </Badge>
                                </div>
                                <p className="mt-0.5 text-xs text-muted-foreground">
                                  {event.position}
                                </p>
                              </div>
                            </div>

                            {/* D-Day + 시간 */}
                            <div className="flex shrink-0 flex-col items-end gap-1">
                              <span
                                className={cn(
                                  "rounded-full border px-2.5 py-0.5 text-xs font-bold",
                                  getDDayBadgeClass(dDay)
                                )}
                              >
                                {dDay}
                              </span>
                              <span className="text-[11px] text-muted-foreground">
                                {format(event.date, "a h:mm", { locale: ko })}
                              </span>
                            </div>
                          </div>

                          {/* 메모 */}
                          {event.memo && (
                            <div className="mt-3 flex items-start gap-2 rounded-[14px] bg-slate-50 px-3.5 py-2.5">
                              <NotebookPen className="mt-px size-3.5 shrink-0 text-slate-400" />
                              <p className="text-xs leading-5 text-slate-600">{event.memo}</p>
                            </div>
                          )}
                        </div>
                      </motion.article>
                    )
                  })}
                </div>
              </ScrollArea>
            </div>

            {/* ── 오른쪽: 주간 전략 뷰 ── */}
            <aside className="flex min-h-0 flex-col border-t border-border/60 bg-muted/20 lg:border-t-0 lg:border-l">

              {/* 헤더 */}
              <div className="border-b border-border/50 px-5 py-4">
                <p className="text-[10px] font-semibold uppercase tracking-[0.2em] text-muted-foreground/50">
                  Week View
                </p>
                <h3 className="mt-1.5 text-base font-semibold">이번 주 전략</h3>
              </div>

              <ScrollArea className="min-h-0 flex-1">
                <div className="space-y-4 px-5 py-4">

                  {/* 주간 타임라인 */}
                  <div>
                    <p className="mb-2 text-[11px] font-medium text-muted-foreground/70">
                      주간 일정 분포
                    </p>
                    <WeekTimeline
                      weeklyEvents={weeklyEvents}
                      selectedDate={date}
                      onJumpToDate={(d) => {
                        onJumpToDate(d)
                      }}
                    />
                  </div>

                  {/* 인사이트 카드 */}
                  {insight && (
                    <motion.div
                      initial={{ opacity: 0, y: 6 }}
                      animate={{ opacity: 1, y: 0 }}
                      transition={{ duration: 0.25, delay: 0.15 }}
                      className={cn(
                        "rounded-[16px] border px-4 py-3.5",
                        insight.color
                      )}
                    >
                      <div className="flex items-start gap-2.5">
                        <Lightbulb className={cn("mt-0.5 size-4 shrink-0", insight.iconColor)} />
                        <div>
                          <p className="text-[11px] font-semibold uppercase tracking-wide opacity-70">
                            {insight.label}
                          </p>
                          <p className="mt-1 text-xs leading-5">{insight.text}</p>
                        </div>
                      </div>
                    </motion.div>
                  )}

                  {/* 이번 주 다른 일정 */}
                  {otherWeekEvents.length > 0 && (
                    <div>
                      <p className="mb-2 text-[11px] font-medium text-muted-foreground/70">
                        이번 주 다른 일정
                      </p>
                      <div className="space-y-1.5">
                        {otherWeekEvents.map((event) => {
                          const config = eventTypeConfig[event.type]
                          const dDay = getEventDDay(event.date)
                          return (
                            <button
                              key={event.id}
                              type="button"
                              onClick={() => onJumpToDate(event.date)}
                              className="group flex w-full items-center gap-3 rounded-[14px] border border-border/60 bg-background px-3.5 py-2.5 text-left transition-colors hover:bg-muted/50"
                            >
                              <div
                                className={cn(
                                  "flex h-7 w-7 shrink-0 items-center justify-center rounded-[10px] text-xs font-bold text-white",
                                  event.logoColor
                                )}
                              >
                                {event.company.slice(0, 1)}
                              </div>
                              <div className="min-w-0 flex-1">
                                <p className="truncate text-xs font-semibold">{event.company}</p>
                                <p className={cn("text-[10px]", config.color)}>
                                  {config.label} · {format(event.date, "M/d EEE", { locale: ko })}
                                </p>
                              </div>
                              <div className="flex shrink-0 items-center gap-1.5">
                                <span
                                  className={cn(
                                    "rounded-full border px-1.5 py-0.5 text-[9px] font-bold",
                                    getDDayBadgeClass(dDay)
                                  )}
                                >
                                  {dDay}
                                </span>
                                <ChevronRight className="size-3 text-muted-foreground opacity-0 transition-opacity group-hover:opacity-100" />
                              </div>
                            </button>
                          )
                        })}
                      </div>
                    </div>
                  )}

                  {otherWeekEvents.length === 0 && (
                    <div className="rounded-[14px] border border-dashed border-border/50 px-4 py-5 text-center">
                      <p className="text-xs text-muted-foreground">
                        이번 주 다른 일정이 없습니다.
                      </p>
                      <p className="mt-1 text-[11px] text-muted-foreground/60">
                        집중 준비 기간으로 활용하세요 🎯
                      </p>
                    </div>
                  )}
                </div>
              </ScrollArea>
            </aside>
          </div>
        ) : (
          <div className="flex items-center justify-center p-12">
            <CalendarClock className="size-8 text-muted-foreground/40" />
          </div>
        )}
      </DialogContent>
    </Dialog>
  )
}
