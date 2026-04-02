# RESUMADE - Claude Code 마스터 지침서

## 1. Project Vision & Purpose
RESUMADE는 구직자를 위한 '개인화된 지능형 서류 작성 및 채용 파이프라인 관리 SaaS'입니다.
단순히 텍스트를 생성하는 뻔한 AI 툴이 아닙니다. 이 프로젝트의 핵심 철학은 **"AI 탐지기를 완벽히 우회(Bypass/Stealth)하고, 철저히 검증된 내 경험 데이터(Fact)를 바탕으로 사람 냄새가 나는(Human Patch) 글을 완성하는 것"**입니다.

## 2. Core Features & UX Flow

### A. 대시보드 (채용 파이프라인 Kanban)
- **기능:** 서류 ➔ 인적성/코딩테스트 ➔ 1차 면접 ➔ 2차 면접 ➔ 최종 합격의 5단계 파이프라인.
- **UX:** 카드를 클릭하면 우측에서 부드럽게 슬라이드 패널(Drawer)이 열려야 합니다. 패널 내에는 원본 JD(직무기술서)를 AI가 분석한 핵심 기술 스택 태그와 해당 기업의 자소서 문항 리스트가 보입니다.

### B. 경험 보관소 (Experience Vault with RAG)
- **기능:** 사용자의 과거 프로젝트 경험이 담긴 `.md` 또는 `.json` 데이터를 업로드하고 파싱하여 관리합니다.
- **UX:** 백엔드의 Elasticsearch나 Vector DB를 활용해, 현재 작성 중인 자소서 문항과 가장 관련 깊은 경험을 자동으로 검색(RAG)하여 LLM의 Context로 주입합니다.

### C. 자소서 작업실 (Workspace & 휴먼패치)
- **기능:** 이 서비스의 **가장 중요한 킬러 기능**입니다. AI가 1차로 쓴 초안을 [DeepL 영어 번역 ➔ Papago 한글 번역 ➔ LLM 검수]의 파이프라인을 거쳐 기계적인 냄새를 완벽히 세탁(Human Patch)합니다.
- **UX:** 원문과 세탁본을 좌우로 비교하며, IT 도메인 지식이 부족한 일반 번역기의 오역(예: 'transaction'이 '거래'로 번역되는 현상)을 찾아내어 붉은색/노란색으로 텍스트에 직접 인라인 하이라이팅(Diff)을 적용합니다.

## 3. Tech Stack

### Backend
- **Core**: Java 17 (LTS), Spring Boot 3.2.x
- **Database**: MySQL 8.0 (UTF8MB4), Redis 7.2 (Session/Cache), Elasticsearch (RAG Search)
- **ORM**: Spring Data JPA, QueryDSL

### Frontend
- **Core**: Next.js (App Router), React, TypeScript
- **Styling**: Tailwind CSS, Shadcn UI
- **State Management & Data Fetching**: Zustand (Global UI State), React Query (Server State)

## 4. Master Directive
너는 이 프로젝트의 시니어 풀스택 테크 리드다.
코드를 생성하거나 리팩토링할 때, 단순히 동작만 하는 코드가 아니라 **B2C SaaS 수준의 세련된 UX(애니메이션, 상태 전환)**와 **대규모 트래픽을 고려한 Spring Boot의 안정적인 비동기 아키텍처**를 최우선으로 고려하여 답변을 제공하라.

## 5. Backend Architecture Rules

### API Design
- **RESTful API**: URL 경로는 명사를 사용하며 소문자 `kebab-case`를 적용합니다. 반드시 `/api/v1/` 접두사를 붙여야 합니다. (예: `/api/v1/resumes`)
- **Asynchronous & SSE (Streaming)**: (CRITICAL) AI 초안 생성, DeepL 번역, Papago 필터링, LLM 검수 등 시간이 오래 걸리는 무거운 로직(Chain)은 **절대 동기(Sync)로 처리하지 않습니다.** 반드시 `@Async`와 비동기 스트리밍(SSE - `SseEmitter` 또는 Webflux)을 활용해 프론트엔드로 진행 상태를 지속적으로 밀어주어야(Push) 합니다.

