import { toApiUrl } from "@/lib/network/api-base"
import type { CalendarEvent, CalendarEventType } from "@/components/recruitment-calendar/calendar-mock-data"
import type { ApplicationStatus } from "@/lib/mock-data"

// ── 백엔드 응답 DTO ─────────────────────────────────────────────────────────

export interface CalendarEventDto {
  id: number
  applicationId: number
  /** "deadline" | "codingTest" | "aptitude" | "interview" */
  type: CalendarEventType
  /** ISO-8601 LocalDateTime (e.g. "2026-04-10T23:59:00") */
  date: string
  company: string
  position: string
  /** ApplicationStatus id: document / aptitude / interview1 / interview2 / passed */
  status: string
  /** ApplicationResult id: pending / pass / fail */
  result: "pending" | "pass" | "fail"
  logoUrl: string | null
  memo: string | null
}

// ── API 호출 ────────────────────────────────────────────────────────────────

export async function fetchCalendarEvents(
  year: number,
  month: number
): Promise<CalendarEventDto[]> {
  const res = await fetch(
    toApiUrl(`/api/v1/calendar/events?year=${year}&month=${month}`),
    { cache: "no-store" }
  )
  if (!res.ok) throw new Error(`캘린더 이벤트 로드 실패: ${res.status}`)
  return res.json()
}

// ── DTO → CalendarEvent 변환 ────────────────────────────────────────────────

/**
 * company 이름에서 결정론적 색상 클래스를 생성합니다.
 * 백엔드에 logoColor 필드가 없으므로 이름 해시로 대체합니다.
 */
const LOGO_COLORS = [
  "bg-blue-500",
  "bg-violet-500",
  "bg-emerald-500",
  "bg-amber-500",
  "bg-rose-500",
  "bg-cyan-500",
  "bg-indigo-500",
  "bg-pink-500",
  "bg-teal-500",
  "bg-orange-500",
]

function hashLogoColor(company: string): string {
  let hash = 0
  for (let i = 0; i < company.length; i++) {
    hash = (hash * 31 + company.charCodeAt(i)) >>> 0
  }
  return LOGO_COLORS[hash % LOGO_COLORS.length]
}

export function toCalendarEvent(dto: CalendarEventDto): CalendarEvent {
  return {
    id: String(dto.id),
    applicationId: String(dto.applicationId),
    company: dto.company,
    position: dto.position,
    type: dto.type,
    date: new Date(dto.date),
    status: dto.status as ApplicationStatus,
    result: dto.result,
    logoColor: hashLogoColor(dto.company),
    logoUrl: dto.logoUrl ?? undefined,
    memo: dto.memo ?? undefined,
  }
}
