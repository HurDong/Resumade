import { type Application, type ApplicationStatus } from "@/lib/mock-data"

// 캘린더 이벤트 타입
export type CalendarEventType = "deadline" | "interview" | "codingTest" | "aptitude"

export interface CalendarEvent {
  id: string
  applicationId: string
  company: string
  position: string
  type: CalendarEventType
  date: Date
  status: ApplicationStatus
  result?: "pending" | "pass" | "fail"
  logoColor: string
  logoUrl?: string
  memo?: string
}

// 이벤트 타입 라벨/색상 매핑
export const eventTypeConfig: Record<
  CalendarEventType,
  { label: string; color: string; bgColor: string; borderColor: string; dotColor: string }
> = {
  deadline: {
    label: "서류 마감",
    color: "text-blue-700",
    bgColor: "bg-blue-50",
    borderColor: "border-blue-200",
    dotColor: "bg-blue-500",
  },
  codingTest: {
    label: "코딩테스트",
    color: "text-violet-700",
    bgColor: "bg-violet-50",
    borderColor: "border-violet-200",
    dotColor: "bg-violet-500",
  },
  aptitude: {
    label: "인적성검사",
    color: "text-emerald-700",
    bgColor: "bg-emerald-50",
    borderColor: "border-emerald-200",
    dotColor: "bg-emerald-500",
  },
  interview: {
    label: "면접",
    color: "text-amber-700",
    bgColor: "bg-amber-50",
    borderColor: "border-amber-200",
    dotColor: "bg-amber-500",
  },
}

// 파이프라인 단계별 색상
export const statusConfig: Record<
  ApplicationStatus,
  { label: string; color: string; bgColor: string }
> = {
  document: { label: "서류", color: "text-primary", bgColor: "bg-primary/10" },
  aptitude: { label: "코테/인적성", color: "text-chart-2", bgColor: "bg-chart-2/10" },
  interview1: { label: "1차 면접", color: "text-chart-3", bgColor: "bg-chart-3/10" },
  interview2: { label: "2차 면접", color: "text-chart-4", bgColor: "bg-chart-4/10" },
  passed: { label: "최종 합격", color: "text-emerald-600", bgColor: "bg-emerald-500/10" },
}

// 오늘 날짜 기준으로 다양한 이벤트 생성
function createDate(dayOffset: number, hour = 23, minute = 59): Date {
  const d = new Date()
  d.setDate(d.getDate() + dayOffset)
  d.setHours(hour, minute, 0, 0)
  return d
}

// 특정 날짜 생성 (이번 달 기준)
function thisMonth(day: number, hour = 23, minute = 59): Date {
  const d = new Date()
  d.setDate(day)
  d.setHours(hour, minute, 0, 0)
  return d
}

