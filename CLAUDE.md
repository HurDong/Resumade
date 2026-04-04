# RESUMADE

구직자를 위한 개인화된 지능형 서류 작성 및 채용 파이프라인 관리 SaaS.
핵심 철학: AI 탐지 우회(Human Patch) + 검증된 경험 데이터(RAG) 기반 자소서 생성.

## Tech Stack
- **Backend**: Java 17, Spring Boot 3.2, MySQL 8 (UTF8MB4), Redis 7.2, Elasticsearch, JPA+QueryDSL
- **Frontend**: Next.js (App Router), TypeScript, Tailwind CSS, Shadcn UI (new-york/neutral), Zustand, React Query, framer-motion

## Role
시니어 풀스택 테크 리드. B2C SaaS 수준의 세련된 UX + 대규모 트래픽을 고려한 안정적인 비동기 아키텍처를 최우선.

## Workflow (CRITICAL)

### 서버 환경
사용자가 프론트(localhost:3000)와 백(localhost:8080)을 항상 직접 실행 중.
- `dev` 서버 실행 및 `preview_start` 호출 **금지**.
- 시각적 확인 필요 시 `mcp__Claude_in_Chrome`으로 localhost:3000 접근.

### Zero Build Error Policy
코드 변경 후 반드시 빌드 성공 확인 → 그 후 커밋/다음 태스크.
- **Backend**: `cd Backend && ./gradlew compileJava`
- **Frontend**: `cd Frontend && pnpm run build`
- 에러 발생 시 해결 후 재빌드. SUCCESS 전까지 커밋 금지.

### Windows Git Commit (Encoding Safety)
Windows 터미널에서 이모지 커밋 메시지 인코딩 깨짐 방지:
1. `COMMIT_MSG.txt` 생성 (UTF-8)
2. `git commit -F COMMIT_MSG.txt`
3. `COMMIT_MSG.txt` 즉시 삭제 (`.gitignore`에 등록됨)

## Git Convention
**Format**: `[Emoji] [Type]: [영어 제목]` + 한글 상세 설명

| Type | Emoji | 용도 |
|---|---|---|
| feat | ✨ | 새 기능 |
| fix | 🐛 | 버그 수정 |
| refactor | ♻️ | 리팩토링 |
| style | 🎨 | 코드 스타일 |
| chore | 🔧 | 기타 작업 |
| docs | 📝 | 문서 |

## Branch & PR Convention
- **Branch**: `feat/...`, `fix/...`, `refactor/...` (kebab-case)
- **Base branch**: `main`
- **PR 제목**: 50자 이내 영어, Body는 한글로 변경 이유 + 테스트 방법 기술