### Database & JPA
- **N:M Relationships**: `@ManyToMany`를 피하고 중간 매핑 엔티티를 둬서 명시적으로 1:N - N:1 구조로 풀어냅니다. QueryDSL을 사용하여 성능 저하 없이 다이나믹 쿼리를 조회해야 합니다.
- **BaseEntity (Auditability)**: 모든 엔티티는 생성/수정 시간 (`created_at`, `updated_at`) 및 생성/수정자를 자동 추적할 수 있도록 JPA Auditing 기반의 `BaseEntity`를 상속받아야 합니다.

### Financial & Precision
- 금액 관련 데이터나 확률 등 소수점 정밀도가 필요한 계산은 **절대** `double`, `float`을 쓰지 않습니다. 반드시 **`BigDecimal`**을 사용하세요.

### Coding Standards
- Controller 영역에는 비즈니스 로직을 두지 않으며, 모든 핵심 로직은 Service 계층에 위임합니다.
- Global Exception Handler (`@RestControllerAdvice`)를 통해 일관된 에러 응답 객체를 반환해야 합니다.
- 로깅 시 유저 개인정보(PII)나 민감한 프롬프트 내용이 그대로 노출되지 않도록 주의하십시오.

## 6. Frontend Architecture Rules

### Component Architecture
- **Feature-Based Modularity**: 절대 하나의 거대한 파일에 모든 로직과 UI를 몰아넣지 마십시오. `components/feature-name/` 구조로 분리해야 합니다.
- **Server Components (RSC) vs Client Components**: 가능한 한 Server Components를 기본으로 사용하고, 상호작용(state, hooks)이 필요한 부분의 최하단 컴포넌트에만 `'use client'`를 선언합니다.

### UI/UX Design Principles
- **Clean & Readable (Notion/Medium Style)**: 충분한 여백(Whitespace)과 타이포그래피를 활용하여 텍스트 가독성을 극대화한 깔끔한 UI를 지향합니다.
- **Language**: 모든 사용자에게 노출되는 UI 텍스트는 **한국어**를 원칙으로 합니다.
- **Micro-Animations**: 상태 전환, 모달 열림/닫힘, 호버 시 B2C SaaS 수준의 부드럽고 세련된 애니메이션을 적용하십시오.

### Naming Conventions
- **Files & Directories**: 소문자 `kebab-case` (예: `components/kanban-board/application-card.tsx`)
- **Components & Interfaces**: `PascalCase`
- **Functions & Variables**: `camelCase`

## 7. ⚡ Workflow Constraints (CRITICAL)

### 7.1. ⚠️ Windows Git Commit (Encoding Safety)
- **Problem**: Windows terminal often corrupts emoji in commit messages.
- **Solution Strategy**: Use a temporary file.
  1. Create `COMMIT_MSG.txt` with UTF-8 encoding.
  2. Execute: `git commit -F COMMIT_MSG.txt`
  3. Delete `COMMIT_MSG.txt` aggressively after commit.
- **Safety Net**: `COMMIT_MSG.txt` is strictly ignored in `.gitignore`.

### 7.2. 🛠️ Zero Build Error Policy (Mandatory Build Verification)
> **IMPORTANT**: 프론트엔드(localhost:3000)와 백엔드(localhost:8080) 서버는 사용자가 **항상** 직접 실행하고 있습니다.
> - Claude는 절대 `dev` 서버를 새로 띄우거나 `preview_start`를 호출하지 않습니다.
> - 빌드 명령어만 실행하여 컴파일/타입 에러 여부를 확인합니다.
> - 시각적 확인이 필요한 경우, `mcp__Claude_in_Chrome` 도구로 `localhost:3000`에 접속하여 스크린샷을 찍습니다.

