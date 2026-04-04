# Backend Rules (Spring Boot)

## Package Structure
Feature-based: `com.resumade.api.[feature].{domain, service, controller, dto, prompt}`
공통 인프라: `com.resumade.api.infra.{exception, sse, ai, elasticsearch}`

## API Design
- **URL**: `/api/v1/` 접두사 + 소문자 `kebab-case` 명사 (예: `/api/v1/workspace-questions`)
- **Heavy async ops**: AI 생성, 번역, 검수 등 시간이 오래 걸리는 작업은 **절대 동기 처리 금지**.
  `SseEmitter` + `@Async` 조합으로 SSE 스트리밍. `Utf8SseSupport` 유틸 활용.
- **Controller**: 비즈니스 로직 없음. 요청 파싱 → Service 위임 → 응답 반환만.

## DTO Naming Convention
| 상황 | 이름 |
|---|---|
| 클라이언트 → 서버 | `*Request` |
| 서버 → 클라이언트 | `*Response` |
| 내부 Service 간 전달 | `*Command` / `*Result` |
| AI/외부 API 파싱 | `*Dto` |

## Database & JPA
- **BaseEntity**: 모든 신규 엔티티는 `created_at`, `updated_at` 공통 상속 적용.
  현재 각 엔티티에 개별 선언 → **신규 엔티티 추가 시 반드시 BaseEntity 상속**.
  ```
  @MappedSuperclass @EntityListeners(AuditingEntityListener.class)
  abstract class BaseEntity { @CreatedDate LocalDateTime createdAt; @LastModifiedDate LocalDateTime updatedAt; }
  ```
- **N:M 금지**: `@ManyToMany` 사용 금지. 중간 매핑 엔티티로 1:N - N:1 분리.
- **Precision**: 금액/확률 계산은 `BigDecimal`. `double`/`float` 금지.
- **Lazy Loading**: 연관 엔티티는 기본 `LAZY`. JPQL fetch join 또는 QueryDSL로 필요 시 로딩.

## Service Layer
- `@Transactional` 은 Service 메서드에만 선언. Controller/Repository 금지.
- 읽기 전용 메서드는 `@Transactional(readOnly = true)` 필수 (성능 최적화).
- Service 클래스명: `*Service`. 인터페이스 분리는 외부 모듈 경계가 있을 때만.

## Exception Handling
- 커스텀 예외: `RuntimeException` 상속, feature별 `*NotFoundException`, `*InvalidStateException`.
- `GlobalExceptionHandler`(`@RestControllerAdvice`)에서 일관된 에러 응답:
  `{ "message": "...", "type": "..." }` 형식 유지.
- SSE 응답 중 클라이언트 disconnect 예외는 조용히 처리 (이미 `GlobalExceptionHandler`에 구현됨).
- 로깅 시 사용자 PII(이름, 이메일) 및 AI 프롬프트 원문 직접 노출 금지.

## 코딩 표준
- Lombok: `@Getter`, `@NoArgsConstructor(access = PROTECTED)`, `@Builder` 기본 세트.
- 엔티티 `equals`/`hashCode` 재정의 금지 (JPA Proxy 충돌). ID 비교만 사용.
- QueryDSL: 동적 쿼리, 복잡 조인 전용. 단순 조회는 Spring Data Repository 메서드명 쿼리.
- `@RestController` 반환 타입: 단순 조회 `ResponseEntity<T>`, SSE `SseEmitter`.
