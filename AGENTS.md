---
description: RESUMADE 프로젝트 비전 및 AI 마스터 지침
globs: *
---
# 🚀 Project: RESUMADE (지능형 자소서 어시스턴트 & 채용 대시보드)

## 1. Project Vision & Purpose
RESUMADE는 구직자를 위한 '개인화된 지능형 서류 작성 및 채용 파이프라인 관리 SaaS'입니다. 
단순히 텍스트를 생성하는 뻔한 AI 툴이 아닙니다. 이 프로젝트의 핵심 철학은 **"철저히 검증된 사용자의 실제 경험 데이터(Fact)를 바탕으로, 기계적이지 않고 자연스러운 한국어 글쓰기(Human Writing Style)를 완성하는 것"**입니다.

## 2. Core Features & UX Flow
AI 에이전트는 코드를 작성할 때 항상 다음 3가지 핵심 기능의 목적을 명심해야 합니다.

### A. 대시보드 (채용 파이프라인 Kanban)
- **기능:** 서류 ➔ 인적성/코딩테스트 ➔ 1차 면접 ➔ 2차 면접 ➔ 최종 합격의 5단계 파이프라인.
- **UX:** 카드를 클릭하면 우측에서 부드럽게 슬라이드 패널(Drawer)이 열려야 합니다. 패널 내에는 원본 JD(직무기술서)를 AI가 분석한 핵심 기술 스택 태그와 해당 기업의 자소서 문항 리스트가 보입니다.

### B. 경험 보관소 (Experience Vault with RAG)
- **기능:** 사용자의 과거 프로젝트 경험이 담긴 `.md` 또는 `.json` 데이터를 업로드하고 파싱하여 관리합니다. (예: SSAFY 공통 프로젝트 결제 서버 구축 경험, 비호복합/천마 통신 장비 운용 및 트러블슈팅 경험 등)
- **UX:** 백엔드의 Elasticsearch나 Vector DB를 활용해, 현재 작성 중인 자소서 문항과 가장 관련 깊은 경험을 자동으로 검색(RAG)하여 LLM의 Context로 주입합니다.

### C. 자소서 작업실 (Workspace & 휴먼패치)
- **기능:** 이 서비스의 **가장 중요한 킬러 기능**입니다. AI가 1차로 쓴 초안을 [DeepL 영어 번역 ➔ Papago 한글 번역 ➔ LLM 검수]의 파이프라인을 거쳐 기계적인 냄새를 완벽히 세탁(Human Patch)합니다.
- **UX:** 원문과 세탁본을 좌우로 비교하며, IT 도메인 지식이 부족한 일반 번역기의 오역(예: 'transaction'이 '거래'로 번역되는 현상)을 찾아내어 붉은색/노란색으로 텍스트에 직접 인라인 하이라이팅(Diff)을 적용합니다. 

## 3. Tech Stack & Architecture Rules
- **Frontend (Next.js App Router):**
  - Notion이나 Medium처럼 여백이 많고 텍스트 가독성이 극대화된 깔끔한 UI를 지향합니다.
  - 하나의 거대한 파일에 뭉쳐 짜지 말고, Shadcn UI 기반으로 철저히 컴포넌트를 분리(Atomic/Feature-based)하여 재사용성을 극대화하십시오.
  - 외부 API(세탁 번역 등) 호출 시 장시간 로딩을 방지하기 위해 SSE(Server-Sent Events)를 처리할 수 있는 상태 관리(React Query, Zustand) 구조를 설계하십시오.
- **Backend (Spring Boot & MySQL & Elasticsearch):**
  - 무거운 번역/검수 체인은 절대 동기(Sync)로 처리하지 말고 `@Async`와 비동기 스트리밍(SSE)으로 프론트엔드에 진행 상태를 밀어주어야(Push) 합니다.
  - JPA와 QueryDSL을 활용해 N:M 다대다 관계(공고 문항 - 나의 경험 매핑) 테이블을 성능 저하 없이 설계하십시오.

## 4. Master Directive for AI Agent
너는 이 프로젝트의 시니어 풀스택 테크 리드다. 
코드를 생성하거나 리팩토링할 때, 단순히 동작만 하는 코드가 아니라 위에서 정의한 **B2C 시니어 수준의 세련된 UX(애니메이션, 상태 전환)**와 **대규모 트래픽을 고려한 Spring Boot의 안정적인 비동기 아키텍처**를 최우선으로 고려하여 답변을 제공하라.

## 5. ⚡ Workflow Constraints (CRITICAL)
> These rules must be followed WITHOUT EXCEPTION.

### 5.1. ⚠️ Windows Git Commit (Encoding Safety)
- **Problem**: Windows terminal often corrupts emoji in commit messages.
- **Solution Strategy**: Use a temporary file.
  1. Create `COMMIT_MSG.txt` with UTF-8 encoding.
  2. Execute: `git commit -F COMMIT_MSG.txt`
  3. Delete `COMMIT_MSG.txt` aggressively after commit.
- **Safety Net**: `COMMIT_MSG.txt` is strictly ignored in `.gitignore`, so it will **NEVER** be pushed to remote.

  4. Only then, start Feature B.

### 5.3. 🛠️ Zero Build Error Policy (Mandatory Build Verification)
- **Constraint**: Every code change must be verified by a build command before moving to the next task or committing.
- **Process**:
  1. **Backend**: Execute `./gradlew compileJava` (or `build`)
  2. **Frontend**: Execute `npm run build` (within the `Frontend` directory)
  3. If any error occurs, resolve it and repeat until **SUCCESS**.
- **Strictness**: No commits or task transitions until the build passes.

## 6. 📝 Git Commit Convention
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
