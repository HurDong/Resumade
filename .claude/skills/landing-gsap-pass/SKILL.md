---
name: landing-gsap-pass
description: 랜딩/히어로 섹션에 GSAP 타임라인 + ScrollTrigger 연출을 적용한다. 파일 경로, "랜딩", "히어로", "메인 화면 화려하게" 같은 자연어 모두 허용. GSAP 미설치 시 자동 안내.
disable-model-invocation: true
---

너는 인터랙티브 웹 전문 시니어 프론트엔드 개발자다.
**이 스킬은 랜딩 페이지 / 히어로 섹션 전용이다. 일반 앱 UI(Kanban, Modal, Form)에 GSAP을 적용하지 마라.**

## Step 1: 대상 파일 결정

인자를 아래 순서로 해석하라.

1. **파일 경로** → 직접 사용
2. **자연어** → 아래 기준으로 파일 결정
   - "랜딩", "히어로", "메인", "소개 페이지" → `Frontend/app/page.tsx` 우선
   - "온보딩" → `Frontend/app/onboarding/page.tsx`
   - 기타 특정 섹션 언급 → 해당 컴포넌트 탐색
3. **인자 없음** → `Frontend/app/page.tsx`를 기본으로 시도

파일을 읽어 랜딩/히어로 섹션이 맞는지 확인한다. 일반 앱 UI 파일이면 중단하고 "이 파일은 `/ui-polish-motion`을 사용하세요"라고 안내한다.

## Step 2: 사전 준비

### GSAP 설치 확인
`Frontend/package.json`에서 `gsap` 여부 확인.
없으면:
```bash
cd Frontend && pnpm add gsap
```

### Lenis 스무스 스크롤 제안 (선택)
없으면 사용자에게 한 번 제안:
> "Lenis 스무스 스크롤도 추가할까요? (`pnpm add @studio-freight/lenis`)"
사용자가 원하면 설치 및 `app/layout.tsx` 연동.

## Step 3: GSAP 적용

### Hero 입장 타임라인
```tsx
"use client"
import { useRef, useLayoutEffect } from "react"
import gsap from "gsap"
import { ScrollTrigger } from "gsap/ScrollTrigger"

gsap.registerPlugin(ScrollTrigger)

export function HeroSection() {
  const containerRef = useRef<HTMLDivElement>(null)

  useLayoutEffect(() => {
    // prefers-reduced-motion 대응
    if (window.matchMedia("(prefers-reduced-motion: reduce)").matches) return

    const ctx = gsap.context(() => {
      const tl = gsap.timeline({ defaults: { ease: "power3.out" } })

      tl.from(".hero-badge",       { opacity: 0, y: -12, duration: 0.4 })
        .from(".hero-title",       { opacity: 0, y: 24,  duration: 0.6 }, "-=0.2")
        .from(".hero-description", { opacity: 0, y: 16,  duration: 0.5 }, "-=0.3")
        .from(".hero-cta",         { opacity: 0, y: 12,  duration: 0.4, stagger: 0.1 }, "-=0.3")
        .from(".hero-visual",      { opacity: 0, scale: 0.96, duration: 0.6 }, "-=0.4")
    }, containerRef)

    return () => ctx.revert()
  }, [])

  return <div ref={containerRef}>...</div>
}
```

### ScrollTrigger — Feature 카드 스크롤 페이드인
```tsx
gsap.utils.toArray<HTMLElement>(".feature-card").forEach((card, i) => {
  gsap.from(card, {
    opacity: 0,
    y: 32,
    duration: 0.5,
    delay: i * 0.08,
    ease: "power2.out",
    scrollTrigger: {
      trigger: card,
      start: "top 85%",
      toggleActions: "play none none none",
    },
  })
})
```

### ScrollTrigger — 텍스트 패럴랙스
```tsx
gsap.to(".parallax-text", {
  x: -60,
  ease: "none",
  scrollTrigger: {
    trigger: ".parallax-section",
    start: "top bottom",
    end: "bottom top",
    scrub: 1.5,
  },
})
```

## Step 4: Lenis 연동 (설치한 경우)

`app/layout.tsx`에 적용:
```tsx
"use client"
import { ReactLenis } from "@studio-freight/lenis/react"

export default function RootLayout({ children }) {
  return (
    <ReactLenis root options={{ lerp: 0.1, duration: 1.2, smoothWheel: true }}>
      {children}
    </ReactLenis>
  )
}
```

Lenis + ScrollTrigger 연동:
```tsx
lenis.on("scroll", ScrollTrigger.update)
gsap.ticker.add((time) => lenis.raf(time * 1000))
gsap.ticker.lagSmoothing(0)
```

## Step 5: 금지 사항
- `ctx.revert()` cleanup 없이 컴포넌트 언마운트 → **반드시 return에서 ctx.revert()**
- 여러 ScrollTrigger를 cleanup 없이 쌓기 → context 패턴으로 일괄 정리
- 일반 앱 UI에 GSAP 적용 → framer-motion 사용

## Step 6: 변경 요약
```
## 변경 요약

### 설치
- [gsap / lenis 등]

### 적용된 애니메이션
- Hero 타임라인: [설명]
- ScrollTrigger: [섹션명 — 연출 종류]
- Lenis: [적용 여부]

### SSR 처리
- [useLayoutEffect / prefers-reduced-motion 처리 내역]
```
