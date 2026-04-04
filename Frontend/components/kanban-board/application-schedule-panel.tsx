"use client"

import * as React from "react"
import { format, addDays } from "date-fns"
import { ko } from "date-fns/locale"
import { AnimatePresence, motion } from "framer-motion"
import {
  CalendarIcon,
  Clock,
  Plus,
  Trash2,
  X,
  Zap,
} from "lucide-react"
import { Button } from "@/components/ui/button"
import { Calendar } from "@/components/ui/calendar"
import { Input } from "@/components/ui/input"
import { Popover, PopoverContent, PopoverTrigger } from "@/components/ui/popover"
import { Skeleton } from "@/components/ui/skeleton"
import { cn } from "@/lib/utils"
import {
  type ApplicationSchedule,
  type ScheduleType,
  DEFAULT_SCHEDULE_TYPES,
  createSchedule,
  updateSchedule,
  deleteSchedule,
  fetchSchedules,
} from "@/lib/api/schedules"

// ── 날짜 증감 델타 ──────────────────────────────────────────────────────────
const DAY_DELTAS = [-7, -3, -1, +1, +3, +7, +30] as const

// ── 시간 프리셋 ─────────────────────────────────────────────────────────────
const TIME_PRESETS = [
  { label: "09:00", h: 9,  m: 0  },
  { label: "12:00", h: 12, m: 0  },
  { label: "14:00", h: 14, m: 0  },
  { label: "18:00", h: 18, m: 0  },
  { label: "23:59", h: 23, m: 59 },
]

// ── 시 증감 스텝 ─────────────────────────────────────────────────────────────
const HOUR_STEPS   = [-3, -1, +1, +3] as const
// ── 분 증감 스텝 ─────────────────────────────────────────────────────────────
const MINUTE_STEPS = [-30, -10, -5, +5, +10, +30] as const

// ── 전형 타입별 색상 ─────────────────────────────────────────────────────────

const TYPE_STYLE: Record<ScheduleType, { dot: string; bg: string; text: string }> = {
  DOCUMENT_DEADLINE: { dot: "bg-blue-500",    bg: "bg-blue-50",    text: "text-blue-700"   },
  CODING_TEST:       { dot: "bg-violet-500",  bg: "bg-violet-50",  text: "text-violet-700" },
  APTITUDE:          { dot: "bg-emerald-500", bg: "bg-emerald-50", text: "text-emerald-700"},
  INTERVIEW1:        { dot: "bg-amber-500",   bg: "bg-amber-50",   text: "text-amber-700"  },
  INTERVIEW2:        { dot: "bg-orange-500",  bg: "bg-orange-50",  text: "text-orange-700" },
  CUSTOM:            { dot: "bg-slate-400",   bg: "bg-slate-50",   text: "text-slate-600"  },
}

// ── 인라인 날짜 피커 ─────────────────────────────────────────────────────────
// 동작 원칙:
//  1. 팝오버 내부에서 날짜/시간을 자유롭게 조정 — 팝오버는 닫히지 않음
//  2. 팝오버가 닫힐 때(onOpenChange false) 한 번만 onSelect 호출 → API 저장
//  3. +/- 델타 버튼은 현재 pending 날짜 기준으로 증감

