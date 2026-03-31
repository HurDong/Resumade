"use client"

import { useState, useMemo } from "react"
import {
  addMonths,
  subMonths,
  startOfMonth,
  endOfMonth,
  startOfWeek,
  endOfWeek,
  eachDayOfInterval,
  isSameMonth,
  isSameDay,
  isToday,
  format,
} from "date-fns"
import { ko } from "date-fns/locale"
import { motion, AnimatePresence } from "framer-motion"
import {
  ChevronLeft,
  ChevronRight,
  Calendar as CalendarIcon,
  FileText,
  Code2,
  BrainCircuit,
  Users,
} from "lucide-react"
import { Button } from "@/components/ui/button"
import { Badge } from "@/components/ui/badge"
import { ScrollArea } from "@/components/ui/scroll-area"
import { CalendarEventCard } from "./calendar-event-card"
import {
  type CalendarEvent,
  type CalendarEventType,
  mockCalendarEvents,
  eventTypeConfig,
  groupEventsByDate,
  formatDateKey,
} from "./calendar-mock-data"

const WEEKDAYS = ["일", "월", "화", "수", "목", "금", "토"]

const filterOptions: { key: CalendarEventType | "all"; label: string; icon: React.ElementType }[] = [
  { key: "all", label: "전체", icon: CalendarIcon },
  { key: "deadline", label: "서류 마감", icon: FileText },
  { key: "codingTest", label: "코딩테스트", icon: Code2 },
  { key: "aptitude", label: "인적성", icon: BrainCircuit },
  { key: "interview", label: "면접", icon: Users },
]