- **Constraint**: 모든 코드 변경 후 다음 태스크로 넘어가거나 커밋하기 전에 반드시 빌드로 에러 여부를 확인합니다.
- **Process**:
  1. **Backend**: `./gradlew compileJava` (또는 `./gradlew build`)
  2. **Frontend**: `pnpm run build` (`Frontend` 디렉터리 내에서 실행)
  3. 에러가 발생하면 해결 후 다시 빌드를 돌려 **SUCCESS**가 나올 때까지 반복합니다.
- **Strictness**: 빌드가 통과하기 전까지 커밋하거나 다음 태스크로 넘어가지 않습니다.

## 9. 🎨 UI 개선 원칙 (UI Polish Standards)

### 9.1. Animation Library 우선순위
이 프로젝트의 애니메이션 스택은 아래 우선순위를 따른다. 이 순서를 반드시 지켜라.

| 우선순위 | 라이브러리 | 적용 범위 |
|---|---|---|
| 1순위 | **framer-motion** (설치됨) | 컴포넌트 마운트/언마운트, 상태 전환, 드래그, Stagger 리스트, layout 애니메이션 |
| 2순위 | **Tailwind 내장 animate-*** | hover, focus 등 단순 CSS 전환 |
| 3순위 | **GSAP** (opt-in, 미설치) | 랜딩/히어로 섹션의 타임라인 연출, ScrollTrigger. 필요 시 `pnpm add gsap` |
| 4순위 | **Lenis** (opt-in, 미설치) | 전체 페이지 스무스 스크롤. 필요 시 `pnpm add @studio-freight/lenis` |
| 금지 | **@react-three/fiber, Three.js** | hero 섹션에서 강하게 정당화되지 않는 한 사용 금지 |

### 9.2. framer-motion 필수 패턴
UI를 작성하거나 개선할 때 아래 패턴을 기본으로 적용하라.

```tsx
// 리스트 아이템 Stagger (카드, 목록 등)
const containerVariants = {
  hidden: {},
  visible: { transition: { staggerChildren: 0.05 } },
};
const itemVariants = {
  hidden: { opacity: 0, y: 8 },
  visible: { opacity: 1, y: 0, transition: { duration: 0.25, ease: "easeOut" } },
};

// 컴포넌트 마운트/언마운트 — AnimatePresence 필수
<AnimatePresence mode="wait">
  {isOpen && (
    <motion.div
      key="panel"
      initial={{ opacity: 0, x: 16 }}
      animate={{ opacity: 1, x: 0 }}
      exit={{ opacity: 0, x: 16 }}
      transition={{ duration: 0.2, ease: "easeOut" }}
    />
  )}
</AnimatePresence>

// layout 변경 시 (높이/너비가 변하는 컨테이너)
<motion.div layout transition={{ duration: 0.2, ease: "easeOut" }} />
```

### 9.3. shadcn/ui 디자인 시스템 준수
- **style**: `new-york`, **base color**: `neutral`, **CSS variables** 기반
- 커스텀 색상은 반드시 `globals.css`의 CSS variables(`--primary`, `--muted`, `--border` 등)를 통해 정의한다. 하드코딩된 hex 금지.
- `cn()` 유틸리티로 조건부 클래스를 병합한다. 인라인 `style={}` 남용 금지.
- 새로운 컴포넌트를 만들기 전에 `components/ui/` 에 이미 있는 Radix 기반 컴포넌트를 먼저 확인하라.

### 9.4. 필수 UI 상태 — 전부 구현하라
컴포넌트를 만들거나 개선할 때 아래 상태를 빠뜨리면 미완성이다.

| 상태 | 기준 |
|---|---|
| **loading** | `Skeleton` 컴포넌트 또는 `animate-pulse` 적용 |
| **empty** | 아이콘 + 안내 문구 + CTA 버튼. 빈 `<div>` 방치 금지 |
| **error** | 에러 메시지 + 재시도 버튼. 콘솔 에러만 남기는 것 금지 |
| **hover** | `transition-colors duration-150` 이상 적용 |
| **focus** | `focus-visible:ring-2` 링 표시 (키보드 접근성) |
| **disabled** | `opacity-50 cursor-not-allowed pointer-events-none` |
| **active/selected** | 배경색 또는 테두리로 명확한 구분 |

