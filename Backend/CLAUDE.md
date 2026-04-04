# Backend Rules (Spring Boot)

## 도메인 용어 사전
| 용어 | 설명 |
|---|---|
| **Application** | 지원 공고 1건. Kanban 카드 1개와 1:1 대응. `workspace/domain/Application.java` |
| **WorkspaceQuestion** | Application 하위 자소서 문항 1개. 초안~최종본까지의 상태를 보유 |
| **Experience (Vault)** | 구직자가 업로드한 경험 데이터(.md/.json). Elasticsearch에 인덱싱되어 RAG 소스로 사용 |
| **AI Draft** | `WorkspaceDraftAiService`가 경험 컨텍스트 + JD 기반으로 생성한 한국어 자소서 초안 |
| **Washed Draft** | AI 초안을 번역 서비스(DeepL→Papago→Google 우선순위)로 EN→KO 세탁한 결과물. AI 탐지 우회 목적 |
| **Human Patch** | `WorkspacePatchAiService`가 원본 초안 vs Washed Draft를 비교해 오번역/기술 용어 손실을 탐지·표시하는 검수 단계 |
| **Snapshot** | `QuestionSnapshot`: 문항별 버전 이력 저장. `SnapshotType`(DRAFT/WASHED/PATCHED/FINAL) 구분 |
| **Prompt Strategy** | `PromptFactory` + `QuestionCategory` 기반으로 문항 유형별 최적 프롬프트를 선택하는 전략 패턴 |

## AI 파이프라인 흐름
Workspace의 자소서 생성은 4단계 비동기 파이프라인으로 구성된다. 모든 단계는 `SseEmitter`로 스트리밍.

```
[1. 문항 분류]
ClassifierAiService (temp=0, gpt-4o-mini)
→ QuestionCategory 결정 (EXPERIENCE / MOTIVATION / COLLABORATION / ...)
→ PromptFactory.select(category) → 해당 PromptStrategy 반환

[2. AI 초안 생성]
StrategyDraftGeneratorService
→ PromptStrategy.build(DraftParams) → 프롬프트 조립
→ OpenAiResponsesWorkspaceDraftService (Primary, Responses API 스트리밍)
   └─ fallback: LangChain4j WorkspaceDraftAiService (레거시)
→ 결과: { title, text } JSON — WorkspaceQuestion.aiDraft에 저장

[3. 번역 세탁 (Human Patch 준비)]
RoutingTranslationService
→ translateToEnglish(aiDraft) → translateToKorean(englishText)
→ Provider 우선순위: deepl → (fallback) google/papago (application.yml: translation.provider)
→ 결과: washedDraft — WorkspaceQuestion.washedDraft에 저장

[4. Human Patch 검수]
WorkspacePatchAiService.analyzePatch(original, washed, ...)
→ CRITICAL/WARNING 오번역 탐지 + 인라인 <mark> 태깅
→ DraftAnalysisResult: { mistranslations[], aiReviewReport, humanPatchedText }
→ 프론트에서 Diff 하이라이팅 UI 렌더링

[보조 서비스]
FinalEditorAiService   — 최종 편집 (plain text 출력, temp=0.7)
SpellCheckAiService    — 맞춤법 교정 (temp=0, json_object)
ExperienceAiService    — Vault 업로드 시 경험 요약·태깅
JdTextAiService        — JD 텍스트 파싱
JdVisionAiService      — JD 이미지 OCR+파싱 (gpt-4o)
CompanyResearchService — Gemini 기반 기업 정보 수집
```

**모델 설정**: `application.yml` → `openai.models.*` 키로 서비스별 모델 독립 관리.
**신규 AI 서비스 추가 시**: `AiConfig.java`에 `@Bean` 등록 후 `@Value`로 모델명 주입.

## API 에러 응답 포맷
`GlobalExceptionHandler`가 모든 에러를 아래 형식으로 통일:

```json
{ "message": "에러 설명", "type": "ExceptionClassName" }
```

| 상황 | HTTP Status | 비고 |
|---|---|---|
| `IllegalArgumentException` | **400** Bad Request | 잘못된 입력값 |
| 그 외 모든 예외 | **500** Internal Server Error | 서버 오류 |
| SSE 응답 중 클라이언트 끊김 | 응답 없음 (null 반환) | `isClientDisconnectException()` 처리 |
| SSE 응답 중 예외 발생 | 응답 없음 (null 반환) | `Accept: text/event-stream` 감지 시 |

**커스텀 예외 작성 규칙**: `RuntimeException` 상속 + feature별 패키지 내 위치.
예: `workspace/exception/ApplicationNotFoundException.java`
프론트는 `res.ok` 체크 후 `res.text()`로 `message` 필드 파싱.

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
