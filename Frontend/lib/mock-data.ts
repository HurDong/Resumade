// Application Status Types
export type ApplicationStatus = "document" | "aptitude" | "interview1" | "interview2" | "passed"

export interface Application {
  id: string
  company: string
  position: string
  deadline: Date
  techStack: string[]
  questions: {
    id: string
    title: string
    maxLength: number
    currentLength: number
  }[]
  status: ApplicationStatus
  logoColor: string
  rawJd?: string
}

export interface Experience {
  id: string
  title: string
  category: string
  description: string
  techStack: string[]
  metrics: string[]
  period: string
  role: string
}

// Mock Applications Data
export const mockApplications: Application[] = [
  // 서류 전형
  {
    id: "app-1",
    company: "Kakao",
    position: "백엔드 엔지니어",
    deadline: new Date(Date.now() + 3 * 24 * 60 * 60 * 1000),
    techStack: ["Spring Boot", "Kotlin", "MySQL", "Redis"],
    questions: [
      { id: "q1", title: "성장 경험", maxLength: 1000, currentLength: 850 },
      { id: "q2", title: "협업 경험", maxLength: 800, currentLength: 400 },
      { id: "q3", title: "문제 해결", maxLength: 1000, currentLength: 0 },
    ],
    status: "document",
    logoColor: "bg-yellow-400",
  },
  {
    id: "app-2",
    company: "Naver",
    position: "서버 개발자",
    deadline: new Date(Date.now() + 7 * 24 * 60 * 60 * 1000),
    techStack: ["Java", "Spring", "MongoDB", "Kafka"],
    questions: [
      { id: "q1", title: "기술적 도전", maxLength: 1500, currentLength: 1200 },
      { id: "q2", title: "팀 프로젝트", maxLength: 1000, currentLength: 950 },
    ],
    status: "document",
    logoColor: "bg-green-500",
  },

  // 인적성/코딩테스트
  {
    id: "app-3",
    company: "Toss",
    position: "플랫폼 엔지니어",
    deadline: new Date(Date.now() + 5 * 24 * 60 * 60 * 1000),
    techStack: ["Go", "Kubernetes", "gRPC", "PostgreSQL"],
    questions: [
      { id: "q1", title: "Toss를 선택한 이유", maxLength: 500, currentLength: 480 },
      { id: "q2", title: "핵심 역량", maxLength: 800, currentLength: 650 },
      { id: "q3", title: "성장 계획", maxLength: 600, currentLength: 200 },
    ],
    status: "aptitude",
    logoColor: "bg-blue-600",
  },
  {
    id: "app-4",
    company: "Coupang",
    position: "백엔드 개발자",
    deadline: new Date(Date.now() + 2 * 24 * 60 * 60 * 1000),
    techStack: ["Java", "AWS", "DynamoDB", "Spring Boot"],
    questions: [
      { id: "q1", title: "업무 경험", maxLength: 1000, currentLength: 1000 },
    ],
    status: "aptitude",
    logoColor: "bg-red-500",
  },

  // 1차 면접
  {
    id: "app-5",
    company: "Line",
    position: "서버 엔지니어",
    deadline: new Date(Date.now() + 4 * 24 * 60 * 60 * 1000),
    techStack: ["Kotlin", "Spring WebFlux", "MySQL", "Redis"],
    questions: [
      { id: "q1", title: "기술적 깊이", maxLength: 1200, currentLength: 1150 },
      { id: "q2", title: "리더십 경험", maxLength: 800, currentLength: 800 },
    ],
    status: "interview1",
    logoColor: "bg-green-400",
  },
  {
    id: "app-6",
    company: "Woowa Brothers",
    position: "백엔드 개발자",
    deadline: new Date(Date.now() + 1 * 24 * 60 * 60 * 1000),
    techStack: ["Java", "Spring Boot", "JPA", "MySQL"],
    questions: [
      { id: "q1", title: "자기소개", maxLength: 500, currentLength: 500 },
    ],
    status: "interview1",
    logoColor: "bg-cyan-400",
  },

  // 2차 면접
  {
    id: "app-7",
    company: "당근마켓",
    position: "플랫폼 개발자",
    deadline: new Date(Date.now() + 3 * 24 * 60 * 60 * 1000),
    techStack: ["Ruby on Rails", "PostgreSQL", "Redis", "Sidekiq"],
    questions: [
      { id: "q1", title: "경력 비전", maxLength: 800, currentLength: 800 },
    ],
    status: "interview2",
    logoColor: "bg-orange-400",
  },

  // 최종 합격
  {
    id: "app-8",
    company: "토스",
    position: "시니어 백엔드 엔지니어",
    deadline: new Date(Date.now() - 5 * 24 * 60 * 60 * 1000),
    techStack: ["Kotlin", "Spring Boot", "Kubernetes", "PostgreSQL"],
    questions: [
      { id: "q1", title: "성장 경험", maxLength: 1000, currentLength: 1000 },
      { id: "q2", title: "기술 역량", maxLength: 800, currentLength: 800 },
    ],
    status: "passed",
    logoColor: "bg-blue-600",
  },
]

