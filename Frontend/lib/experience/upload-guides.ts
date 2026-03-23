export const EXPERIENCE_MARKDOWN_TEMPLATE = `# SSAFY 결제 서버 장애 대응

## 한 줄 요약
결제 승인 흐름의 정합성과 운영 추적성을 높이기 위해 트랜잭션 경계와 재시도 정책을 재설계한 경험

## 출처 / 맥락
- SSAFY 공통 프로젝트
- 백엔드 개발 담당

## 문제 상황
- 결제 승인 과정에서 중복 승인 가능성과 재시도 처리 충돌이 발생
- 운영 중 장애 원인 추적이 어려웠음

## 내가 맡은 역할
- 결제 상태 전이 로직 설계
- 예외 로그와 재처리 정책 정리

## 내가 한 판단
- 단순 성능보다 정합성과 운영 안정성을 우선해야 한다고 판단
- 트랜잭션 범위를 축소하고 상태 전이를 분리하는 방향으로 설계

## 내가 실제로 한 행동
- 결제 상태 전이 로직 분리
- 트랜잭션 경계 재설계
- 재시도 정책 및 예외 로그 체계 정리

## 결과
- 중복 승인 케이스 감소
- 장애 원인 추적성 향상
- 운영 안정성 개선

## 프로젝트 전체 기술 스택
- Spring Boot
- JPA
- MySQL
- Redis

## 기술 스택
- Spring Boot
- JPA
- MySQL
- Transaction

## 직무 연결 키워드
- 백엔드
- 운영 안정성
- 데이터 정합성
- 장애 대응

## 활용 가능한 문항 유형
- 문제 해결
- 직무 역량
- 협업
`;

export const EXPERIENCE_JSON_TEMPLATE = `{
  "title": "SSAFY 결제 서버 장애 대응",
  "summary": "결제 승인 흐름의 정합성과 운영 추적성을 높이기 위해 트랜잭션 경계와 재시도 정책을 재설계한 경험",
  "origin": "SSAFY 공통 프로젝트",
  "role": "백엔드 개발",
  "situation": [
    "결제 승인 과정에서 중복 승인 가능성과 재시도 충돌이 발생",
    "운영 중 장애 원인 추적이 어려웠음"
  ],
  "judgment": [
    "단순 성능보다 정합성과 운영 안정성을 우선해야 한다고 판단",
    "트랜잭션 범위를 축소하고 상태 전이를 분리하는 방향으로 설계"
  ],
  "actions": [
    "결제 상태 전이 로직 분리",
    "트랜잭션 경계 재설계",
    "재시도 정책 및 예외 로그 체계 정리"
  ],
  "results": [
    "중복 승인 케이스 감소",
    "장애 원인 추적성 향상",
    "운영 안정성 개선"
  ],
  "techStack": ["Spring Boot", "JPA", "MySQL", "Transaction"],
  "jobKeywords": ["백엔드", "운영 안정성", "데이터 정합성", "장애 대응"],
  "questionTypes": ["문제 해결", "직무 역량", "협업"]
}`;

export const EXPERIENCE_MARKDOWN_SCHEMA_TEMPLATE = `# [경험 제목]

## 한 줄 요약
[검증 가능한 핵심 요약]

## 출처 / 맥락
- [프로젝트 / 조직 / 저장소]
- [담당 역할]

## 문제 상황
- [당시 문제를 bullet로 정리]
- 필요하면 여러 개 작성

## 내가 맡은 역할
- [본인 역할을 bullet로 정리]
- 필요하면 여러 개 작성

## 내가 한 판단
- [판단 근거를 bullet로 정리]
- 필요하면 여러 개 작성

## 내가 실제로 한 행동
- [실행 내용을 bullet로 정리]
- 필요하면 여러 개 작성

## 결과
- [확인 가능한 결과를 bullet로 정리]
- 필요하면 여러 개 작성

## 프로젝트 전체 기술 스택
- [README와 코드베이스에서 확인한 프로젝트 전반 기술]
- 필요하면 여러 개 작성

## 기술 스택
- [실제로 사용한 기술]
- 필요하면 여러 개 작성

## 직무 연결 키워드
- [직무와 연결되는 키워드]
- 필요하면 여러 개 작성

## 활용 가능한 문항 유형
- [활용 가능한 문항 유형]
- 필요하면 여러 개 작성
`;