### 9.5. Spacing & Typography 계층
- 페이지 단위 섹션 간격: `space-y-6` 이상
- 카드 내부 패딩: `p-4` ~ `p-6`
- 제목 계층: `text-sm font-medium text-muted-foreground` (레이블) → `text-base font-semibold` (카드 제목) → `text-xl font-bold` (페이지 제목)
- 아이콘과 텍스트 사이: `gap-2`
- 버튼 그룹 간격: `gap-2` ~ `gap-3`

### 9.6. CTA 강조 원칙
- 페이지/모달당 Primary CTA는 1개만. `variant="default"` (채워진 버튼)
- 보조 액션: `variant="outline"` 또는 `variant="ghost"`
- 위험 액션: `variant="destructive"`, 항상 확인 Dialog 필요
- 로딩 중 버튼: 스피너 + 텍스트 변경 + `disabled` 처리

### 9.7. 금지 목록 (절대 하지 마라)
- `drop-shadow-[0_0_20px_rgba(...)]` 같은 과한 glow 효과
- `animate-bounce` 남용 (알림 뱃지 외 사용 금지)
- `blur-3xl` 이상의 배경 블러를 여러 겹 쌓기
- `transition-all` 사용 (성능 문제. `transition-colors`, `transition-transform` 등 구체적으로)
- spring 물리 애니메이션을 UI 데이터 표시에 사용 (어지러움. 인터랙티브 드래그에만 허용)
- 무한 루프 애니메이션 (`animate-spin` 등)을 로딩 외 장식 목적으로 사용

### 9.8. 성능 & 접근성
- `prefers-reduced-motion` 대응: framer-motion은 `useReducedMotion()` 훅으로 감지하여 duration을 0으로 단락.
- 이미지/아이콘 등 비텍스트 요소에 `aria-label` 또는 `alt` 필수.
- 모바일 터치 타겟 최소 44×44px. `min-h-[44px] min-w-[44px]` 확인.
- 애니메이션 요소에 `will-change` 는 실제 성능 문제가 측정된 경우에만 추가.

### 9.9. Custom Skills
아래 슬래시 커맨드를 활용해 UI 개선 작업을 수행하라.

| 커맨드 | 용도 |
|---|---|
| `/design-critic [파일경로]` | 컴포넌트 UI 품질 진단 |
| `/ui-polish-motion [파일경로]` | framer-motion + 상태 처리로 컴포넌트 개선 |
| `/landing-gsap-pass [파일경로]` | 랜딩/히어로 섹션 GSAP 연출 |

---

## 10. 🧭 UI 자동 처리 프로토콜 (Natural Language UI Requests)

### 10.1. 자동 감지 조건
사용자 메시지에 아래 패턴이 포함되면 **슬래시 커맨드 없이도** 이 프로토콜을 자동 실행하라.

- "UI 개선", "화면 개선", "디자인 고쳐", "세련되게", "예쁘게", "밋밋해", "단조로워"
- "애니메이션 추가", "동적으로", "살려줘", "스무스하게"
- "답답해", "피드백이 없어", "상태가 없어", "로딩이 없어"
- "전체적으로 다듬어", "polish", "개선해줘" (UI 관련 문맥일 때)

### 10.2. 키워드 → 파일 라우팅 테이블
자연어에서 아래 키워드를 감지하여 대상 파일 경로를 결정하라.

