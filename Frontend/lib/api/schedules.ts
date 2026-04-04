import { toApiUrl } from "@/lib/network/api-base"

// ── 타입 ────────────────────────────────────────────────────────────────────

export type ScheduleType =
  | "DOCUMENT_DEADLINE"
  | "CODING_TEST"
  | "APTITUDE"
  | "INTERVIEW1"
  | "INTERVIEW2"
  | "CUSTOM"

export interface ApplicationSchedule {
  id: number
  applicationId: number
  companyName: string
  position: string
  type: ScheduleType
  /** UI 표시용 레이블 (CUSTOM이면 customLabel, 나머지는 기본값) */
  label: string
  /** 캘린더 이벤트 타입 매핑 */
  calendarEventType: string
  /** ISO-8601 LocalDateTime */
  scheduledAt: string
  memo: string | null
  sortOrder: number
}

export interface CreateScheduleRequest {
  type: ScheduleType
  customLabel?: string
  scheduledAt: string
  memo?: string
  sortOrder?: number
}

export interface UpdateScheduleRequest {
  scheduledAt?: string
  memo?: string
  customLabel?: string
  sortOrder?: number
}

// ── 기본 전형 목록 (UI 타임라인용) ───────────────────────────────────────────

export const DEFAULT_SCHEDULE_TYPES: {
  type: ScheduleType
  label: string
  sortOrder: number
}[] = [
  { type: "DOCUMENT_DEADLINE", label: "서류 마감",   sortOrder: 0 },
  { type: "CODING_TEST",       label: "코딩테스트", sortOrder: 1 },
  { type: "APTITUDE",          label: "인적성검사", sortOrder: 2 },
  { type: "INTERVIEW1",        label: "1차 면접",   sortOrder: 3 },
  { type: "INTERVIEW2",        label: "2차 면접",   sortOrder: 4 },
]

// ── API 함수 ────────────────────────────────────────────────────────────────

const base = (appId: number) =>
  toApiUrl(`/api/v1/applications/${appId}/schedules`)

export async function fetchSchedules(applicationId: number): Promise<ApplicationSchedule[]> {
  const res = await fetch(base(applicationId), { cache: "no-store" })
  if (!res.ok) throw new Error(`일정 로드 실패: ${res.status}`)
  return res.json()
}

export async function createSchedule(
  applicationId: number,
  req: CreateScheduleRequest
): Promise<ApplicationSchedule> {
  const res = await fetch(base(applicationId), {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(req),
  })
  if (!res.ok) throw new Error(`일정 추가 실패: ${res.status}`)
  return res.json()
}

export async function updateSchedule(
  applicationId: number,
  scheduleId: number,
  req: UpdateScheduleRequest
): Promise<ApplicationSchedule> {
  const res = await fetch(`${base(applicationId)}/${scheduleId}`, {
    method: "PATCH",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(req),
  })
  if (!res.ok) throw new Error(`일정 수정 실패: ${res.status}`)
  return res.json()
}

export async function deleteSchedule(
  applicationId: number,
  scheduleId: number
): Promise<void> {
  const res = await fetch(`${base(applicationId)}/${scheduleId}`, {
    method: "DELETE",
  })
  if (!res.ok) throw new Error(`일정 삭제 실패: ${res.status}`)
}