function InlineDatePicker({
  value,
  onSelect,
  onClear,
  triggerClassName,
}: {
  value: Date | undefined
  onSelect: (date: Date) => void
  onClear?: () => void
  triggerClassName?: string
}) {
  const [open, setOpen] = React.useState(false)

  const makeDefault = () => {
    const d = new Date()
    d.setHours(23, 59, 0, 0)
    return d
  }

  // 팝오버 내부에서만 사용하는 임시 상태 (닫힐 때 커밋)
  const [pending, setPending] = React.useState<Date>(() =>
    value ? new Date(value) : makeDefault()
  )

  // 달력에 표시할 월 (pending과 별도로 관리)
  const [calendarMonth, setCalendarMonth] = React.useState<Date>(() =>
    value ? new Date(value) : new Date()
  )

  // 팝오버 열릴 때마다 외부 value로 초기화
  const handleOpenChange = (next: boolean) => {
    if (next) {
      const init = value ? new Date(value) : makeDefault()
      setPending(init)
      setCalendarMonth(new Date(init))
    } else {
      // 팝오버 닫힐 때 커밋 (날짜가 바뀐 경우에만)
      if (!value || pending.getTime() !== value.getTime()) {
        onSelect(pending)
      }
    }
    setOpen(next)
  }

  // 날짜 증감 — 달력도 함께 이동
  const shiftDay = (delta: number) => {
    setPending((prev) => {
      const next = addDays(prev, delta)
      setCalendarMonth(new Date(next))
      return next
    })
  }

  // 오늘로 리셋 (시간은 현재 pending 유지)
  const resetToToday = () => {
    setPending((prev) => {
      const next = new Date()
      next.setHours(prev.getHours(), prev.getMinutes(), 0, 0)
      setCalendarMonth(new Date(next))
      return next
    })
  }

  // 달력 날짜 클릭 (시간은 현재 pending에서 보존)
  const handleCalendarSelect = (d: Date | undefined) => {
    if (!d) return
    setPending((prev) => {
      const next = new Date(d)
      next.setHours(prev.getHours(), prev.getMinutes(), 0, 0)
      return next
    })
  }

  // 시간 프리셋 버튼
  const setTime = (h: number, m: number) => {
    setPending((prev) => {
      const next = new Date(prev)
      next.setHours(h, m, 0, 0)
      return next
    })
  }

  // 시간 증감 (시간/분 범위 클램프)
  const shiftTime = (dh: number, dm: number) => {
    setPending((prev) => {
      const next = new Date(prev)
      let totalMins = next.getHours() * 60 + next.getMinutes() + dh * 60 + dm
      // 0~23:59 범위로 클램프
      totalMins = Math.max(0, Math.min(23 * 60 + 59, totalMins))
      next.setHours(Math.floor(totalMins / 60), totalMins % 60, 0, 0)
      return next
    })
  }

  const currentH = pending.getHours()
  const currentM = pending.getMinutes()
  const isToday =
    pending.toDateString() === new Date().toDateString()

  return (
    <Popover open={open} onOpenChange={handleOpenChange}>
      <PopoverTrigger asChild>
        <button
          className={cn(
            "flex items-center gap-1.5 rounded-lg border px-2.5 py-2 text-xs font-medium transition-colors",
            value
              ? "border-border bg-muted/40 text-foreground hover:bg-muted/70"
              : "border-dashed border-muted-foreground/30 bg-transparent text-muted-foreground hover:border-primary/40 hover:text-primary",
            triggerClassName
          )}
        >
          <CalendarIcon className="size-3 shrink-0" />
          <span className="flex-1 text-left">
            {value ? format(value, "M.d(EEE) HH:mm", { locale: ko }) : "날짜 추가"}
          </span>
          {value && onClear && (
            <span
              role="button"
              onClick={(e) => { e.stopPropagation(); onClear() }}
              className="ml-1 flex size-4 shrink-0 items-center justify-center rounded-full text-muted-foreground/50 hover:bg-destructive/10 hover:text-destructive"
            >
              <X className="size-3" />
            </span>
          )}
        </button>
      </PopoverTrigger>

      <PopoverContent className="w-[340px] rounded-2xl p-0 shadow-2xl" align="start">
        {/* ── 헤더: 현재 날짜·시간 + 오늘 + 초기화 ── */}
        <div className="flex items-center justify-between border-b border-border/40 px-4 py-3">
          <span className="text-base font-bold tabular-nums">
            {format(pending, "M월 d일(EEE)", { locale: ko })}
            <span className="ml-2 text-primary">
              {format(pending, "HH:mm")}
            </span>
          </span>
          <div className="flex items-center gap-2">
            {!isToday && (
              <button
                onClick={resetToToday}
                className="rounded-md bg-muted/60 px-2 py-0.5 text-[11px] font-medium text-muted-foreground transition-colors hover:bg-primary/10 hover:text-primary"
              >
                오늘
              </button>
            )}
            {value && onClear && (
              <button
                onClick={() => { onClear(); setOpen(false) }}
                className="text-[11px] text-muted-foreground/50 hover:text-destructive"
              >
                초기화
              </button>
            )}
          </div>
        </div>

        {/* ── 날짜 증감 델타 ── */}
        <div className="flex items-center gap-0.5 border-b border-border/40 px-3 py-2">
          {DAY_DELTAS.map((d) => (
            <button
              key={d}
              onClick={() => shiftDay(d)}
              className={cn(
                "flex-1 rounded-md py-1.5 text-[11px] font-semibold transition-colors",
                d < 0
                  ? "text-rose-500 hover:bg-rose-50"
                  : "text-blue-600 hover:bg-blue-50"
              )}
            >
              {d > 0 ? `+${d}` : d}
            </button>
          ))}
        </div>

        {/* ── 달력 ── */}
        <div className="px-2 py-1">
          <Calendar
            mode="single"
            selected={pending}
            onSelect={handleCalendarSelect}
            month={calendarMonth}
            onMonthChange={setCalendarMonth}
            initialFocus
            className="rounded-xl"
          />
        </div>

        {/* ── 시간 섹션 ── */}
        <div className="border-t border-border/40 px-4 py-3 space-y-3">
          {/* 빠른 프리셋 */}
          <div className="grid grid-cols-5 gap-1">
            {TIME_PRESETS.map((t) => {
              const isActive = currentH === t.h && currentM === t.m
              return (
                <button
                  key={t.label}
                  onClick={() => setTime(t.h, t.m)}
                  className={cn(
                    "rounded-lg py-1.5 text-[11px] font-semibold transition-colors",
                    isActive
                      ? "bg-primary text-primary-foreground shadow-sm"
                      : "bg-muted/50 text-muted-foreground hover:bg-muted hover:text-foreground"
                  )}
                >
                  {t.label}
                </button>
              )
            })}
          </div>

          {/* 시·분 스피너 */}
          <div className="rounded-xl border border-border/50 bg-muted/20 p-2 space-y-1.5">
            {/* 시 조정 */}
            <div className="flex items-center gap-1">
              <span className="w-6 shrink-0 text-[10px] font-bold uppercase text-muted-foreground">시</span>
              <div className="flex flex-1 items-center gap-0.5">
                {HOUR_STEPS.map((s) => (
                  <button
                    key={s}
                    onClick={() => shiftTime(s, 0)}
                    className={cn(
                      "flex-1 rounded-md py-1 text-[11px] font-semibold transition-colors",
                      s < 0
                        ? "text-rose-500 hover:bg-rose-100"
                        : "text-blue-600 hover:bg-blue-100"
                    )}
                  >
                    {s > 0 ? `+${s}h` : `${s}h`}
                  </button>
                ))}
              </div>
              <span className="w-10 shrink-0 rounded-lg bg-background py-1 text-center text-sm font-bold tabular-nums shadow-sm">
                {String(currentH).padStart(2, "0")}
              </span>
            </div>

            {/* 분 조정 */}
            <div className="flex items-center gap-1">
              <span className="w-6 shrink-0 text-[10px] font-bold uppercase text-muted-foreground">분</span>
              <div className="flex flex-1 items-center gap-0.5">
                {MINUTE_STEPS.map((s) => (
                  <button
                    key={s}
                    onClick={() => shiftTime(0, s)}
                    className={cn(
                      "flex-1 rounded-md py-1 text-[11px] font-semibold transition-colors",
                      s < 0
                        ? "text-rose-500 hover:bg-rose-100"
                        : "text-blue-600 hover:bg-blue-100"
                    )}
                  >
                    {s > 0 ? `+${s}` : `${s}`}
                  </button>
                ))}
              </div>
              <span className="w-10 shrink-0 rounded-lg bg-background py-1 text-center text-sm font-bold tabular-nums shadow-sm">
                {String(currentM).padStart(2, "0")}
              </span>
            </div>
          </div>
        </div>
      </PopoverContent>
    </Popover>
  )
}

