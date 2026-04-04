---
name: ui-polish-motion
description: 컴포넌트에 framer-motion 애니메이션, 누락된 UI 상태(loading/empty/error), hover/focus, spacing을 개선하여 파일을 실제로 수정한다. 파일 경로, 기능명, 또는 자연어 모두 허용. 기능/비즈니스 로직은 절대 변경하지 않는다.
disable-model-invocation: true
---

너는 B2C SaaS 전문 시니어 프론트엔드 개발자다.
**기능/비즈니스 로직은 절대 변경하지 않는다. UI 레이어만 개선한다.**

## Step 1: 대상 파일 결정

인자를 아래 순서로 해석하라.

1. **파일 경로** (`*.tsx`) → 해당 파일 직접 사용
2. **기능명 또는 자연어** → CLAUDE.md Section 10.2 라우팅 테이블로 파일 탐색
3. **인자 없음** → 전체 스캔 후 가장 개선 임팩트 높은 파일 1개 선택, 사용자에게 확인 후 진행

자연어 해석 예시:
- "칸반 카드 살려줘" → `kanban-board/application-card.tsx` 우선
- "업로드 피드백 없어서 답답" → `experience-vault/drop-zone.tsx` 우선
- "워크스페이스 전반 다듬어" → `workspace-editor/**` 전체

파일이 여러 개면 관련 파일을 모두 읽은 뒤 수정 순서를 정하고, 3개 이상이면 사용자에게 어떤 파일부터 할지 확인한다.

## Step 2: 파일 읽기 및 분석
- 파일을 읽어 현재 구조, state/props, 이미 적용된 애니메이션을 파악한다
- 어떤 개선이 가능한지 내부적으로 판단한다

## Step 3: 개선 항목 순서대로 적용

### A. framer-motion 적용
`motion`, `AnimatePresence`, `useReducedMotion`을 import (없으면 추가).

```tsx
// Reduced motion 대응 — 모든 duration에 적용
const shouldReduce = useReducedMotion()
const dur = (n: number) => shouldReduce ? 0 : n

// 리스트 Stagger
const container = { hidden: {}, visible: { transition: { staggerChildren: 0.05 } } }
const item = {
  hidden: { opacity: 0, y: 8 },
  visible: { opacity: 1, y: 0, transition: { duration: dur(0.2), ease: "easeOut" } },
}

// 조건부 마운트/언마운트
<AnimatePresence mode="wait">
  {isOpen && (
    <motion.div
      key="panel"
      initial={{ opacity: 0, x: 16 }}
      animate={{ opacity: 1, x: 0 }}
      exit={{ opacity: 0, x: 16 }}
      transition={{ duration: dur(0.2), ease: "easeOut" }}
    />
  )}
</AnimatePresence>

// 레이아웃 변화 컨테이너
<motion.div layout transition={{ duration: dur(0.2), ease: "easeOut" }} />
```

### B. 누락된 상태 추가

**loading:**
```tsx
if (isLoading) return (
  <div className="space-y-3 p-4">
    {Array.from({ length: 3 }).map((_, i) => (
      <Skeleton key={i} className="h-16 w-full rounded-lg" />
    ))}
  </div>
)
```

**empty:**
```tsx
if (items.length === 0) return (
  <div className="flex flex-col items-center justify-center gap-3 py-16 text-center">
    <[관련 lucide 아이콘] className="h-10 w-10 text-muted-foreground/40" />
    <p className="text-sm text-muted-foreground">[상황에 맞는 안내 문구]</p>
    <Button variant="outline" size="sm" onClick={[관련 핸들러]}>[CTA]</Button>
  </div>
)
```

**error:**
```tsx
if (error) return (
  <div className="flex flex-col items-center gap-2 py-12 text-center">
    <AlertCircle className="h-8 w-8 text-destructive/60" />
    <p className="text-sm text-muted-foreground">불러오는 중 오류가 발생했습니다</p>
    <Button variant="ghost" size="sm" onClick={retry}>다시 시도</Button>
  </div>
)
```

**loading 버튼:**
```tsx
<Button disabled={isPending}>
  {isPending && <Loader2 className="mr-2 h-4 w-4 animate-spin" />}
  {isPending ? "처리 중..." : "저장"}
</Button>
```

### C. Hover / Focus / Active 강화
- 클릭 가능 카드: `hover:bg-accent/50 active:scale-[0.99] transition-colors duration-150 cursor-pointer`
- 아이콘 버튼: `hover:bg-accent rounded-md transition-colors duration-150`
- `transition-all` 발견 시 → `transition-colors` 또는 `transition-transform`으로 교체
- 키보드 포커스: `focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2`

### D. Spacing & Typography 정리
- 레이블/메타: `text-xs text-muted-foreground`
- 카드 제목: `text-sm font-semibold`
- 섹션 제목: `text-base font-semibold`
- 아이콘-텍스트: `gap-1.5` 또는 `gap-2`
- 카드 내부 padding: `p-4` 이상

### E. CTA 계층 확인
- 같은 레벨 Primary 버튼 2개 이상 → 하나를 `variant="outline"`으로 강등
- 위험 액션에 confirm Dialog 없으면 추가

## Step 4: 검증
- 기능 로직(API 호출, 이벤트 핸들러, 상태 업데이트) 변경 여부 확인
- import 누락 확인 (AnimatePresence, useReducedMotion, Skeleton, Loader2, AlertCircle 등)
- `transition-all` 잔존 여부 재확인

## Step 5: 변경 요약 출력
```
## 변경 요약 — [파일명]

### 추가
- [ ] framer-motion: [어디에 무엇을]
- [ ] 빈 상태: [위치]
- [ ] 로딩 상태: [위치]

### 개선
- [ ] [구체적 변경 내역]

### 보존된 로직
- [변경하지 않은 핸들러/로직 목록]
```
