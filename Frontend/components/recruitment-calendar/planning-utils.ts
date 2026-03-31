import { addDays, differenceInCalendarDays, format, isSameWeek, startOfDay } from "date-fns"
import { ko } from "date-fns/locale"
import {
  type CalendarEvent,
  type CalendarEventType,
  eventTypeConfig,
} from "./calendar-mock-data"

const typePriority: Record<CalendarEventType, number> = {
  deadline: 0,
  codingTest: 1,
  aptitude: 2,
  interview: 3,
}

export function getDaysUntil(date: Date, baseDate = new Date()) {
  return differenceInCalendarDays(startOfDay(date), startOfDay(baseDate))
}

export function sortEventsByUrgency(events: CalendarEvent[], baseDate = new Date()) {
  return [...events].sort((left, right) => {
    const leftPendingPenalty = left.result === "pending" ? 0 : 500
    const rightPendingPenalty = right.result === "pending" ? 0 : 500
    const leftDayPenalty = getDaysUntil(left.date, baseDate) < 0 ? 300 : 0
    const rightDayPenalty = getDaysUntil(right.date, baseDate) < 0 ? 300 : 0
    const leftScore =
      leftPendingPenalty +
      leftDayPenalty +
      Math.abs(getDaysUntil(left.date, baseDate)) * 10 +
      typePriority[left.type]
    const rightScore =
      rightPendingPenalty +
      rightDayPenalty +
      Math.abs(getDaysUntil(right.date, baseDate)) * 10 +
      typePriority[right.type]

    if (leftScore !== rightScore) {
      return leftScore - rightScore
    }

    return left.date.getTime() - right.date.getTime()
  })
}

export function getPriorityEvents(events: CalendarEvent[], today: Date) {
  const upcoming = sortEventsByUrgency(
    events.filter((event) => event.result === "pending" && getDaysUntil(event.date, today) >= 0),
    today
  )
  const nextWeek = upcoming.filter((event) => event.date <= addDays(today, 7))
  return {
    upcoming,
    priority: (nextWeek.length > 0 ? nextWeek : upcoming).slice(0, 6),
  }
}

export function getWeeklyEvents(events: CalendarEvent[], date: Date) {
  return sortEventsByUrgency(
    events.filter((event) => isSameWeek(event.date, date, { weekStartsOn: 0 })),
    date
  )
}

export function getTypeCounts(events: CalendarEvent[]) {
  return events.reduce(
    (acc, event) => {
      acc[event.type] += 1
      return acc
    },
    {
      deadline: 0,
      codingTest: 0,
      aptitude: 0,
      interview: 0,
    }
  )
}

export function buildPriorityHeadline(events: CalendarEvent[], today: Date) {
  if (events.length === 0) {
    return {
      title: "이번 주 먼저 챙길 일정 없음",
      summary: "필터에 걸린 예정 일정이 없습니다. 다른 월이나 다른 타입을 보면 바로 다시 계산됩니다.",
    }
  }

  const firstEvent = events[0]
  const counts = getTypeCounts(events)
  const fragments = (["deadline", "codingTest", "aptitude", "interview"] as const)
    .filter((type) => counts[type] > 0)
    .map((type) => `${eventTypeConfig[type].label} ${counts[type]}`)

  return {
    title: `이번 주 먼저 챙길 일정 ${events.length}건`,
    summary: `${format(firstEvent.date, "M월 d일 EEE", { locale: ko })} ${firstEvent.company} ${eventTypeConfig[firstEvent.type].label}부터 시작됩니다. ${fragments.join(" · ")} 흐름이 같은 주간에 겹쳐 있습니다.`,
  }
}