// Mock Experiences Data
export const mockExperiences: Experience[] = [
  {
    id: "exp-1",
    title: "결제 서버 아키텍처 개선",
    category: "백엔드 개발",
    description: "고동시성 트랜잭션을 처리하기 위해 결제 처리 서버 아키텍처를 재설계했습니다. Kafka를 사용한 이벤트 기반 아키텍처를 구현하여 비동기 처리를 실현하고 응답 시간을 40% 단축했습니다.",
    techStack: ["Spring Boot", "Kafka", "Redis", "MySQL"],
    metrics: ["응답 시간 40% 감소", "99.99% 가용성", "처리량 3배 증가"],
    period: "2023.06 - 2023.12",
    role: "기술 리더",
  },
  {
    id: "exp-2",
    title: "트래픽 최적화 프로젝트",
    category: "성능 엔지니어링",
    description: "전자상거래 플랫폼의 피크 판매 시기 트래픽 최적화 주도. 캐싱 전략 및 데이터베이스 쿼리 최적화를 구현하여 정상 트래픽의 10배를 성공적으로 처리했습니다.",
    techStack: ["Redis", "Elasticsearch", "MySQL", "Nginx"],
    metrics: ["10배 트래픽 처리", "DB 부하 60% 감소", "P99 지연 100ms 이하"],
    period: "2023.01 - 2023.05",
    role: "백엔드 개발자",
  },
  {
    id: "exp-3",
    title: "마이크로서비스 전환",
    category: "시스템 아키텍처",
    description: "모놀리식에서 마이크로서비스로의 전환에 참여했습니다. gRPC를 사용한 서비스 간 통신을 설계 및 구현하고 각 서비스를 위한 CI/CD 파이프라인을 구축했습니다.",
    techStack: ["Kubernetes", "gRPC", "Docker", "ArgoCD"],
    metrics: ["15개 서비스 배포", "무중단 전환", "배포 속도 50% 향상"],
    period: "2022.07 - 2022.12",
    role: "플랫폼 엔지니어",
  },
  {
    id: "exp-4",
    title: "실시간 알림 시스템",
    category: "백엔드 개발",
    description: "푸시 알림, 이메일, SMS를 지원하는 확장 가능한 실시간 알림 시스템을 구축했습니다. 효율적인 메시지 배포를 위해 팬아웃 패턴을 구현하여 수백만 사용자에게 서비스했습니다.",
    techStack: ["Node.js", "WebSocket", "RabbitMQ", "MongoDB"],
    metrics: ["일일 500만 건 알림", "1초 이내 배송", "99.9% 배송률"],
    period: "2022.01 - 2022.06",
    role: "백엔드 개발자",
  },
  {
    id: "exp-5",
    title: "데이터 파이프라인 현대화",
    category: "데이터 엔지니어링",
    description: "레거시 배치 처리를 실시간 스트리밍 파이프라인으로 현대화했습니다. CDC(Change Data Capture)를 구현하여 여러 데이터 저장소 간 실시간 데이터 동기화를 실현했습니다.",
    techStack: ["Apache Flink", "Debezium", "Kafka", "AWS S3"],
    metrics: ["실시간 데이터 동기화", "비용 80% 절감", "지연 6시간에서 5분으로 단축"],
    period: "2021.06 - 2021.12",
    role: "데이터 엔지니어",
  },
  {
    id: "exp-6",
    title: "API Gateway 구현",
    category: "플랫폼 엔지니어링",
    description: "서비스 메시를 위한 중앙 API Gateway를 설계 및 구현했습니다. 속도 제한, 인증, 요청 변환 기능을 추가했습니다.",
    techStack: ["Kong", "Lua", "PostgreSQL", "Prometheus"],
    metrics: ["50개 이상 API 관리", "중앙 인증 시스템", "99.99% 가동 시간"],
    period: "2021.01 - 2021.05",
    role: "플랫폼 엔지니어",
  },
]