export const EXPERIENCE_LOCAL_PROMPT = `아래 경험 원문을 RESUMADE 업로드용 사실 기반 파일로 구조화해줘.

중요 규칙:
- 반드시 경험 1개만 반환해. 여러 경험이 보이면 가장 근거가 풍부한 1개만 선택해.
- 절대 없는 수치, 없는 도구, 없는 성과를 만들지 마.
- 애매하면 비워두거나 "미확인"이라고 써.
- 추상적인 미사여구보다 인터뷰에서 검증 가능한 사실을 우선해.
- 문제 상황 / 내가 맡은 역할 / 내가 한 판단 / 내가 실제로 한 행동 / 결과를 분리해.
- "프로젝트 전체 기술 스택"에는 프로젝트 전반 스택을, "기술 스택"에는 이 경험에 직접 쓰인 기술만 적어.
- 결과는 과장하지 말고 근거가 있는 것만 적어.
- 최종 출력은 업로드 가능한 Markdown 본문 하나만 반환해.
- 코드블록(\`\`\`), 설명 문장, 머리말, 꼬리말, 번호 목록을 붙이지 마.
- 응답은 반드시 \`# \` 제목으로 시작해야 해.
- 섹션 제목은 아래 스키마의 문구를 그대로 사용해.

반환 형식:
아래 구조는 출력 스키마 예시이며, 대괄호 안의 문구는 실제 내용으로 치환해야 하는 자리표시자다.

${EXPERIENCE_MARKDOWN_SCHEMA_TEMPLATE}`;

export const EXPERIENCE_GITHUB_PROMPT = `You are extracting fact-grounded experience records from a GitHub repository or project materials for RESUMADE.

Goal:
Create a single Markdown experience file that can be uploaded into an experience vault for RAG retrieval and interview-verifiable self-introduction writing.

Rules:
- Return exactly one experience record. If multiple plausible experiences exist, choose the single one with the strongest visible evidence.
- Codebase-first research is mandatory. Start by scanning the repository structure and reading the relevant implementation files end-to-end.
- Use README as a primary evidence source together with the codebase for feature intent, architecture, and product context.
- Prioritize source code evidence and README over all other materials.
- Base the experience mainly on implementation that spans multiple files, classes, modules, or flows, not on a single recent commit.
- Use commits only as a secondary signal to estimate likely author contribution in team projects.
- Never let commits outweigh codebase or README evidence.
- Do not rely on issues unless the user explicitly asks for them.
- Use only evidence that is actually visible from the codebase, README, repository docs, or explicitly supplied notes.
- Do not invent impact metrics, responsibilities, production scale, team size, or technologies.
- If evidence is partial, say "미확인" instead of guessing.
- Separate situation, role, judgment, actions, and results.
- Prefer concrete implementation details over polished summary language.
- Mention concrete code evidence when available: key modules, classes, endpoints, schedulers, event listeners, repositories, or data flows.
- If the code evidence is weak or too fragmented to support one solid experience, return "미확인" where needed instead of filling gaps with assumptions.
- Split the stack into two layers.
- In "프로젝트 전체 기술 스택", include the broader stack confirmed by README and codebase for the overall project.
- In "기술 스택", include only the technologies that materially participate in the chosen feature.
- Prefer explicit technology names exactly as they appear in README or codebase when available.
- Add jobKeywords and questionTypes based on evidence-backed inference only.
- Return upload-ready Markdown only.
- Do not return JSON.
- Do not wrap the answer in code fences.
- Do not add any introduction, explanation, commentary, or closing sentence.
- The first line must start with "# ".
- Use the exact section headings shown in the schema.
- Use bullet lists for list sections. Add as many bullets as needed.

Recommended research process:
1. Read repository structure and identify substantial feature areas.
2. Read README to understand the product goal, architecture, and feature framing.
3. Read the main implementation files for one feature end-to-end.
4. Trace related controllers, services, repositories, schedulers, event handlers, configs, and DTOs.
5. In team projects, review commits only to refine likely ownership or contribution boundaries for the already-identified feature.
6. Compile the broader project stack from README and codebase, then separate out the feature-direct stack for the chosen experience.
7. Produce one upload-ready Markdown experience grounded in the strongest code-backed feature.

Output format:
The structure below is a placeholder-only schema. Replace bracketed placeholders with repository-specific facts and do not reuse them literally.

${EXPERIENCE_MARKDOWN_SCHEMA_TEMPLATE}`;

export const REINDEXING_NOTES = [
  "질문 문구나 검색 로직만 바꾸면 기존 파일을 다시 넣지 않아도 됩니다.",
  "경험을 임베딩하는 서술 구조나 인덱싱 텍스트를 바꾸면 기존 경험을 다시 재인덱싱해야 효과가 납니다.",
  "원문 Markdown/JSON 자체를 더 구조화해서 보강하면 재업로드보다 재편집 후 재분류가 더 좋습니다.",
];
