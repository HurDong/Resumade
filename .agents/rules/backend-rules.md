---
description: Backend (Spring Boot) Architecture and API Rules
globs: Backend/**/*
---

# ⚙️ Backend Architecture & API Rules

## 1. Tech Stack
- **Core**: Java 17 (LTS), Spring Boot 3.2.x
- **Database**: MySQL 8.0 (UTF8MB4), Redis 7.2 (Session/Cache), Elasticsearch (RAG Search)
- **ORM**: Spring Data JPA, QueryDSL

## 2. API Design & Async Processing
- **RESTful API**: URL 경로는 명사를 사용하며 소문자 `kebab-case`를 적용합니다. 반드시 `/api/v1/` 접두사를 붙여야 합니다. (예: `/api/v1/resumes`)
- **Asynchronous & SSE (Streaming)**: (CRITICAL) AI 초안 생성, DeepL 번역, Papago 필터링, LLM 검수 등 시간이 오래 걸리는 무거운 로직(Chain)은 **절대 동기(Sync)로 처리하지 않습니다.** 반드시 `@Async`와 비동기 스트리밍(SSE - `SseEmitter` 또는 Webflux)을 활용해 프론트엔드로 진행 상태를 지속적으로 밀어주어야(Push) 합니다.

## 3. Database & JPA Rules
- **N:M Relationships**: 공고 문항과 나의 경험 간의 매핑 테이블 같은 다대다(N:M) 관계는 `@ManyToMany`를 피하고 중간 매핑 엔티티(Entity)를 둬서 명시적으로 1:N - N:1 구조로 풀어냅니다. QueryDSL을 사용하여 성능 저하 없이 다이나믹 쿼리를 조회해야 합니다.
- **BaseEntity (Auditability)**: 모든 엔티티는 생성/수정 시간 (`created_at`, `updated_at`) 및 생성/수정자를 자동 추적할 수 있도록 JPA Auditing 기반의 `BaseEntity`를 상속받아야 합니다.

## 4. Financial & Precision Constraints
- **Precision (Crucial)**: 금액 관련 데이터나 확률 등 소수점 정밀도가 필요한 계산은 **절대** `double`, `float`을 쓰지 않습니다. 반드시 **`BigDecimal`**을 사용하세요.
- 환율, 수익률 등 계산 시 `MathContext.DECIMAL128` 또는 명확한 반올림 정책(RoundingMode)을 컨트롤러/서비스 단에서 명시해야 합니다.

## 5. Coding Standards & Error Handling
- Controller 영역에는 비즈니스 로직을 두지 않으며, 모든 핵심 로직은 Service 계층에 위임합니다.
- Global Exception Handler (`@RestControllerAdvice`)를 통해 일관된 에러 응답 객체(공통 코드, 메시지 등)를 반환해야 합니다.
- 로깅 시 유저 개인정보(PII)나 민감한 프롬프트 내용이 그대로 노출되지 않도록 주의하십시오.