// Workspace Mock Data
export const mockWorkspaceData = {
  currentQuestion: {
    company: "Kakao",
    position: "백엔드 엔지니어",
    questionNumber: 2,
    questionTitle: "참여했던 도전적인 프로젝트를 설명하고 기술적 어려움을 어떻게 해결했는지 서술하세요.",
    maxLength: 1000,
  },
  extractedContext: [
    {
      id: "ctx-1",
      experienceTitle: "결제 서버 아키텍처 개선",
      relevantPart: "고동시성 트랜잭션을 처리하기 위해 결제 처리 서버 아키텍처를 재설계했습니다. Kafka를 사용한 이벤트 기반 아키텍처를 구현하여 비동기 처리를 실현했습니다.",
      relevanceScore: 95,
    },
    {
      id: "ctx-2",
      experienceTitle: "트래픽 최적화 프로젝트",
      relevantPart: "전자상거래 플랫폼의 피크 판매 시기 트래픽 최적화를 주도했습니다. 캐싱 전략 및 데이터베이스 쿼리 최적화를 구현했습니다.",
      relevanceScore: 87,
    },
  ],
  draft: `이전 핀테크 회사에서 근무하며 결제 서버 아키텍처 개선 프로젝트에 핵심 멤버로 참여했습니다. 당시 기존 시스템은 급증하는 트랜잭션 볼륨을 감당하지 못하고 있었으며, 피크 시간대에는 응답 시간이 허용 가능한 임계값을 크게 초과하는 상황이었습니다.

핵심 기술적 과제는 동기식 처리 모델이 병목 현상을 유발하고, 트랜잭션 처리 중 발생하는 데이터베이스 락(Lock)이 연쇄적인 지연을 일으킨다는 점이었습니다. 저는 이 문제를 해결하기 위해 Kafka를 활용한 이벤트 기반 아키텍처 도입을 제안했습니다.

먼저 전체 트랜잭션 흐름을 분석하여 비동기 처리가 가능한 작업들을 식별했습니다. 이후 트랜잭션 무결성을 유지하면서도 병렬 처리를 가능하게 하는 메시지 스키마를 설계했습니다. 구현 과정에서는 Kafka 클러스터 구축, 멱등성(Idempotent) 컨슈머 개발, 분산 트랜잭션을 위한 사가(Saga) 패턴 적용 등의 작업을 수행했습니다.

그 결과 응답 시간 40% 단축과 시스템 가용성 99.99% 달성이라는 성과를 이끌어냈습니다. 이 경험을 통해 시스템 병목 현상에 대한 깊은 이해와 확장성을 고려한 아키텍처 패턴 선택의 중요성을 체득할 수 있었습니다.`,
  originalText: `이전 핀테크 회사에서 결제 서버 아키텍처 개선 프로젝트에 참여했습니다. 기존 시스템은 증가하는 트랜잭션 볼륨을 처리하는 데 어려움을 겪고 있었으며, 피크 시간대에 응답 시간이 허용 가능한 임계값을 초과했습니다.

핵심 기술적 과제는 동기식 처리 모델이 병목 현상을 만들고, 트랜잭션 처리 중 데이터베이스 락이 연쇄적인 지연을 유발한다는 것이었습니다. 이를 해결하기 위해 Kafka를 사용한 이벤트 기반 아키텍처 도입을 제안했습니다.

먼저 트랜잭션 흐름을 분석하고 비동기적으로 처리할 수 있는 작업을 식별했습니다. 그 다음, 트랜잭션 무결성을 유지하면서 병렬 처리를 가능하게 하는 메시지 스키마를 설계했습니다. 구현에는 Kafka 클러스터 설정, 멱등성 컨슈머 구현, 분산 트랜잭션을 위한 사가 패턴 구축이 포함되었습니다.

결과적으로 응답 시간 40% 감소와 99.99% 가용성 향상을 달성했습니다. 이 경험을 통해 시스템 병목 현상 이해와 확장성을 위한 적절한 아키텍처 패턴 선택의 중요성을 배웠습니다.`,
  translatedText: `이전 핀테크 회사에서 결제 서버 설계 개선 프로젝트에 참여했습니다. 기존 시스템은 증가하는 거래 양을 처리하는 데 어려움을 겪고 있었으며, 피크 시간대에 응답 시간이 허용 가능한 기준을 초과했습니다.

핵심 기술적 과제는 동기식 처리 방식이 정체 현상을 만들고, 거래 처리 중 데이터베이스 잠금이 연쇄적인 지연을 유발한다는 것이었습니다. 이를 해결하기 위해 Kafka를 사용한 사건 기반 설계 도입을 제안했습니다.

먼저 거래 흐름을 분석하고 비동기적으로 처리할 수 있는 작업을 식별했습니다. 그 다음, 거래 무결성을 유지하면서 병렬 처리를 가능하게 하는 메시지 구조를 설계했습니다. 구현에는 Kafka 군집 설정, 멱등성 소비자 구현, 분산 거래를 위한 이야기 패턴 구축이 포함되었습니다.

결과적으로 응답 시간 40% 감소와 99.99% 가용성 향상을 달성했습니다. 이 경험을 통해 시스템 정체 현상 이해와 확장성을 위한 적절한 설계 패턴 선택의 중요성을 배웠습니다.`,
  mistranslations: [
    {
      id: "mis-1",
      original: "transaction (트랜잭션)",
      translated: "commerce (거래)",
      context: "Database Transaction - 데이터베이스 트랜잭션",
      severity: "high",
      suggestion: "'트랜잭션'으로 유지 - ACID 특성을 가진 데이터베이스 작업 단위를 의미하는 기술 용어",
    },
    {
      id: "mis-2",
      original: "architecture (아키텍처)",
      translated: "design (설계)",
      context: "System Architecture - 시스템 아키텍처",
      severity: "medium",
      suggestion: "'아키텍처'로 유지 - 시스템의 전체적인 구조를 의미하는 기술 용어",
    },
    {
      id: "mis-3",
      original: "bottleneck (병목)",
      translated: "congestion (정체)",
      context: "System Bottleneck - 시스템 병목 현상",
      severity: "medium",
      suggestion: "'병목'으로 유지 - 시스템 성능을 제한하는 지점을 의미하는 표준 기술 용어",
    },
    {
      id: "mis-4",
      original: "consumer (컨슈머)",
      translated: "consumer (소비자)",
      context: "Kafka Consumer - Kafka 컨슈머",
      severity: "high",
      suggestion: "'컨슈머'로 유지 - Kafka에서 메시지를 읽는 클라이언트를 의미하는 기술 용어",
    },
    {
      id: "mis-5",
      original: "saga pattern (사가 패턴)",
      translated: "story pattern (이야기 패턴)",
      context: "Saga Pattern for Distributed Transactions - 분산 트랜잭션 사가 패턴",
      severity: "high",
      suggestion: "'사가 패턴'으로 유지 - 분산 시스템에서 데이터 일관성을 유지하기 위한 설계 패턴",
    },
    {
      id: "mis-6",
      original: "cluster (클러스터)",
      translated: "group (군집)",
      context: "Kafka Cluster - Kafka 클러스터",
      severity: "high",
      suggestion: "'클러스터'로 유지 - 여러 서버가 하나처럼 동작하는 구성을 의미하는 기술 용어",
    },
  ],
  aiReviewReport: {
    overallScore: 72,
    technicalAccuracy: 45,
    contextPreservation: 68,
    readability: 92,
    summary: "번역이 기술 용어를 상당히 왜곡했습니다. 여러 중요한 기술 용어가 일반 어휘로 변환되어 기술 배경이 있는 채용담당자를 혼동시킬 수 있습니다. 강조된 용어에 대한 수동 수정을 강력히 권장합니다.",
    recommendations: [
      "'트랜잭션' 유지 - 데이터베이스 작업을 의미하는 기술 용어",
      "'아키텍처' 유지 - 시스템의 전체적인 구조를 의미하는 기술 용어",
      "'컨슈머' 유지 - Kafka에서 메시지를 읽는 클라이언트를 의미하는 기술 용어",
      "'사가 패턴' 유지 - 분산 시스템에서 데이터 일관성을 유지하는 설계 패턴",
      "'클러스터' 유지 - 여러 서버로 구성된 시스템을 의미하는 기술 용어",
    ],
  },
}

// D-Day 계산 헬퍼 함수
export function getDDay(deadline: Date): string {
  const today = new Date()
  today.setHours(0, 0, 0, 0)
  const target = new Date(deadline)
  target.setHours(0, 0, 0, 0)
  
  const diff = Math.ceil((target.getTime() - today.getTime()) / (1000 * 60 * 60 * 24))
  
  if (diff === 0) return "D-Day"
  if (diff > 0) return `D-${diff}`
  return `D+${Math.abs(diff)}`
}

// 진행도 계산 헬퍼 함수
export function calculateProgress(questions: Application["questions"]): number {
  if (!questions || questions.length === 0) return 0
  
  const totalMax = questions.reduce((sum, q) => sum + (q.maxLength || 1000), 0)
  const totalCurrent = questions.reduce((sum, q) => sum + (q.currentLength || 0), 0)
  
  if (totalMax === 0) return 0
  return Math.round((totalCurrent / totalMax) * 100)
}