export function RecruitmentCalendar() {
  const [currentMonth, setCurrentMonth] = useState(new Date())
  const [selectedDate, setSelectedDate] = useState<Date | null>(new Date())
  const [activeFilter, setActiveFilter] = useState<CalendarEventType | "all">("all")
  const [direction, setDirection] = useState(0)

  const filteredEvents = useMemo(() => {
    if (activeFilter === "all") return mockCalendarEvents
    return mockCalendarEvents.filter((e) => e.type === activeFilter)
  }, [activeFilter])

  const eventsByDate = useMemo(() => groupEventsByDate(filteredEvents), [filteredEvents])

  // 달력 그리드 날짜 계산
  const calendarDays = useMemo(() => {
    const monthStart = startOfMonth(currentMonth)
    const monthEnd = endOfMonth(currentMonth)
    const calendarStart = startOfWeek(monthStart, { weekStartsOn: 0 })
    const calendarEnd = endOfWeek(monthEnd, { weekStartsOn: 0 })
    return eachDayOfInterval({ start: calendarStart, end: calendarEnd })
  }, [currentMonth])

  // 선택된 날짜의 이벤트
  const selectedDateEvents = useMemo(() => {
    if (!selectedDate) return []
    const key = formatDateKey(selectedDate)
    return eventsByDate.get(key) || []
  }, [selectedDate, eventsByDate])

  // 이번 달 이벤트 요약 통계
  const monthStats = useMemo(() => {
    const monthStart = startOfMonth(currentMonth)
    const monthEnd = endOfMonth(currentMonth)
    const eventsInMonth = filteredEvents.filter(
      (e) => e.date >= monthStart && e.date <= monthEnd
    )
    return {
      total: eventsInMonth.length,
      deadline: eventsInMonth.filter((e) => e.type === "deadline").length,
      codingTest: eventsInMonth.filter((e) => e.type === "codingTest").length,
      aptitude: eventsInMonth.filter((e) => e.type === "aptitude").length,
      interview: eventsInMonth.filter((e) => e.type === "interview").length,
    }
  }, [currentMonth, filteredEvents])

  const handlePrevMonth = () => {
    setDirection(-1)
    setCurrentMonth((prev) => subMonths(prev, 1))
  }

  const handleNextMonth = () => {
    setDirection(1)
    setCurrentMonth((prev) => addMonths(prev, 1))
  }

  const handleToday = () => {
    const today = new Date()
    setDirection(today > currentMonth ? 1 : -1)
    setCurrentMonth(today)
    setSelectedDate(today)
  }

  return (
    <div className="flex h-full min-h-0 gap-4">
      {/* 왼쪽: 월간 캘린더 */}
      <div className="flex min-h-0 min-w-0 flex-1 flex-col">
        {/* 헤더: 월 네비게이션 + 필터 */}
        <div className="mb-3 flex flex-wrap items-center justify-between gap-3">
          <div className="flex items-center gap-2">
            <Button variant="outline" size="icon" className="size-8" onClick={handlePrevMonth}>
              <ChevronLeft className="size-4" />
            </Button>

            <AnimatePresence mode="wait" initial={false}>
              <motion.h2
                key={format(currentMonth, "yyyy-MM")}
                initial={{ y: direction * 10, opacity: 0 }}
                animate={{ y: 0, opacity: 1 }}
                exit={{ y: direction * -10, opacity: 0 }}
                transition={{ duration: 0.15 }}
                className="min-w-[140px] text-center text-lg font-bold tracking-tight"
              >
                {format(currentMonth, "yyyy년 M월", { locale: ko })}
              </motion.h2>
            </AnimatePresence>

            <Button variant="outline" size="icon" className="size-8" onClick={handleNextMonth}>
              <ChevronRight className="size-4" />
            </Button>

            <Button variant="ghost" size="sm" className="ml-1 text-xs" onClick={handleToday}>
              오늘
            </Button>
          </div>

          {/* 필터 버튼 */}
          <div className="flex items-center gap-1.5">
            {filterOptions.map(({ key, label, icon: Icon }) => (
              <Button
                key={key}
                variant={activeFilter === key ? "default" : "outline"}
                size="sm"
                className="h-7 gap-1 px-2.5 text-xs"
                onClick={() => setActiveFilter(key)}
              >
                <Icon className="size-3" />
                {label}
                {key !== "all" && (
                  <span className="ml-0.5 text-[10px] opacity-70">
                    {monthStats[key as keyof typeof monthStats] || 0}
                  </span>
                )}
              </Button>
            ))}
          </div>
        </div>

        {/* 이번 달 요약 통계 바 */}
        <div className="mb-3 flex items-center gap-3 rounded-lg border border-border/50 bg-background/80 px-4 py-2">
          <span className="text-xs font-medium text-muted-foreground">이번 달</span>
          <div className="flex items-center gap-4">
            {(["deadline", "codingTest", "aptitude", "interview"] as const).map((type) => {
              const config = eventTypeConfig[type]
              const count = monthStats[type]
              if (count === 0) return null
              return (
                <div key={type} className="flex items-center gap-1.5">
                  <div className={`size-2 rounded-full ${config.dotColor}`} />
                  <span className="text-xs text-muted-foreground">
                    {config.label}{" "}
                    <span className="font-semibold text-foreground">{count}</span>
                  </span>
                </div>
              )
            })}
            <span className="text-xs font-bold text-foreground">
              총 {monthStats.total}건
            </span>
          </div>
        </div>

        {/* 캘린더 그리드 */}
        <div className="min-h-0 flex-1 overflow-hidden rounded-xl border border-border/50 bg-background">
          {/* 요일 헤더 */}
          <div className="grid grid-cols-7 border-b border-border/50 bg-muted/30">
            {WEEKDAYS.map((day, i) => (
              <div
                key={day}
                className={`py-2 text-center text-xs font-medium ${
                  i === 0 ? "text-red-400" : i === 6 ? "text-blue-400" : "text-muted-foreground"
                }`}
              >
                {day}
              </div>
            ))}
          </div>

          {/* 날짜 그리드 */}
          <div className="grid h-[calc(100%-33px)] grid-cols-7">
            {calendarDays.map((day, index) => {
              const dateKey = formatDateKey(day)
              const dayEvents = eventsByDate.get(dateKey) || []
              const isCurrentMonth = isSameMonth(day, currentMonth)
              const isSelected = selectedDate && isSameDay(day, selectedDate)
              const isTodayDate = isToday(day)
              const dayOfWeek = day.getDay()
              const maxVisible = 3

              return (
                <button
                  key={dateKey}
                  type="button"
                  onClick={() => setSelectedDate(day)}
                  className={`group relative flex flex-col border-b border-r border-border/30 p-1 text-left transition-colors ${
                    !isCurrentMonth ? "bg-muted/20" : "hover:bg-muted/40"
                  } ${isSelected ? "bg-primary/5 ring-1 ring-inset ring-primary/30" : ""}`}
                >
                  {/* 날짜 숫자 */}
                  <div className="mb-0.5 flex items-center justify-between">
                    <span
                      className={`flex size-6 items-center justify-center rounded-full text-xs font-medium ${
                        isTodayDate
                          ? "bg-primary font-bold text-primary-foreground"
                          : !isCurrentMonth
                            ? "text-muted-foreground/40"
                            : dayOfWeek === 0
                              ? "text-red-400"
                              : dayOfWeek === 6
                                ? "text-blue-400"
                                : "text-foreground"
                      }`}
                    >
                      {format(day, "d")}
                    </span>
                    {dayEvents.length > maxVisible && (
                      <span className="text-[9px] font-medium text-muted-foreground">
                        +{dayEvents.length - maxVisible}
                      </span>
                    )}
                  </div>

                  {/* 이벤트 리스트 */}
                  <div className="flex min-h-0 flex-1 flex-col gap-0.5">
                    {dayEvents.slice(0, maxVisible).map((event) => (
                      <CalendarEventCard key={event.id} event={event} compact />
                    ))}
                  </div>

                  {/* 이벤트 도트 인디케이터 (이벤트가 있지만 공간이 좁을 때) */}
                  {dayEvents.length > 0 && dayEvents.length <= maxVisible && (
                    <div className="mt-auto flex justify-center gap-0.5 pt-0.5">
                      {dayEvents.map((event) => (
                        <div
                          key={`dot-${event.id}`}
                          className={`size-1 rounded-full ${eventTypeConfig[event.type].dotColor} opacity-0 group-hover:opacity-100 transition-opacity`}
                        />
                      ))}
                    </div>
                  )}
                </button>
              )
            })}
          </div>
        </div>
      </div>

      {/* 오른쪽: 선택된 날짜 상세 패널 */}
      <div className="flex w-[320px] shrink-0 flex-col rounded-xl border border-border/50 bg-background">
        <div className="border-b border-border/50 px-4 py-3">
          <AnimatePresence mode="wait">
            <motion.div
              key={selectedDate ? formatDateKey(selectedDate) : "none"}
              initial={{ opacity: 0, y: 4 }}
              animate={{ opacity: 1, y: 0 }}
              exit={{ opacity: 0, y: -4 }}
              transition={{ duration: 0.15 }}
            >
              {selectedDate ? (
                <>
                  <h3 className="text-sm font-bold">
                    {format(selectedDate, "M월 d일 EEEE", { locale: ko })}
                  </h3>
                  <p className="mt-0.5 text-xs text-muted-foreground">
                    {selectedDateEvents.length > 0
                      ? `${selectedDateEvents.length}개의 일정`
                      : "등록된 일정이 없습니다"}
                  </p>
                </>
              ) : (
                <p className="text-sm text-muted-foreground">날짜를 선택해 주세요</p>
              )}
            </motion.div>
          </AnimatePresence>
        </div>

        <ScrollArea className="flex-1 px-3 py-3">
          <AnimatePresence mode="wait">
            {selectedDateEvents.length > 0 ? (
              <motion.div
                key={selectedDate ? formatDateKey(selectedDate) : "none"}
                initial={{ opacity: 0 }}
                animate={{ opacity: 1 }}
                exit={{ opacity: 0 }}
                transition={{ duration: 0.15 }}
                className="space-y-2"
              >
                {selectedDateEvents
                  .sort((a, b) => a.date.getTime() - b.date.getTime())
                  .map((event, i) => (
                    <motion.div
                      key={event.id}
                      initial={{ opacity: 0, y: 8 }}
                      animate={{ opacity: 1, y: 0 }}
                      transition={{ duration: 0.2, delay: i * 0.05 }}
                    >
                      <CalendarEventCard event={event} />
                    </motion.div>
                  ))}
              </motion.div>
            ) : (
              <motion.div
                initial={{ opacity: 0 }}
                animate={{ opacity: 1 }}
                className="flex h-[200px] flex-col items-center justify-center gap-3"
              >
                <div className="flex size-12 items-center justify-center rounded-full bg-muted/50">
                  <CalendarIcon className="size-5 text-muted-foreground/50" />
                </div>
                <div className="text-center">
                  <p className="text-sm font-medium text-muted-foreground/70">일정 없음</p>
                  <p className="mt-0.5 text-xs text-muted-foreground/50">
                    이 날에는 등록된 일정이 없습니다
                  </p>
                </div>
              </motion.div>
            )}
          </AnimatePresence>
        </ScrollArea>

        {/* 범례 */}
        <div className="border-t border-border/50 px-4 py-3">
          <p className="mb-2 text-[10px] font-medium uppercase tracking-wider text-muted-foreground/60">
            범례
          </p>
          <div className="grid grid-cols-2 gap-1.5">
            {(["deadline", "codingTest", "aptitude", "interview"] as const).map((type) => {
              const config = eventTypeConfig[type]
              return (
                <div key={type} className="flex items-center gap-1.5">
                  <div className={`size-2 rounded-full ${config.dotColor}`} />
                  <span className="text-[10px] text-muted-foreground">{config.label}</span>
                </div>
              )
            })}
          </div>
        </div>
      </div>
    </div>
  )
}