// ── 메인 컴포넌트 ─────────────────────────────────────────────────────────────

export function ApplicationSchedulePanel({ applicationId }: { applicationId: number }) {
  const [schedules, setSchedules] = React.useState<ApplicationSchedule[]>([])
  const [isLoading, setIsLoading] = React.useState(true)
  const [isError, setIsError] = React.useState(false)
  const [saving, setSaving] = React.useState<number | string | null>(null)
  const [addingCustom, setAddingCustom] = React.useState(false)
  const [customLabel, setCustomLabel] = React.useState("")

  const load = React.useCallback(async () => {
    setIsLoading(true)
    setIsError(false)
    try {
      setSchedules(await fetchSchedules(applicationId))
    } catch {
      setIsError(true)
    } finally {
      setIsLoading(false)
    }
  }, [applicationId])

  React.useEffect(() => { load() }, [load])

  // 특정 타입의 기존 일정 찾기
  const findSchedule = (type: ScheduleType) =>
    schedules.find((s) => s.type === type)

  // 날짜 선택 → 즉시 저장 (Notion/Linear 스타일)
  const handleDateSelect = async (
    type: ScheduleType,
    date: Date,
    existingId?: number,
    opts?: { customLabel?: string; sortOrder?: number }
  ) => {
    const scheduledAt = format(date, "yyyy-MM-dd'T'HH:mm:ss")
    setSaving(existingId ?? type)
    try {
      if (existingId) {
        const updated = await updateSchedule(applicationId, existingId, { scheduledAt })
        setSchedules((prev) => prev.map((s) => (s.id === existingId ? updated : s)))
      } else {
        const created = await createSchedule(applicationId, {
          type,
          scheduledAt,
          customLabel: opts?.customLabel,
          sortOrder: opts?.sortOrder,
        })
        setSchedules((prev) => [...prev, created])
      }
    } finally {
      setSaving(null)
    }
  }

  const handleDelete = async (scheduleId: number) => {
    setSaving(scheduleId)
    try {
      await deleteSchedule(applicationId, scheduleId)
      setSchedules((prev) => prev.filter((s) => s.id !== scheduleId))
    } finally {
      setSaving(null)
    }
  }

  const handleAddCustom = async (date: Date) => {
    if (!customLabel.trim()) return
    await handleDateSelect("CUSTOM", date, undefined, {
      customLabel: customLabel.trim(),
      sortOrder: 99,
    })
    setCustomLabel("")
    setAddingCustom(false)
  }

  if (isLoading) {
    return (
      <div className="space-y-3 p-1">
        {Array.from({ length: 4 }).map((_, i) => (
          <Skeleton key={i} className="h-11 w-full rounded-xl" />
        ))}
      </div>
    )
  }

  if (isError) {
    return (
      <div className="flex flex-col items-center gap-3 py-8 text-center">
        <p className="text-sm text-muted-foreground">일정을 불러오지 못했습니다</p>
        <Button variant="outline" size="sm" onClick={load}>다시 시도</Button>
      </div>
    )
  }

  const customSchedules = schedules.filter((s) => s.type === "CUSTOM")

  return (
    <div className="space-y-1.5">
      {/* ── 기본 전형 5개 ─────────────────────────────────────────── */}
      {DEFAULT_SCHEDULE_TYPES.map((def) => {
        const existing = findSchedule(def.type)
        const style = TYPE_STYLE[def.type]
        const isSaving = saving === (existing?.id ?? def.type)

        return (
          <motion.div
            key={def.type}
            layout
            className="flex items-center gap-3 rounded-xl border border-border/60 bg-background px-3 py-2.5 transition-colors hover:bg-muted/30"
          >
            {/* 상태 도트 */}
            <div className={cn(
              "size-2 shrink-0 rounded-full transition-colors",
              existing ? style.dot : "bg-muted-foreground/20"
            )} />

            {/* 전형명 */}
            <span className={cn(
              "w-20 shrink-0 text-xs font-medium",
              existing ? style.text : "text-muted-foreground"
            )}>
              {def.label}
            </span>

            {/* 날짜 피커 */}
            <div className="flex flex-1 items-center">
              {isSaving ? (
                <span className="flex-1 text-xs text-muted-foreground">저장 중...</span>
              ) : (
                <InlineDatePicker
                  value={existing ? new Date(existing.scheduledAt) : undefined}
                  onSelect={(date) => handleDateSelect(def.type, date, existing?.id)}
                  onClear={existing ? () => handleDelete(existing.id) : undefined}
                  triggerClassName="flex-1"
                />
              )}
            </div>
          </motion.div>
        )
      })}

      {/* ── 커스텀 전형 목록 ────────────────────────────────────────── */}
      <AnimatePresence>
        {customSchedules.map((s) => (
          <motion.div
            key={s.id}
            layout
            initial={{ opacity: 0, height: 0 }}
            animate={{ opacity: 1, height: "auto" }}
            exit={{ opacity: 0, height: 0 }}
            className="flex items-center gap-3 rounded-xl border border-dashed border-border/60 bg-muted/20 px-3 py-2.5"
          >
            <div className="size-2 shrink-0 rounded-full bg-slate-400" />
            <span className="w-20 shrink-0 text-xs font-medium text-slate-600">
              {s.label}
            </span>
            <div className="flex flex-1 items-center">
              <InlineDatePicker
                value={new Date(s.scheduledAt)}
                onSelect={(date) => handleDateSelect("CUSTOM", date, s.id)}
                onClear={() => handleDelete(s.id)}
                triggerClassName="flex-1"
              />
            </div>
          </motion.div>
        ))}
      </AnimatePresence>

      {/* ── 커스텀 전형 추가 ────────────────────────────────────────── */}
      <div className="pt-1">
        <AnimatePresence mode="wait">
          {!addingCustom ? (
            <motion.button
              key="add-btn"
              initial={{ opacity: 0 }}
              animate={{ opacity: 1 }}
              exit={{ opacity: 0 }}
              onClick={() => setAddingCustom(true)}
              className="flex w-full items-center gap-2 rounded-xl border border-dashed border-muted-foreground/20 px-3 py-2.5 text-xs text-muted-foreground transition-colors hover:border-primary/30 hover:text-primary"
            >
              <Plus className="size-3.5" />
              커스텀 전형 추가 (커피챗, 과제전형 등)
            </motion.button>
          ) : (
            <motion.div
              key="add-form"
              initial={{ opacity: 0, y: -4 }}
              animate={{ opacity: 1, y: 0 }}
              exit={{ opacity: 0, y: -4 }}
              className="flex items-center gap-2 rounded-xl border border-primary/30 bg-primary/5 px-3 py-2"
            >
              <Zap className="size-3.5 shrink-0 text-primary" />
              <Input
                autoFocus
                placeholder="전형명 입력 (예: 커피챗)"
                value={customLabel}
                onChange={(e) => setCustomLabel(e.target.value)}
                className="h-7 flex-1 border-0 bg-transparent p-0 text-xs shadow-none focus-visible:ring-0"
                onKeyDown={(e) => {
                  if (e.key === "Escape") {
                    setAddingCustom(false)
                    setCustomLabel("")
                  }
                }}
              />
              {customLabel.trim() && (
                <InlineDatePicker
                  value={undefined}
                  onSelect={handleAddCustom}
                />
              )}
              <button
                onClick={() => { setAddingCustom(false); setCustomLabel("") }}
                className="text-muted-foreground/50 hover:text-foreground"
              >
                <X className="size-3.5" />
              </button>
            </motion.div>
          )}
        </AnimatePresence>
      </div>
    </div>
  )
}
