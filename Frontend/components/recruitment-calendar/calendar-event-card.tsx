"use client"

import { format } from "date-fns"
import { ko } from "date-fns/locale"
import {
  FileText,
  Code2,
  BrainCircuit,
  Users,
  CheckCircle2,
  XCircle,
  Clock,
  MapPin,
} from "lucide-react"
import { Badge } from "@/components/ui/badge"
import {
  Tooltip,
  TooltipTrigger,
  TooltipContent,
} from "@/components/ui/tooltip"
import {
  type CalendarEvent,
  type CalendarEventType,
  eventTypeConfig,
  getEventDDay,
} from "./calendar-mock-data"

const eventTypeIcon: Record<CalendarEventType, React.ElementType> = {
  deadline: FileText,
  codingTest: Code2,
  aptitude: BrainCircuit,
  interview: Users,
}

interface CalendarEventCardProps {
  event: CalendarEvent
  compact?: boolean
}

export function CalendarEventCard({ event, compact = false }: CalendarEventCardProps) {
  const config = eventTypeConfig[event.type]
  const Icon = eventTypeIcon[event.type]
  const dDay = getEventDDay(event.date)
  const isPast = dDay.startsWith("D+")
  const isToday = dDay === "D-Day"
  const isUrgent = dDay === "D-1" || dDay === "D-2" || dDay === "D-3"
  const isFailed = event.result === "fail"
  const isPassed = event.result === "pass"

  if (compact) {
    return (
      <Tooltip>
        <TooltipTrigger asChild>
          <button
            type="button"
            className={`group flex w-full items-center gap-1.5 rounded-md border px-1.5 py-1 text-left transition-all hover:shadow-sm ${
              isFailed
                ? "border-red-200/60 bg-red-50/40 opacity-60"
                : isPassed
                  ? "border-emerald-200 bg-emerald-50/60"
                  : isToday
                    ? "border-red-300 bg-red-50 shadow-sm shadow-red-100"
                    : isUrgent
                      ? "border-amber-200 bg-amber-50/60"
                      : `${config.borderColor} ${config.bgColor}`
            }`}
          >
            <div
              className={`size-1.5 shrink-0 rounded-full ${
                isFailed ? "bg-red-400" : isPassed ? "bg-emerald-500" : config.dotColor
              }`}
            />
            <span
              className={`truncate text-[10px] font-medium leading-tight ${
                isFailed
                  ? "text-muted-foreground/50 line-through"
                  : isPassed
                    ? "text-emerald-700"
                    : config.color
              }`}
            >
              {event.company}
            </span>
          </button>
        </TooltipTrigger>
        <TooltipContent side="right" className="max-w-[260px] p-0">
          <EventTooltipContent event={event} />
        </TooltipContent>
      </Tooltip>
    )
  }

  return (
    <Tooltip>
      <TooltipTrigger asChild>
        <button
          type="button"
          className={`group flex w-full items-center gap-2 rounded-lg border px-2.5 py-2 text-left transition-all hover:shadow-md ${
            isFailed
              ? "border-red-200/60 bg-red-50/30 opacity-60"
              : isPassed
                ? "border-emerald-200 bg-emerald-50/80 shadow-sm shadow-emerald-100"
                : isToday
                  ? "border-red-300 bg-red-50 shadow-md shadow-red-100 ring-1 ring-red-200"
                  : isUrgent
                    ? "border-amber-200 bg-amber-50/80 shadow-sm"
                    : `${config.borderColor} ${config.bgColor} hover:shadow-sm`
          }`}
        >
          <div
            className={`flex size-7 shrink-0 items-center justify-center rounded-md ${
              isFailed
                ? "bg-red-100 text-red-400"
                : isPassed
                  ? "bg-emerald-100 text-emerald-600"
                  : isToday
                    ? "bg-red-100 text-red-600"
                    : `${config.bgColor} ${config.color}`
            }`}
          >
            {isFailed ? (
              <XCircle className="size-3.5" />
            ) : isPassed ? (
              <CheckCircle2 className="size-3.5" />
            ) : (
              <Icon className="size-3.5" />
            )}
          </div>

          <div className="min-w-0 flex-1">
            <div className="flex items-center justify-between gap-1">
              <span
                className={`truncate text-[11px] font-bold leading-tight ${
                  isFailed ? "text-muted-foreground/50 line-through" : "text-foreground"
                }`}
              >
                {event.company}
              </span>
              <Badge
                variant="outline"
                className={`h-4 shrink-0 px-1 text-[9px] font-bold ${
                  isFailed
                    ? "border-red-200 text-red-400"
                    : isPassed
                      ? "border-emerald-200 text-emerald-600"
                      : isToday
                        ? "border-red-300 bg-red-100 text-red-700"
                        : isUrgent
                          ? "border-amber-300 bg-amber-100 text-amber-700"
                          : isPast
                            ? "border-zinc-200 text-zinc-400"
                            : "border-blue-200 text-blue-600"
                }`}
              >
                {dDay}
              </Badge>
            </div>
            <p
              className={`mt-0.5 truncate text-[10px] leading-tight ${
                isFailed ? "text-muted-foreground/40" : "text-muted-foreground"
              }`}
            >
              {config.label} · {event.position}
            </p>
          </div>
        </button>
      </TooltipTrigger>
      <TooltipContent side="right" className="max-w-[280px] p-0">
        <EventTooltipContent event={event} />
      </TooltipContent>
    </Tooltip>
  )
}

function EventTooltipContent({ event }: { event: CalendarEvent }) {
  const config = eventTypeConfig[event.type]

  return (
    <div className="space-y-2 p-3">
      <div className="flex items-center gap-2">
        <div className={`size-2 rounded-full ${config.dotColor}`} />
        <span className="text-xs font-semibold">{event.company}</span>
        <Badge variant="outline" className="h-4 px-1 text-[9px]">
          {config.label}
        </Badge>
      </div>
      <p className="text-[11px] text-muted-foreground">{event.position}</p>
      <div className="flex items-center gap-1.5 text-[11px]">
        <Clock className="size-3 text-muted-foreground" />
        <span>{format(event.date, "M월 d일 (EEE) a h:mm", { locale: ko })}</span>
      </div>
      {event.memo && (
        <div className="flex items-start gap-1.5 text-[11px]">
          <MapPin className="mt-0.5 size-3 shrink-0 text-muted-foreground" />
          <span className="text-muted-foreground">{event.memo}</span>
        </div>
      )}
      {event.result && event.result !== "pending" && (
        <div className="flex items-center gap-1.5 text-[11px]">
          {event.result === "pass" ? (
            <>
              <CheckCircle2 className="size-3 text-emerald-500" />
              <span className="font-medium text-emerald-600">합격</span>
            </>
          ) : (
            <>
              <XCircle className="size-3 text-red-400" />
              <span className="font-medium text-red-500">불합격</span>
            </>
          )}
        </div>
      )}
    </div>
  )
}