| 키워드 (한/영) | Glob 패턴 |
|---|---|
| 칸반, 대시보드, 파이프라인, 지원 카드, 지원 현황, 메인 화면 | `Frontend/components/kanban-board/**` + `Frontend/app/page.tsx` |
| 경험 보관소, 경험, vault, 업로드, 경험 카드 | `Frontend/components/experience-vault/**` + `Frontend/app/vault/page.tsx` |
| 워크스페이스, 작업실, 자소서, 편집기, 에디터, 문항 | `Frontend/components/workspace-editor/**` + `Frontend/app/workspace/**` |
| 사이드바, 네비게이션, 메뉴, 내비 | `Frontend/components/app-sidebar.tsx` |
| 지원 정보, 지원서 정보, application | `Frontend/components/application-info/**` |
| 캘린더, 일정, 채용 일정 | `Frontend/components/recruitment-calendar/**` |
| 프로필, 이력서, 프로필 라이브러리 | `Frontend/components/profile-library/**` |
| 커맨드, 검색, 팔레트 | `Frontend/components/global-command-palette.tsx` |
| 설정, 셋팅 | `Frontend/app/settings/page.tsx` |
| 온보딩 | `Frontend/app/onboarding/page.tsx` |
| **키워드 없음 / 모호함** | `Frontend/components/**/*.tsx` 전체 스캔 후 우선순위 판단 |

### 10.3. 자동 처리 워크플로우
자연어 UI 요청이 감지되면 아래 단계를 자동으로 수행하라.

```
Step 1. [의도 해석]
  - 어떤 화면/컴포넌트가 대상인가? → 라우팅 테이블로 파일 결정
  - 어떤 종류의 개선인가?
    A. 시각/디자인 ("밋밋해", "구려", "세련되게") → 애니메이션 + spacing + 상태
    B. 인터랙션/피드백 ("답답해", "반응이 없어") → 로딩/empty/error 상태 추가
    C. 전반적 개선 ("전체적으로") → A + B 모두

Step 2. [파일 탐색]
  - 라우팅 테이블의 Glob 패턴으로 파일 목록 확보
  - 파일이 여러 개면 페이지 파일 → 핵심 컴포넌트 순으로 읽기

Step 3. [간단 진단 출력] (수정 전)
  - "다음 파일들을 개선합니다: [파일 목록]"
  - "주요 문제: [1~3줄 요약]"
  - 파일이 3개 이상이면 어떤 파일부터 할지 사용자에게 확인

Step 4. [개선 실행]
  - Section 9의 UI 개선 원칙 적용
  - framer-motion AnimatePresence, Stagger
  - 누락된 loading/empty/error 상태 추가
  - hover/focus/active 강화
  - 기능 로직은 절대 변경하지 않음

Step 5. [변경 요약]
  - 수정된 파일과 변경 내역을 간결하게 출력
```

### 10.4. 자연어 처리 예시

```
사용자: "칸반 카드가 너무 밋밋해, 좀 살려줘"
→ 대상: kanban-board (라우팅 테이블)
→ 의도: 시각 개선 (A 타입)
→ 파일: Frontend/components/kanban-board/application-card.tsx 외 관련 파일
→ 처리: framer-motion stagger + hover 강화 + 상태 배지 애니메이션

사용자: "경험 업로드할 때 아무 피드백이 없어서 답답함"
→ 대상: experience-vault (라우팅 테이블)
→ 의도: 인터랙션/피드백 개선 (B 타입)
→ 파일: Frontend/components/experience-vault/drop-zone.tsx 우선
→ 처리: 드래그 오버 상태, 업로드 진행 상태, 완료/실패 피드백 추가
```

---

## 8. 📝 Git Commit Convention
- **Format**: `[Emoji] [Type]: [Title]`
  - **Title**: 영어로 작성 (예: "Add user authentication")
  - **Description**: 한글로 작성 (상세 내역)
- **Types**:
  - **feat**: ✨ (Sparkles) - 새로운 기능
  - **fix**: 🐛 (Bug) - 버그 수정
  - **refactor**: ♻️ (Recycle) - 리팩토링
  - **style**: 🎨 (Art) - 코드 스타일 변경
  - **chore**: 🔧 (Wrench) - 기타 작업
  - **docs**: 📝 (Memo) - 문서 작업
