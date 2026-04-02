"use client"

import { useMemo, useState } from "react"
import {
  addMonths,
  eachDayOfInterval,
  endOfMonth,
  endOfWeek,
  format,
  isSameDay,
  isSameMonth,
  isToday,
  startOfDay,
  startOfMonth,
  startOfWeek,
  subMonths,
} from "date-fns"
import { ko } from "date-fns/locale"
import { AnimatePresence, motion } from "framer-motion"
import {
  BrainCircuit,
  Calendar as CalendarIcon,
  ChevronLeft,
  ChevronRight,
  Code2,
  FileText,
  TrendingUp,
  Users,
  Zap,
} from "lucide-react"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { cn } from "@/lib/utils"
import {
  type CalendarEventType,
  eventTypeConfig,
  formatDateKey,
  groupEventsByDate,
  mockCalendarEvents,
} from "./calendar-mock-data"
import { CalendarDayDialog } from "./calendar-day-dialog"
import { CalendarEventCard } from "./calendar-event-card"
import {
  getDaysUntil,
  getPriorityEvents,
  getTypeCounts,
  getWeeklyEvents,
} from "./planning-utils"

const WEEKDAYS = ["일", "월", "화", "수", "목", "금", "토"]

const filterOptions: { key: CalendarEventType | "all"; label: string; icon: React.ElementType }[] = [
  { key: "all", label: "전체", icon: CalendarIcon },
  { key: "deadline", label: "서류", icon: FileText },
  { key: "codingTest", label: "코테", icon: Code2 },
  { key: "aptitude", label: "인적성", icon: BrainCircuit },
  { key: "interview", label: "면접", icon: Users },
]

const calendarGridVariants = {
  hidden: {},
  visible: { transition: { staggerChildren: 0.012 } },
}
const calendarCellVariants = {
  hidden: { opacity: 0, y: 5 },
  visible: { opacity: 1, y: 0, transition: { duration: 0.18, ease: "easeOut" } },
}

