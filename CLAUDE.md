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
> **IMPORTANT**: 백엔드와 프론트엔드 서버는 사용자가 이미 직접 실행하고 있습니다. Claude는 서버를 직접 실행하거나 `dev` 모드로 띄우려 하지 말고, **빌드 명령어만 실행하여 컴파일/타입 에러 여부만 확인**하면 됩니다.

- **Constraint**: 모든 코드 변경 후 다음 태스크로 넘어가거나 커밋하기 전에 반드시 빌드로 에러 여부를 확인합니다.
- **Process**:
  1. **Backend**: `./gradlew compileJava` (또는 `./gradlew build`)
  2. **Frontend**: `pnpm run build` (`Frontend` 디렉터리 내에서 실행)
  3. 에러가 발생하면 해결 후 다시 빌드를 돌려 **SUCCESS**가 나올 때까지 반복합니다.
- **Strictness**: 빌드가 통과하기 전까지 커밋하거나 다음 태스크로 넘어가지 않습니다.

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