export const mockCalendarEvents: CalendarEvent[] = [
  // --- 서류 마감 (deadline) ---
  {
    id: "evt-1",
    applicationId: "app-1",
    company: "카카오",
    position: "백엔드 엔지니어",
    type: "deadline",
    date: createDate(2, 18, 0),
    status: "document",
    result: "pending",
    logoColor: "bg-yellow-400",
    memo: "자소서 3번 문항 마무리 필요",
  },
  {
    id: "evt-2",
    applicationId: "app-2",
    company: "네이버",
    position: "서버 개발자",
    type: "deadline",
    date: createDate(5, 23, 59),
    status: "document",
    result: "pending",
    logoColor: "bg-green-500",
  },
  {
    id: "evt-3",
    applicationId: "app-10",
    company: "삼성SDS",
    position: "클라우드 엔지니어",
    type: "deadline",
    date: createDate(1, 17, 0),
    status: "document",
    result: "pending",
    logoColor: "bg-blue-700",
    memo: "포트폴리오 첨부 필수",
  },
  {
    id: "evt-4",
    applicationId: "app-11",
    company: "SK하이닉스",
    position: "임베디드 SW 개발",
    type: "deadline",
    date: createDate(2, 23, 59),
    status: "document",
    result: "pending",
    logoColor: "bg-red-600",
  },
  {
    id: "evt-5",
    applicationId: "app-12",
    company: "현대오토에버",
    position: "자율주행 플랫폼 개발",
    type: "deadline",
    date: createDate(8, 18, 0),
    status: "document",
    result: "pending",
    logoColor: "bg-sky-600",
    memo: "경력기술서 별도 제출",
  },
  {
    id: "evt-6",
    applicationId: "app-13",
    company: "LG CNS",
    position: "풀스택 개발자",
    type: "deadline",
    date: createDate(12, 23, 59),
    status: "document",
    result: "pending",
    logoColor: "bg-red-500",
  },

  // --- 코딩테스트 ---
  {
    id: "evt-7",
    applicationId: "app-3",
    company: "토스",
    position: "플랫폼 엔지니어",
    type: "codingTest",
    date: createDate(4, 14, 0),
    status: "aptitude",
    result: "pending",
    logoColor: "bg-blue-600",
    memo: "프로그래머스 플랫폼, 3시간",
  },
  {
    id: "evt-8",
    applicationId: "app-4",
    company: "쿠팡",
    position: "백엔드 개발자",
    type: "codingTest",
    date: createDate(3, 10, 0),
    status: "aptitude",
    result: "pending",
    logoColor: "bg-red-500",
    memo: "HackerRank, 알고리즘 5문제",
  },
  {
    id: "evt-9",
    applicationId: "app-14",
    company: "카카오뱅크",
    position: "서버 개발자",
    type: "codingTest",
    date: createDate(6, 13, 0),
    status: "aptitude",
    result: "pending",
    logoColor: "bg-yellow-500",
    memo: "코딩테스트 + SQL 문제 포함",
  },
  {
    id: "evt-10",
    applicationId: "app-15",
    company: "라인플러스",
    position: "백엔드 엔지니어",
    type: "codingTest",
    date: createDate(10, 15, 0),
    status: "aptitude",
    result: "pending",
    logoColor: "bg-green-500",
  },

  // --- 인적성검사 ---
  {
    id: "evt-11",
    applicationId: "app-16",
    company: "삼성전자",
    position: "SW 개발",
    type: "aptitude",
    date: createDate(7, 9, 0),
    status: "aptitude",
    result: "pending",
    logoColor: "bg-blue-800",
    memo: "GSAT 온라인, 오전 9시 시작",
  },
  {
    id: "evt-12",
    applicationId: "app-17",
    company: "LG전자",
    position: "플랫폼 SW 개발",
    type: "aptitude",
    date: createDate(7, 13, 0),
    status: "aptitude",
    result: "pending",
    logoColor: "bg-red-700",
    memo: "LG Way Fit Test, 오후 1시",
  },

  // --- 면접 ---
  {
    id: "evt-13",
    applicationId: "app-5",
    company: "LINE",
    position: "서버 엔지니어",
    type: "interview",
    date: createDate(3, 14, 0),
    status: "interview1",
    result: "pending",
    logoColor: "bg-green-400",
    memo: "기술 면접 (1시간), 화상",
  },
  {
    id: "evt-14",
    applicationId: "app-6",
    company: "우아한형제들",
    position: "백엔드 개발자",
    type: "interview",
    date: createDate(1, 10, 0),
    status: "interview1",
    result: "pending",
    logoColor: "bg-cyan-400",
    memo: "1차 기술면접, 잠실 본사 방문",
  },
  {
    id: "evt-15",
    applicationId: "app-7",
    company: "당근",
    position: "플랫폼 개발자",
    type: "interview",
    date: createDate(6, 11, 0),
    status: "interview2",
    result: "pending",
    logoColor: "bg-orange-400",
    memo: "2차 컬처핏 면접, 서초 본사",
  },
  {
    id: "evt-16",
    applicationId: "app-18",
    company: "비바리퍼블리카",
    position: "iOS 개발자",
    type: "interview",
    date: createDate(9, 15, 0),
    status: "interview1",
    result: "pending",
    logoColor: "bg-blue-500",
    memo: "1차 기술면접, 온라인",
  },
  {
    id: "evt-17",
    applicationId: "app-19",
    company: "넥슨",
    position: "게임 서버 개발자",
    type: "interview",
    date: createDate(11, 10, 30),
    status: "interview2",
    result: "pending",
    logoColor: "bg-indigo-600",
    memo: "2차 임원 면접, 판교",
  },

  // --- 지난 이벤트 (결과 있음) ---
  {
    id: "evt-18",
    applicationId: "app-20",
    company: "NHN",
    position: "백엔드 개발자",
    type: "deadline",
    date: createDate(-3, 23, 59),
    status: "document",
    result: "pass",
    logoColor: "bg-rose-500",
  },
  {
    id: "evt-19",
    applicationId: "app-21",
    company: "크래프톤",
    position: "서버 프로그래머",
    type: "codingTest",
    date: createDate(-5, 14, 0),
    status: "aptitude",
    result: "pass",
    logoColor: "bg-slate-700",
  },
  {
    id: "evt-20",
    applicationId: "app-22",
    company: "데브시스터즈",
    position: "백엔드 엔지니어",
    type: "interview",
    date: createDate(-2, 14, 0),
    status: "interview1",
    result: "fail",
    logoColor: "bg-purple-500",
    memo: "면접 피드백: 시스템 디자인 보완 필요",
  },
  {
    id: "evt-21",
    applicationId: "app-23",
    company: "야놀자",
    position: "풀스택 개발자",
    type: "deadline",
    date: createDate(-7, 23, 59),
    status: "document",
    result: "fail",
    logoColor: "bg-pink-500",
  },
  {
    id: "evt-22",
    applicationId: "app-8",
    company: "토스",
    position: "시니어 백엔드 엔지니어",
    type: "interview",
    date: createDate(-10, 14, 0),
    status: "passed",
    result: "pass",
    logoColor: "bg-blue-600",
    memo: "최종 합격!",
  },

  // --- 같은 날에 여러 이벤트 (밀집 테스트) ---
  {
    id: "evt-23",
    applicationId: "app-24",
    company: "배달의민족",
    position: "프론트엔드 개발자",
    type: "deadline",
    date: createDate(5, 23, 59),
    status: "document",
    result: "pending",
    logoColor: "bg-cyan-500",
  },
  {
    id: "evt-24",
    applicationId: "app-25",
    company: "카카오엔터프라이즈",
    position: "ML 엔지니어",
    type: "deadline",
    date: createDate(5, 18, 0),
    status: "document",
    result: "pending",
    logoColor: "bg-yellow-500",
    memo: "논문 포함 제출",
  },
  {
    id: "evt-25",
    applicationId: "app-26",
    company: "NC소프트",
    position: "AI 플랫폼 개발",
    type: "codingTest",
    date: createDate(4, 10, 0),
    status: "aptitude",
    result: "pending",
    logoColor: "bg-gray-700",
    memo: "AI/ML 코딩테스트, 2시간",
  },
]

// 날짜별로 이벤트를 그룹핑하는 유틸
export function groupEventsByDate(events: CalendarEvent[]): Map<string, CalendarEvent[]> {
  const map = new Map<string, CalendarEvent[]>()
  for (const event of events) {
    const key = formatDateKey(event.date)
    const existing = map.get(key) || []
    existing.push(event)
    map.set(key, existing)
  }
  return map
}

// 날짜 키 생성 (YYYY-MM-DD)
export function formatDateKey(date: Date): string {
  const y = date.getFullYear()
  const m = String(date.getMonth() + 1).padStart(2, "0")
  const d = String(date.getDate()).padStart(2, "0")
  return `${y}-${m}-${d}`
}

// D-Day 텍스트
export function getEventDDay(date: Date): string {
  const now = new Date()
  now.setHours(0, 0, 0, 0)
  const target = new Date(date)
  target.setHours(0, 0, 0, 0)
  const diff = Math.ceil((target.getTime() - now.getTime()) / (1000 * 60 * 60 * 24))
  if (diff === 0) return "D-Day"
  if (diff > 0) return `D-${diff}`
  return `D+${Math.abs(diff)}`
}
