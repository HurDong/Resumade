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
import { cn } from "@/lib/utils"
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

function getDDayClass(dDay: string) {
  if (dDay === "D-Day") return "border-red-300 bg-red-500 text-white"
  if (dDay === "D-1") return "border-red-200 bg-red-50 text-red-600"
  if (dDay === "D-2" || dDay === "D-3") return "border-amber-200 bg-amber-50 text-amber-700"
  if (dDay.startsWith("D+")) return "border-border bg-muted/50 text-muted-foreground"
  return "border-slate-200 bg-slate-50 text-slate-700"
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
            className={cn(
              "group flex w-full items-center gap-1.5 rounded-[7px] border-l-[3px] py-[3px] pl-2 pr-1.5 text-left",
              "shadow-[0_1px_2px_rgba(15,23,42,0.06)]",
              "transition-[transform,box-shadow] hover:-translate-y-px hover:shadow-[0_3px_8px_rgba(15,23,42,0.12)]",
              isFailed
                ? "border-l-red-300 bg-red-100/50 opacity-50"
                : isPassed
                  ? "border-l-emerald-500 bg-emerald-100/70"
                  : isToday
                    ? "border-l-red-500 bg-red-100/80"
                    : `${config.chipAccent} ${config.chipBg}`
            )}
          >
            <span
              className={cn(
                "truncate text-[11px] font-semibold leading-tight",
                isFailed
                  ? "text-slate-400 line-through"
                  : isPassed
                    ? "text-emerald-700"
                    : isToday
                      ? "text-red-700"
                      : config.color
              )}
            >
              {event.company}
            </span>
          </button>
        </TooltipTrigger>
        <TooltipContent
          side="right"
          sideOffset={8}
          className="max-w-[260px] p-0 bg-white border border-border/60 text-foreground shadow-xl rounded-[16px] overflow-hidden"
        >
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
      <TooltipContent
        side="right"
        sideOffset={8}
        className="max-w-[280px] p-0 bg-white border border-border/60 text-foreground shadow-xl rounded-[16px] overflow-hidden"
      >
        <EventTooltipContent event={event} />
      </TooltipContent>
    </Tooltip>
  )
}

function EventTooltipContent({ event }: { event: CalendarEvent }) {
  const config = eventTypeConfig[event.type]
  const Icon = eventTypeIcon[event.type]
  const dDay = getEventDDay(event.date)
  const isFailed = event.result === "fail"
  const isPassed = event.result === "pass"

  return (
    <div className="relative overflow-hidden">
      {/* 이벤트 타입 좌측 액센트 바 */}
      <div
        className={cn(
          "absolute left-0 top-0 h-full w-[3px]",
          isFailed ? "bg-red-300" : isPassed ? "bg-emerald-500" : config.dotColor
        )}
      />

      <div className="space-y-2.5 py-3.5 pl-5 pr-4">
        {/* 회사명 + D-Day */}
        <div className="flex items-start justify-between gap-3">
          <div className="min-w-0">
            <p
              className={cn(
                "text-[13px] font-bold leading-snug",
                isFailed ? "text-slate-400 line-through" : "text-foreground"
              )}
            >
              {event.company}
            </p>
            <p className="mt-0.5 truncate text-[11px] text-muted-foreground">
              {event.position}
            </p>
          </div>
          <span
            className={cn(
              "mt-0.5 shrink-0 rounded-full border px-2 py-0.5 text-[10px] font-bold",
              getDDayClass(dDay)
            )}
          >
            {dDay}
          </span>
        </div>

        {/* 이벤트 타입 배지 */}
        <div className="flex items-center gap-1.5">
          <div
            className={cn(
              "flex size-5 shrink-0 items-center justify-center rounded-md",
              config.chipBg,
              config.color
            )}
          >
            <Icon className="size-3" />
          </div>
          <span className={cn("text-[11px] font-semibold", config.color)}>
            {config.label}
          </span>
        </div>

        {/* 날짜 · 시간 */}
        <div className="flex items-center gap-1.5 text-[11px] text-muted-foreground">
          <Clock className="size-3 shrink-0" />
          <span>{format(event.date, "M월 d일 (EEE) a h:mm", { locale: ko })}</span>
        </div>

        {/* 메모 */}
        {event.memo && (
          <div className="flex items-start gap-1.5 rounded-[10px] bg-slate-50 px-2.5 py-2">
            <MapPin className="mt-px size-3 shrink-0 text-slate-400" />
            <span className="text-[11px] leading-[1.5] text-slate-600">{event.memo}</span>
          </div>
        )}

        {/* 합격 / 불합격 */}
        {event.result && event.result !== "pending" && (
          <div
            className={cn(
              "flex items-center gap-1.5 rounded-[10px] px-2.5 py-1.5 text-[11px] font-semibold",
              isPassed ? "bg-emerald-50 text-emerald-700" : "bg-red-50 text-red-600"
            )}
          >
            {isPassed ? (
              <CheckCircle2 className="size-3.5" />
            ) : (
              <XCircle className="size-3.5" />
            )}
            {isPassed ? "합격" : "불합격"}
          </div>
        )}
      </div>
    </div>
  )
}