export function RecruitmentCalendar() {
  const today = useMemo(() => startOfDay(new Date()), [])
  const [currentMonth, setCurrentMonth] = useState(new Date())
  const [selectedDate, setSelectedDate] = useState<Date | null>(today)
  const [activeFilter, setActiveFilter] = useState<CalendarEventType | "all">("all")
  const [dialogDate, setDialogDate] = useState<Date | null>(null)
  const [direction, setDirection] = useState(0)

  const filteredEvents = useMemo(() => {
    if (activeFilter === "all") return mockCalendarEvents
    return mockCalendarEvents.filter((e) => e.type === activeFilter)
  }, [activeFilter])

  const eventsByDate = useMemo(() => groupEventsByDate(filteredEvents), [filteredEvents])

  const calendarDays = useMemo(() => {
    const monthStart = startOfMonth(currentMonth)
    const monthEnd = endOfMonth(currentMonth)
    const calendarStart = startOfWeek(monthStart, { weekStartsOn: 0 })
    const calendarEnd = endOfWeek(monthEnd, { weekStartsOn: 0 })
    return eachDayOfInterval({ start: calendarStart, end: calendarEnd })
  }, [currentMonth])

  const monthStats = useMemo(() => {
    const monthStart = startOfMonth(currentMonth)
    const monthEnd = endOfMonth(currentMonth)
    const eventsInMonth = filteredEvents.filter((e) => e.date >= monthStart && e.date <= monthEnd)
    return {
      total: eventsInMonth.length,
      deadline: eventsInMonth.filter((e) => e.type === "deadline").length,
      codingTest: eventsInMonth.filter((e) => e.type === "codingTest").length,
      aptitude: eventsInMonth.filter((e) => e.type === "aptitude").length,
      interview: eventsInMonth.filter((e) => e.type === "interview").length,
    }
  }, [currentMonth, filteredEvents])

  const { upcoming, priority } = useMemo(
    () => getPriorityEvents(filteredEvents, today),
    [filteredEvents, today]
  )

  const urgentCount = useMemo(
    () => upcoming.filter((e) => getDaysUntil(e.date, today) <= 3).length,
    [today, upcoming]
  )

  const nextMilestone = priority[0] ?? null

  const monthMix = getTypeCounts(
    filteredEvents.filter(
      (e) => e.date >= startOfMonth(currentMonth) && e.date <= endOfMonth(currentMonth)
    )
  )

  const dialogDateKey = dialogDate ? formatDateKey(dialogDate) : null

  const dialogEvents = useMemo(() => {
    if (!dialogDateKey) return []
    return [...(eventsByDate.get(dialogDateKey) || [])].sort(
      (a, b) => a.date.getTime() - b.date.getTime()
    )
  }, [dialogDateKey, eventsByDate])

  const dialogWeeklyEvents = useMemo(() => {
    if (!dialogDate) return []
    return getWeeklyEvents(filteredEvents, dialogDate)
  }, [dialogDate, filteredEvents])

  const handlePrevMonth = () => {
    setDirection(-1)
    setCurrentMonth((prev) => subMonths(prev, 1))
  }

  const handleNextMonth = () => {
    setDirection(1)
    setCurrentMonth((prev) => addMonths(prev, 1))
  }

  const handleToday = () => {
    const now = new Date()
    setDirection(now > currentMonth ? 1 : -1)
    setCurrentMonth(now)
    setSelectedDate(now)
  }

  const openDateDetail = (date: Date) => {
    const key = formatDateKey(date)
    setSelectedDate(date)
    if (!isSameMonth(date, currentMonth)) setCurrentMonth(date)
    if ((eventsByDate.get(key) || []).length > 0) setDialogDate(date)
  }

  return (
    <>
      <div className="flex h-full min-h-0 flex-col gap-2.5">

        {/* ── 컴팩트 컨트롤 바 ── */}
        <motion.div
          initial={{ opacity: 0, y: -8 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.22 }}
          className="shrink-0 overflow-hidden rounded-[20px] border border-border/70 bg-background shadow-sm"
        >
          <div className="flex flex-wrap items-center justify-between gap-3 px-4 py-3">

            {/* 월 네비게이션 */}
            <div className="flex items-center gap-1.5">
              <Button
                variant="outline"
                size="icon"
                className="size-7 rounded-full"
                onClick={handlePrevMonth}
              >
                <ChevronLeft className="size-3.5" />
              </Button>

              <AnimatePresence mode="wait" initial={false}>
                <motion.span
                  key={format(currentMonth, "yyyy-MM")}
                  initial={{ y: direction * 8, opacity: 0 }}
                  animate={{ y: 0, opacity: 1 }}
                  exit={{ y: direction * -8, opacity: 0 }}
                  transition={{ duration: 0.15 }}
                  className="min-w-[120px] text-center text-base font-semibold tracking-tight"
                >
                  {format(currentMonth, "yyyy년 M월", { locale: ko })}
                </motion.span>
              </AnimatePresence>

              <Button
                variant="outline"
                size="icon"
                className="size-7 rounded-full"
                onClick={handleNextMonth}
              >
                <ChevronRight className="size-3.5" />
              </Button>

              <Button
                variant="ghost"
                size="sm"
                className="h-7 rounded-full px-2.5 text-xs"
                onClick={handleToday}
              >
                오늘
              </Button>
            </div>

            {/* 인라인 통계 */}
            <div className="flex items-center gap-2">
              <div className="flex items-center gap-1 rounded-full border border-border/60 bg-muted/40 px-2.5 py-1 text-xs">
                <TrendingUp className="size-3 text-muted-foreground" />
                <span className="font-semibold">{monthStats.total}</span>
                <span className="text-muted-foreground">건</span>
              </div>

              {urgentCount > 0 && (
                <div className="flex items-center gap-1 rounded-full border border-amber-200 bg-amber-50 px-2.5 py-1 text-xs font-semibold text-amber-700">
                  <Zap className="size-3" />
                  D-3 이내 {urgentCount}건
                </div>
              )}

              {nextMilestone && (
                <div className="hidden items-center gap-1.5 text-xs lg:flex">
                  <span className="text-muted-foreground">Next</span>
                  <span className="font-semibold">{nextMilestone.company}</span>
                  <Badge
                    variant="outline"
                    className="rounded-full px-1.5 py-0 text-[10px]"
                  >
                    D-{getDaysUntil(nextMilestone.date, today)}
                  </Badge>
                </div>
              )}
            </div>

            {/* 필터 */}
            <div className="flex flex-wrap gap-1">
              {filterOptions.map(({ key, label, icon: Icon }) => (
                <Button
                  key={key}
                  variant={activeFilter === key ? "default" : "ghost"}
                  size="sm"
                  className="h-7 gap-1 rounded-full px-2.5 text-xs"
                  onClick={() => setActiveFilter(key)}
                >
                  <Icon className="size-3" />
                  {label}
                  {key !== "all" && monthStats[key as keyof typeof monthStats] > 0 && (
                    <span className="opacity-60">
                      {monthStats[key as keyof typeof monthStats]}
                    </span>
                  )}
                </Button>
              ))}
            </div>
          </div>

          {/* 타입 범례 */}
          {(["deadline", "codingTest", "aptitude", "interview"] as const).some(
            (t) => monthMix[t] > 0
          ) && (
            <div className="flex flex-wrap items-center gap-x-3 gap-y-1 border-t border-border/40 bg-muted/20 px-4 py-1.5 text-[11px] text-muted-foreground">
              {(["deadline", "codingTest", "aptitude", "interview"] as const).map((type) =>
                monthMix[type] > 0 ? (
                  <span key={type} className="inline-flex items-center gap-1">
                    <span className={`size-1.5 rounded-full ${eventTypeConfig[type].dotColor}`} />
                    {eventTypeConfig[type].label} {monthMix[type]}
                  </span>
                ) : null
              )}
              <span className="ml-auto text-muted-foreground/50">날짜를 클릭하면 상세 보기</span>
            </div>
          )}
        </motion.div>

        {/* ── 풀-하이트 캘린더 그리드 ── */}
        <motion.section
          initial={{ opacity: 0, y: 10 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.26, delay: 0.05 }}
          className="flex min-h-0 flex-1 flex-col overflow-hidden rounded-[20px] border border-border/70 bg-background shadow-sm"
        >
          {/* 요일 헤더 */}
          <div className="grid shrink-0 grid-cols-7 border-b border-border/50">
            {WEEKDAYS.map((day, idx) => (
              <div
                key={day}
                className={cn(
                  "py-2 text-center text-[11px] font-medium tracking-wide",
                  idx === 0
                    ? "text-red-400"
                    : idx === 6
                      ? "text-blue-400"
                      : "text-muted-foreground"
                )}
              >
                {day}
              </div>
            ))}
          </div>

          {/* 날짜 그리드 — 남은 높이를 균등 분배 */}
          <div className="min-h-0 flex-1 bg-muted/15 p-1.5">
            <AnimatePresence mode="wait" initial={false}>
              <motion.div
                key={format(currentMonth, "yyyy-MM")}
                className="grid h-full grid-cols-7 gap-1"
                style={{ gridAutoRows: "minmax(0, 1fr)" }}
                variants={calendarGridVariants}
                initial="hidden"
                animate="visible"
                exit={{ opacity: 0 }}
              >
                {calendarDays.map((day) => {
                  const dateKey = formatDateKey(day)
                  const dayEvents = [...(eventsByDate.get(dateKey) || [])].sort(
                    (a, b) => a.date.getTime() - b.date.getTime()
                  )
                  const isCurrentMonth = isSameMonth(day, currentMonth)
                  const isSelected = selectedDate ? isSameDay(day, selectedDate) : false
                  const isTodayDate = isToday(day)
                  const hasEvents = dayEvents.length > 0
                  const hasUrgentEvent = dayEvents.some((e) => {
                    const diff = getDaysUntil(e.date, today)
                    return e.result === "pending" && diff >= 0 && diff <= 3
                  })

                  return (
                    <motion.div
                      key={dateKey}
                      role="button"
                      tabIndex={0}
                      onClick={() => openDateDetail(day)}
                      onKeyDown={(e) => {
                        if (e.key === "Enter" || e.key === " ") openDateDetail(day)
                      }}
                      variants={calendarCellVariants}
                      className={cn(
                        "group flex flex-col overflow-hidden rounded-[14px] border p-1.5 transition-[transform,border-color,box-shadow] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring",
                        hasEvents ? "cursor-pointer" : "cursor-default",
                        !isCurrentMonth &&
                          "border-transparent bg-transparent opacity-40",
                        isCurrentMonth &&
                          !hasUrgentEvent &&
                          "border-slate-200/60 bg-white hover:-translate-y-0.5 hover:border-slate-300/80 hover:shadow-[0_4px_16px_-4px_rgba(15,23,42,0.14)]",
                        hasUrgentEvent &&
                          isCurrentMonth &&
                          "border-amber-100/80 bg-[linear-gradient(160deg,rgba(255,251,235,1),rgba(255,255,255,0.98))] hover:-translate-y-0.5 hover:shadow-[0_4px_16px_-4px_rgba(15,23,42,0.12)]",
                        isSelected &&
                          isCurrentMonth &&
                          "ring-2 ring-sky-300 ring-offset-1 ring-offset-muted/15"
                      )}
                    >
                      {/* 날짜 숫자 */}
                      <div className="flex shrink-0 items-center justify-between">
                        <span
                          className={cn(
                            "flex h-5 w-5 items-center justify-center rounded-full text-[11px] font-semibold leading-none",
                            isTodayDate
                              ? "bg-slate-950 text-white"
                              : !isCurrentMonth
                                ? "text-muted-foreground/50"
                                : "text-slate-800"
                          )}
                        >
                          {format(day, "d")}
                        </span>
                        {dayEvents.length > 0 && (
                          <span className="text-[9px] leading-none text-muted-foreground/50">
                            {dayEvents.length}
                          </span>
                        )}
                      </div>

                      {/* 이벤트 칩 */}
                      {hasEvents && (
                        <div className="mt-0.5 flex min-h-0 flex-1 flex-col gap-0.5 overflow-hidden">
                          {dayEvents.slice(0, 3).map((event) => (
                            <CalendarEventCard key={event.id} event={event} compact />
                          ))}
                          {dayEvents.length > 3 && (
                            <span className="pl-0.5 text-[9px] text-muted-foreground/60">
                              +{dayEvents.length - 3}
                            </span>
                          )}
                        </div>
                      )}
                    </motion.div>
                  )
                })}
              </motion.div>
            </AnimatePresence>
          </div>
        </motion.section>
      </div>

      <CalendarDayDialog
        open={dialogDate !== null}
        onOpenChange={(open) => {
          if (!open) setDialogDate(null)
        }}
        date={dialogDate}
        dailyEvents={dialogEvents}
        weeklyEvents={dialogWeeklyEvents}
        onJumpToDate={(date) => openDateDetail(date)}
      />
    </>
  )
}
