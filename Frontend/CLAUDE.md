# Frontend Rules (Next.js)

## Product Features (UX 참고)
- **Kanban 대시보드**: 서류→인적성→1차면접→2차면접→최종합격 5단계. 카드 클릭 시 우측 Drawer 슬라이드.
- **Experience Vault**: .md/.json 업로드 → Elasticsearch RAG로 자소서 문항과 관련 경험 자동 검색.
- **Workspace (킬러 기능)**: AI 초안 → DeepL→Papago→LLM 검수 파이프라인(Human Patch). 원문/세탁본 Diff 하이라이팅.

## Data Layer Architecture
실제 사용 중인 패턴 (React Query는 미도입, 추후 서버 상태 라이브러리로 전환 예정):

| 레이어 | 위치 | 역할 |
|---|---|---|
| API 함수 | `lib/api/[feature].ts` | typed fetch 함수. 반드시 `toApiUrl(path)` 사용 |
| SSE 스트리밍 | `lib/network/stream-sse.ts` | `streamSse()` 유틸 사용 (직접 EventSource 금지) |
| 글로벌 상태 | `lib/store/[feature]-store.ts` | Zustand `create()`. 복잡 SSE 상태, 파이프라인 상태 |
| 로컬 UI 상태 | 컴포넌트 내 `useState` | 폼 입력, 토글 등 단순 UI 상태 |
| 타입 정의 | `lib/[feature]/types.ts` | 기능별 타입 co-location |

```ts
// API 함수 작성 시 필수 패턴
import { toApiUrl } from "@/lib/network/api-base"
export async function fetchXxx(...): Promise<XxxResponse> {
  const res = await fetch(toApiUrl("/api/v1/xxx"), { ... })
  if (!res.ok) throw new Error(await res.text())
  return res.json()
}
```

**환경변수**: 클라이언트용 `NEXT_PUBLIC_RESUMADE_API_BASE_URL`, 서버용 `RESUMADE_API_BASE_URL`.
절대 API URL 하드코딩 금지.

## Component Architecture
- **Feature-based**: `components/[feature]/` 디렉토리. 하나의 파일에 모든 로직 몰아넣기 금지.
- **RSC 우선**: `'use client'`는 상호작용(state, hooks)이 필요한 최하단 컴포넌트에만.
- **신규 컴포넌트**: `components/ui/`의 Radix 기반 컴포넌트 먼저 확인 후 만들기.

## Naming Conventions
- **Files/Dirs**: `kebab-case` (예: `application-card.tsx`)
- **Components/Types**: `PascalCase`
- **Functions/Variables**: `camelCase`
- **Zustand Store**: `use[Feature]Store` (예: `useWorkspaceStore`)
- **UI 텍스트**: 모두 한국어

## Animation (framer-motion 우선)
| 우선순위 | 라이브러리 | 용도 |
|---|---|---|
| 1 | **framer-motion** (설치됨) | 마운트/언마운트, 상태전환, 드래그, Stagger |
| 2 | Tailwind `animate-*` | hover/focus 단순 CSS 전환 |
| 3 | **GSAP** (미설치) | 랜딩/히어로 타임라인. 필요 시 `pnpm add gsap` |
| 금지 | Three.js / @react-three/fiber | 강한 정당화 없이 사용 금지 |

필수 패턴: 마운트/언마운트에 `AnimatePresence` 필수. 리스트에 `staggerChildren: 0.05`. 패널류 `x: 16` 슬라이드.

## Required UI States (빠뜨리면 미완성)
| 상태 | 구현 기준 |
|---|---|
| loading | `Skeleton` 또는 `animate-pulse` |
| empty | 아이콘 + 안내 문구 + CTA. 빈 div 금지 |
| error | 에러 메시지 + 재시도 버튼. 콘솔 에러만 금지 |
| hover | `transition-colors duration-150` 이상 |
| focus | `focus-visible:ring-2` |
| disabled | `opacity-50 cursor-not-allowed pointer-events-none` |
| active | 배경색 또는 테두리로 명확히 구분 |

## Design System (shadcn/ui)
- style: `new-york`, color: `neutral`, CSS variables 기반.
- 커스텀 색상은 `globals.css`의 `--primary`, `--muted`, `--border` 등으로. hex 하드코딩 금지.
- 조건부 클래스: `cn()` 유틸. 인라인 `style={}` 남용 금지.

## Spacing & CTA
- 섹션 간격 `space-y-6`+, 카드 내부 `p-4`~`p-6`, 아이콘-텍스트 `gap-2`.
- 타이포: `text-sm font-medium text-muted-foreground` (레이블) → `text-base font-semibold` (카드제목) → `text-xl font-bold` (페이지제목).
- Primary CTA 페이지당 1개 (`variant="default"`). 보조: `outline`/`ghost`. 위험: `destructive` + 확인 Dialog.

## 금지 목록
- `transition-all` (→ `transition-colors`, `transition-transform` 등 구체적으로)
- `drop-shadow-[0_0_20px_rgba(...)]` 과한 glow
- `blur-3xl` 이상 배경 블러 여러 겹
- `animate-bounce` (알림 뱃지 외 금지), `animate-spin` (로딩 외 장식 금지)
- spring 물리 애니메이션을 데이터 표시에 사용 (드래그 인터랙션에만)

## 접근성 & 성능
- `useReducedMotion()` 감지 후 duration 0으로 단락.
- 비텍스트 요소에 `aria-label` / `alt` 필수. 터치 타겟 최소 `min-h-[44px] min-w-[44px]`.

---

## UI 자동 처리 프로토콜

### 자동 감지 키워드
"UI 개선", "밋밋해", "세련되게", "살려줘", "답답해", "피드백이 없어", "애니메이션 추가", "polish"

### 키워드 → 파일 라우팅
| 키워드 | 대상 경로 |
|---|---|
| 칸반, 대시보드, 파이프라인, 지원 카드 | `components/kanban-board/**` + `app/page.tsx` |
| 경험, vault, 업로드 | `components/experience-vault/**` + `app/vault/page.tsx` |
| 워크스페이스, 작업실, 자소서, 에디터 | `components/workspace-editor/**` + `app/workspace/**` |
| 사이드바, 내비 | `components/app-sidebar.tsx` |
| 캘린더, 일정 | `components/recruitment-calendar/**` |
| 프로필, 이력서 | `components/profile-library/**` |
| 설정 | `app/settings/page.tsx` |

### 처리 순서
1. 키워드로 대상 파일 결정 → 파일 읽기
2. 문제 1~3줄 요약 후 개선 실행 (3개 파일 이상이면 사용자에게 순서 확인)
3. 기능 로직 절대 변경 금지. UI/스타일/상태만 개선.
4. 변경 파일 + 내역 간결히 출력
