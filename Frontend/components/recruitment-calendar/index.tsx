"use client"

import { useMemo, useState } from "react"
import {
  addDays,
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
  Users,
} from "lucide-react"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { cn } from "@/lib/utils"
import {
  type CalendarEvent,
  type CalendarEventType,
  eventTypeConfig,
  formatDateKey,
  groupEventsByDate,
  mockCalendarEvents,
} from "./calendar-mock-data"
import { CalendarDayDialog } from "./calendar-day-dialog"
import {
  buildPriorityHeadline,
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

function PriorityCard({ event, onOpen }: { event: CalendarEvent; onOpen: (date: Date) => void }) {
  const config = eventTypeConfig[event.type]
  const dDay = getDaysUntil(event.date)

  return (
    <button
      type="button"
      onClick={() => onOpen(event.date)}
      className="group rounded-[24px] border border-slate-200/80 bg-white/95 p-4 text-left shadow-[0_18px_40px_-32px_rgba(15,23,42,0.35)] transition-all hover:-translate-y-0.5 hover:border-slate-300 hover:shadow-[0_20px_48px_-28px_rgba(15,23,42,0.38)]"
    >
      <div className="flex items-start justify-between gap-3">
        <div className="flex min-w-0 items-start gap-3">
          <div
            className={cn(
              "mt-0.5 flex h-10 w-10 shrink-0 items-center justify-center rounded-[16px] text-sm font-semibold text-white shadow-inner",
              event.logoColor
            )}
          >
            {event.company.slice(0, 1)}
          </div>

          <div className="min-w-0">
            <div className="flex items-center gap-2">
              <p className="truncate text-sm font-semibold text-slate-950">{event.company}</p>
              <span className="text-[11px] font-medium text-slate-400">{config.label}</span>
            </div>
            <p className="mt-1 truncate text-xs text-muted-foreground">{event.position}</p>
          </div>
        </div>

        <Badge
          variant="outline"
          className={cn(
            "rounded-full px-2.5 py-1 text-[11px] font-semibold",
            dDay <= 3 ? "border-amber-200 bg-amber-50 text-amber-700" : "border-slate-200 bg-slate-50 text-slate-600"
          )}
        >
          {dDay === 0 ? "오늘" : `D-${dDay}`}
        </Badge>
      </div>

      <div className="mt-4 flex items-center justify-between gap-3 text-xs text-muted-foreground">
        <span>{format(event.date, "M월 d일 EEE a h:mm", { locale: ko })}</span>
        <span className="truncate text-right text-slate-400">{event.memo ?? "상세 보기"}</span>
      </div>
    </button>
  )
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
    return mockCalendarEvents.filter((event) => event.type === activeFilter)
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
    const eventsInMonth = filteredEvents.filter((event) => event.date >= monthStart && event.date <= monthEnd)

    return {
      total: eventsInMonth.length,
      deadline: eventsInMonth.filter((event) => event.type === "deadline").length,
      codingTest: eventsInMonth.filter((event) => event.type === "codingTest").length,
      aptitude: eventsInMonth.filter((event) => event.type === "aptitude").length,
      interview: eventsInMonth.filter((event) => event.type === "interview").length,
    }
  }, [currentMonth, filteredEvents])

  const { upcoming, priority } = useMemo(() => getPriorityEvents(filteredEvents, today), [filteredEvents, today])

  const urgentCount = useMemo(
    () => upcoming.filter((event) => getDaysUntil(event.date, today) <= 3).length,
    [today, upcoming]
  )

  const nextMilestone = priority[0] ?? null
  const priorityHeadline = useMemo(() => buildPriorityHeadline(priority, today), [priority, today])

  const busiestDayLabel = useMemo(() => {
    const monthStart = startOfMonth(currentMonth)
    const monthEnd = endOfMonth(currentMonth)
    const monthEvents = filteredEvents.filter((event) => event.date >= monthStart && event.date <= monthEnd)
    const counts = new Map<string, { count: number; date: Date }>()

    for (const event of monthEvents) {
      const key = formatDateKey(event.date)
      const existing = counts.get(key)
      counts.set(key, {
        count: (existing?.count ?? 0) + 1,
        date: event.date,
      })
    }

    const busiest = [...counts.values()].sort((left, right) => right.count - left.count)[0]
    return busiest ? `${format(busiest.date, "M월 d일", { locale: ko })} · ${busiest.count}건` : "분산 배치"
  }, [currentMonth, filteredEvents])
  const dialogDateKey = dialogDate ? formatDateKey(dialogDate) : null

  const dialogEvents = useMemo(() => {
    if (!dialogDateKey) return []
    return [...(eventsByDate.get(dialogDateKey) || [])].sort((left, right) => left.date.getTime() - right.date.getTime())
  }, [dialogDateKey, eventsByDate])

  const dialogWeeklyEvents = useMemo(() => {
    if (!dialogDate) return []
    return getWeeklyEvents(filteredEvents, dialogDate)
  }, [dialogDate, filteredEvents])

  const monthMix = getTypeCounts(
    filteredEvents.filter(
      (event) => event.date >= startOfMonth(currentMonth) && event.date <= endOfMonth(currentMonth)
    )
  )

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
    if (!isSameMonth(date, currentMonth)) {
      setCurrentMonth(date)
    }
    if ((eventsByDate.get(key) || []).length > 0) {
      setDialogDate(date)
    }
  }

  return (
    <>
      <div className="flex h-full min-h-0 flex-col gap-4">
        <motion.section
          initial={{ opacity: 0, y: 16 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.28 }}
          className="overflow-hidden rounded-[36px] border border-border/70 bg-background shadow-[0_30px_80px_-56px_rgba(15,23,42,0.35)]"
        >
          <div className="grid xl:grid-cols-[minmax(0,1fr)_320px]">
            <div className="border-b border-border/60 p-6 sm:p-8 xl:border-b-0 xl:border-r">
              <div className="flex flex-wrap items-center gap-2">
                <Badge variant="secondary" className="rounded-full px-3 py-1 text-[11px]">
                  This Week
                </Badge>
                <Badge variant="outline" className="rounded-full px-3 py-1 text-[11px]">
                  {filterOptions.find((option) => option.key === activeFilter)?.label ?? "전체"}
                </Badge>
                <Badge variant="outline" className="rounded-full px-3 py-1 text-[11px]">
                  다음 7일 기준
                </Badge>
              </div>

              <div className="mt-5 max-w-3xl">
                <h2
                  className="text-3xl font-semibold tracking-tight text-slate-950 sm:text-[2.4rem]"
                  style={{ textWrap: "balance" }}
                >
                  {priorityHeadline.title}
                </h2>
                <p className="mt-3 max-w-2xl text-sm leading-6 text-muted-foreground" style={{ textWrap: "pretty" }}>
                  {priorityHeadline.summary}
                </p>
              </div>

              <div className="mt-6 grid gap-3 md:grid-cols-2 2xl:grid-cols-3">
                {priority.length > 0 ? (
                  priority.map((event) => <PriorityCard key={event.id} event={event} onOpen={openDateDetail} />)
                ) : (
                  <div className="rounded-[26px] border border-dashed border-border/70 bg-slate-50 px-5 py-6 text-sm text-muted-foreground">
                    예정 일정이 비어 있습니다. 다른 타입을 선택하거나 다음 달로 이동해 보세요.
                  </div>
                )}
              </div>
            </div>

            <aside className="bg-[linear-gradient(180deg,rgba(15,23,42,0.98),rgba(30,41,59,0.98))] px-6 py-6 text-white sm:px-8 xl:px-6">
              <p className="text-[11px] font-medium uppercase tracking-[0.24em] text-white/50">Month Load</p>
              <h3 className="mt-3 text-xl font-semibold">{format(currentMonth, "M월 운영 밀도", { locale: ko })}</h3>
              <p className="mt-2 text-sm leading-6 text-white/70">
                지금 필터 기준으로 달력에 보이는 일정만 압축했습니다. 상단 우선순위와 함께 보면 이번 주 배치가 더 선명해집니다.
              </p>

              <div className="mt-6 grid grid-cols-2 gap-3">
                <div className="rounded-[24px] border border-white/10 bg-white/6 px-4 py-4">
                  <p className="text-[10px] uppercase tracking-[0.18em] text-white/50">이번 달</p>
                  <p className="mt-2 text-3xl font-semibold">{monthStats.total}</p>
                  <p className="mt-1 text-xs text-white/55">보이는 전체 일정</p>
                </div>
                <div className="rounded-[24px] border border-white/10 bg-white/6 px-4 py-4">
                  <p className="text-[10px] uppercase tracking-[0.18em] text-white/50">D-3</p>
                  <p className="mt-2 text-3xl font-semibold">{urgentCount}</p>
                  <p className="mt-1 text-xs text-white/55">임박한 일정</p>
                </div>
              </div>

              <div className="mt-3 rounded-[24px] border border-white/10 bg-white/6 px-4 py-4">
                <p className="text-[10px] uppercase tracking-[0.18em] text-white/50">Peak Day</p>
                <p className="mt-2 text-lg font-semibold">{busiestDayLabel}</p>
                <p className="mt-1 text-xs text-white/55">가장 일정이 몰린 날짜</p>
              </div>

              <div className="mt-3 rounded-[24px] border border-sky-400/20 bg-sky-400/10 px-4 py-4">
                <p className="text-[10px] uppercase tracking-[0.18em] text-sky-200/70">Next</p>
                <p className="mt-2 text-lg font-semibold">
                  {nextMilestone ? `${nextMilestone.company} ${eventTypeConfig[nextMilestone.type].label}` : "예정 일정 없음"}
                </p>
                <p className="mt-1 text-xs text-sky-100/70">
                  {nextMilestone
                    ? `${format(nextMilestone.date, "M월 d일 EEE a h:mm", { locale: ko })}`
                    : "다른 월이나 필터를 확인해 보세요."}
                </p>
              </div>
            </aside>
          </div>
        </motion.section>

        <motion.section
          initial={{ opacity: 0, y: 16 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.28, delay: 0.04 }}
          className="flex min-h-0 flex-1 flex-col overflow-hidden rounded-[32px] border border-border/70 bg-background shadow-[0_24px_70px_-54px_rgba(15,23,42,0.32)]"
        >
          <div className="border-b border-border/60 px-5 py-5 sm:px-6">
            <div className="flex flex-col gap-4 lg:flex-row lg:items-end lg:justify-between">
              <div>
                <p className="text-[11px] font-medium uppercase tracking-[0.22em] text-muted-foreground/70">
                  Month View
                </p>
                <div className="mt-3 flex flex-wrap items-center gap-2">
                  <Button variant="outline" size="icon" className="size-9 rounded-full" onClick={handlePrevMonth}>
                    <ChevronLeft className="size-4" />
                  </Button>

                  <AnimatePresence mode="wait" initial={false}>
                    <motion.h2
                      key={format(currentMonth, "yyyy-MM")}
                      initial={{ y: direction * 10, opacity: 0 }}
                      animate={{ y: 0, opacity: 1 }}
                      exit={{ y: direction * -10, opacity: 0 }}
                      transition={{ duration: 0.16 }}
                      className="min-w-[160px] text-xl font-semibold tracking-tight sm:text-2xl"
                    >
                      {format(currentMonth, "yyyy년 M월", { locale: ko })}
                    </motion.h2>
                  </AnimatePresence>

                  <Button variant="outline" size="icon" className="size-9 rounded-full" onClick={handleNextMonth}>
                    <ChevronRight className="size-4" />
                  </Button>

                  <Button variant="ghost" size="sm" className="rounded-full text-xs" onClick={handleToday}>
                    오늘로 이동
                  </Button>
                </div>
              </div>

              <div className="flex flex-wrap gap-2">
                {filterOptions.map(({ key, label, icon: Icon }) => (
                  <Button
                    key={key}
                    variant={activeFilter === key ? "default" : "outline"}
                    size="sm"
                    className="h-8 gap-1.5 rounded-full px-3 text-xs"
                    onClick={() => setActiveFilter(key)}
                  >
                    <Icon className="size-3.5" />
                    {label}
                    {key !== "all" ? (
                      <span className="text-[10px] opacity-70">{monthStats[key as keyof typeof monthStats]}</span>
                    ) : null}
                  </Button>
                ))}
              </div>
            </div>

            <div className="mt-4 flex flex-wrap items-center gap-x-4 gap-y-2 text-xs text-muted-foreground">
              <span>일정이 있는 날짜를 누르면 큰 상세 뷰로 열립니다.</span>
              <span>이번 달 구성</span>
              {(["deadline", "codingTest", "aptitude", "interview"] as const).map((type) =>
                monthMix[type] > 0 ? (
                  <span key={type} className="inline-flex items-center gap-1.5">
                    <span className={`size-2 rounded-full ${eventTypeConfig[type].dotColor}`} />
                    {eventTypeConfig[type].label} {monthMix[type]}
                  </span>
                ) : null
              )}
            </div>
          </div>

          <div className="min-h-0 flex-1 overflow-auto bg-slate-50/70 p-3 sm:p-4">
            <div className="min-w-[860px]">
              <div className="mb-2 grid grid-cols-7 gap-2">
                {WEEKDAYS.map((day, index) => (
                  <div
                    key={day}
                    className={cn(
                      "px-3 py-2 text-center text-xs font-medium",
                      index === 0
                        ? "text-red-400"
                        : index === 6
                          ? "text-blue-400"
                          : "text-muted-foreground"
                    )}
                  >
                    {day}
                  </div>
                ))}
              </div>

              <div className="grid grid-cols-7 gap-2">
                {calendarDays.map((day) => {
                  const dateKey = formatDateKey(day)
                  const dayEvents = [...(eventsByDate.get(dateKey) || [])].sort(
                    (left, right) => left.date.getTime() - right.date.getTime()
                  )
                  const isCurrentMonth = isSameMonth(day, currentMonth)
                  const isSelected = selectedDate ? isSameDay(day, selectedDate) : false
                  const isTodayDate = isToday(day)
                  const hasEvents = dayEvents.length > 0
                  const hasUrgentEvent = dayEvents.some((event) => {
                    const diff = getDaysUntil(event.date, today)
                    return event.result === "pending" && diff >= 0 && diff <= 3
                  })

                  return (
                    <button
                      key={dateKey}
                      type="button"
                      onClick={() => openDateDetail(day)}
                      className={cn(
                        "group flex min-h-[156px] flex-col rounded-[24px] border px-3 py-3 text-left shadow-[0_18px_44px_-38px_rgba(15,23,42,0.32)] transition-all",
                        hasEvents ? "cursor-pointer" : "cursor-default",
                        !isCurrentMonth && "border-slate-200/70 bg-white/55 text-muted-foreground/50",
                        isCurrentMonth && "border-white bg-white/95 hover:-translate-y-0.5 hover:border-slate-200 hover:shadow-[0_26px_48px_-36px_rgba(15,23,42,0.36)]",
                        hasUrgentEvent && isCurrentMonth && "bg-[linear-gradient(180deg,rgba(255,250,235,1),rgba(255,255,255,0.98))]",
                        isSelected && "ring-2 ring-sky-200"
                      )}
                    >
                      <div className="flex items-center justify-between gap-2">
                        <span
                          className={cn(
                            "flex h-9 w-9 items-center justify-center rounded-full text-sm font-semibold",
                            isTodayDate
                              ? "bg-slate-950 text-white"
                              : !isCurrentMonth
                                ? "text-muted-foreground/45"
                                : "text-slate-900"
                          )}
                        >
                          {format(day, "d")}
                        </span>

                        {hasEvents ? (
                          <Badge variant="outline" className="rounded-full px-2.5 py-1 text-[10px] text-slate-500">
                            {dayEvents.length}건
                          </Badge>
                        ) : null}
                      </div>

                      <div className="mt-3 flex flex-1 flex-col gap-2">
                        {dayEvents.slice(0, 2).map((event) => (
                          <div
                            key={event.id}
                            className="rounded-[18px] border border-slate-100 bg-slate-50/90 px-3 py-2"
                          >
                            <div className="flex items-center gap-2">
                              <span className={`size-2 rounded-full ${eventTypeConfig[event.type].dotColor}`} />
                              <span className="truncate text-xs font-medium text-slate-800">{event.company}</span>
                            </div>
                            <div className="mt-1 flex items-center justify-between gap-2">
                              <span className="truncate text-[11px] text-muted-foreground">{eventTypeConfig[event.type].label}</span>
                              <span className="text-[11px] text-muted-foreground">
                                {format(event.date, "a h:mm", { locale: ko })}
                              </span>
                            </div>
                          </div>
                        ))}
                      </div>

                      {dayEvents.length > 2 ? (
                        <div className="mt-2 rounded-full border border-dashed border-slate-200 px-3 py-1 text-[11px] text-muted-foreground">
                          추가 일정 {dayEvents.length - 2}건 더 보기
                        </div>
                      ) : hasUrgentEvent ? (
                        <div className="mt-2 text-[11px] font-medium text-amber-700">임박 일정 포함</div>
                      ) : hasEvents ? (
                        <div className="mt-2 text-[11px] text-muted-foreground">상세 보기</div>
                      ) : null}
                    </button>
                  )
                })}
              </div>
            </div>
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
