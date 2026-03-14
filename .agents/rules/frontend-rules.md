---
description: Frontend (Next.js) Architecture and UI Rules
globs: Frontend/**/*
---

# 🎨 Frontend Architecture & UI Rules

## 1. Tech Stack
- **Core**: Next.js (App Router), React, TypeScript
- **Styling**: Tailwind CSS, Shadcn UI
- **State Management & Data Fetching**: Zustand (Global UI State), React Query (Server State)

## 2. Component Architecture
- **Feature-Based Modularity**: 절대 하나의 거대한 파일에 모든 로직과 UI를 몰아넣지 마십시오. Atomic/Feature-based 패턴을 사용하여 재사용 가능하고 목적이 단일한 작은 컴포넌트 단위로 분리해야 합니다. (`components/feature-name/`)
- **Server Components (RSC) vs Client Components**: 가능한 한 Server Components를 기본으로 사용하여 성능을 최적화하고, 상호작용(state, hooks)이 필요한 부분의 최하단 컴포넌트에만 `'use client'`를 선언합니다.

## 3. UI/UX Design Principles
- **Clean & Readable (Notion/Medium Style)**: 복잡한 선과 박스보다는 충분한 여백(Whitespace)과 타이포그래피를 활용하여 텍스트 가독성을 극대화한 깔끔한 UI를 지향합니다.
- **Language**: 모든 메뉴, 버튼, 플레이스홀더 등 사용자에게 노출되는 UI 텍스트는 **한국어**를 원칙으로 합니다.
- **Micro-Animations**: 상태 전환, 모달 열림/닫힘, 호버 시 B2C SaaS 수준의 부드럽고 세련된 애니메이션을 적용하십시오.

## 4. API & Async Handling
- **SSE (Server-Sent Events)**: AI 번역 및 검수 파이프라인과 같은 장시간 실행되는 백엔드 작업의 경우, 일반 HTTPS Polling 대신 SSE를 사용하여 실시간 로딩 상태 및 스트리밍 데이터를 받도록 처리하십시오.
- **Error Handling**: API 호출 실패 시 사용자 친화적인 Toast/Alert 알림을 띄우고 자연스럽게 폴백(Fallback) UI를 렌더링해야 합니다.

## 5. Naming Conventions
- **Files & Directories**: 모두 소문자 `kebab-case`를 사용합니다. (예: `components/kanban-board/application-card.tsx`)
- **Components & Interfaces**: `PascalCase`를 사용합니다.
- **Functions & Variables**: `camelCase`를 사용합니다.
