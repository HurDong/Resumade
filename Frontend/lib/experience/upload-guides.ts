export const EXPERIENCE_MARKDOWN_TEMPLATE = `# SSAFY 결제 서버 안정성 개선

## 한 줄 요약
결제 승인 흐름의 정합성과 운영 추적성을 높이기 위해 상태 전이와 장애 대응 흐름을 재설계한 프로젝트 경험

## 프로젝트 메타데이터
- 출처: SSAFY 공통 프로젝트
- 조직/소속: SSAFY
- 역할: 백엔드 개발 담당
- 기간: 2024.01 - 2024.06
- 카테고리: 백엔드 개발

## 프로젝트 전체 기술 스택
- Spring Boot
- JPA
- MySQL
- Redis

## 대표 직무 연결 키워드
- 백엔드
- 운영 안정성
- 데이터 정합성
- 장애 대응

## 대표 활용 가능한 문항 유형
- 직무 역량
- 문제 해결
- 협업

## Facet 1 | 결제 상태 전이 재설계
### 문제 상황
- 결제 승인 과정에서 중복 승인 가능성과 재시도 충돌이 동시에 발생했다.
- 운영 중 장애가 났을 때 어느 상태에서 꼬였는지 추적하기 어려웠다.

### 내가 맡은 역할
- 결제 상태 전이 로직을 설계하고 구현했다.
- 재시도와 예외 처리 경계를 서비스 계층 기준으로 정리했다.

### 내가 한 판단
- 단순 처리량보다 정합성과 운영 추적성을 먼저 확보해야 한다고 판단했다.
- 트랜잭션 범위를 축소하고 상태 전이를 명시적으로 나누는 구조가 더 안전하다고 봤다.

### 내가 실제로 한 행동
- 결제 상태 전이 로직을 별도 서비스 흐름으로 분리했다.
- 승인/실패/재시도 케이스별 상태 변경 조건을 다시 정의했다.
- 예외 로그와 재처리 기준을 운영 로그에서 바로 보이도록 정리했다.

### 결과
- 중복 승인 케이스를 줄였다.
- 장애 발생 시 어떤 상태에서 실패했는지 더 빠르게 추적할 수 있게 됐다.
- 운영 안정성이 개선됐다.

### 기술 스택
- Spring Boot
- JPA
- MySQL
- Transaction

### 직무 연결 키워드
- 백엔드
- 데이터 정합성
- 장애 대응

### 활용 가능한 문항 유형
- 문제 해결
- 직무 역량

## Facet 2 | 장애 원인 추적 로그 체계 정리
### 문제 상황
- 결제 장애가 발생했을 때 운영 로그만으로는 재현 경로를 빠르게 좁히기 어려웠다.

### 내가 맡은 역할
- 운영 로그와 예외 로그 포맷을 정리하는 작업을 맡았다.

### 내가 한 판단
- 장애 대응 속도를 높이려면 기능 구현과 별도로 운영 추적 정보를 남기는 구조가 필요하다고 판단했다.

### 내가 실제로 한 행동
- 상태 전이별 로그 포인트를 다시 배치했다.
- 재시도 여부와 실패 원인을 한 번에 확인할 수 있는 예외 로그 기준을 정의했다.

### 결과
- 장애 원인 파악 시간이 단축됐다.
- 운영자 입장에서 재현 포인트를 더 빠르게 찾을 수 있게 됐다.

### 기술 스택
- Spring Boot
- Logging
- MySQL

### 직무 연결 키워드
- 운영 안정성
- 관측 가능성
- 장애 대응

### 활용 가능한 문항 유형
- 문제 해결
- 협업
`;

export const EXPERIENCE_JSON_TEMPLATE = `{
  "schemaVersion": "experience-upload.v2",
  "title": "SSAFY 결제 서버 안정성 개선",
  "summary": "결제 승인 흐름의 정합성과 운영 추적성을 높이기 위해 상태 전이와 장애 대응 흐름을 재설계한 프로젝트 경험",
  "origin": "SSAFY 공통 프로젝트",
  "organization": "SSAFY",
  "role": "백엔드 개발 담당",
  "period": "2024.01 - 2024.06",
  "category": "백엔드 개발",
  "overallTechStack": ["Spring Boot", "JPA", "MySQL", "Redis"],
  "jobKeywords": ["백엔드", "운영 안정성", "데이터 정합성", "장애 대응"],
  "questionTypes": ["직무 역량", "문제 해결", "협업"],
  "facets": [
    {
      "title": "결제 상태 전이 재설계",
      "situation": [
        "결제 승인 과정에서 중복 승인 가능성과 재시도 충돌이 동시에 발생했다.",
        "운영 중 장애가 났을 때 어느 상태에서 꼬였는지 추적하기 어려웠다."
      ],
      "role": [
        "결제 상태 전이 로직을 설계하고 구현했다.",
        "재시도와 예외 처리 경계를 서비스 계층 기준으로 정리했다."
      ],
      "judgment": [
        "단순 처리량보다 정합성과 운영 추적성을 먼저 확보해야 한다고 판단했다.",
        "트랜잭션 범위를 축소하고 상태 전이를 명시적으로 나누는 구조가 더 안전하다고 봤다."
      ],
      "actions": [
        "결제 상태 전이 로직을 별도 서비스 흐름으로 분리했다.",
        "승인/실패/재시도 케이스별 상태 변경 조건을 다시 정의했다.",
        "예외 로그와 재처리 기준을 운영 로그에서 바로 보이도록 정리했다."
      ],
      "results": [
        "중복 승인 케이스를 줄였다.",
        "장애 발생 시 어떤 상태에서 실패했는지 더 빠르게 추적할 수 있게 됐다.",
        "운영 안정성이 개선됐다."
      ],
      "techStack": ["Spring Boot", "JPA", "MySQL", "Transaction"],
      "jobKeywords": ["백엔드", "데이터 정합성", "장애 대응"],
      "questionTypes": ["문제 해결", "직무 역량"]
    },
    {
      "title": "장애 원인 추적 로그 체계 정리",
      "situation": [
        "결제 장애가 발생했을 때 운영 로그만으로는 재현 경로를 빠르게 좁히기 어려웠다."
      ],
      "role": [
        "운영 로그와 예외 로그 포맷을 정리하는 작업을 맡았다."
      ],
      "judgment": [
        "장애 대응 속도를 높이려면 기능 구현과 별도로 운영 추적 정보를 남기는 구조가 필요하다고 판단했다."
      ],
      "actions": [
        "상태 전이별 로그 포인트를 다시 배치했다.",
        "재시도 여부와 실패 원인을 한 번에 확인할 수 있는 예외 로그 기준을 정의했다."
      ],
      "results": [
        "장애 원인 파악 시간이 단축됐다.",
        "운영자 입장에서 재현 포인트를 더 빠르게 찾을 수 있게 됐다."
      ],
      "techStack": ["Spring Boot", "Logging", "MySQL"],
      "jobKeywords": ["운영 안정성", "관측 가능성", "장애 대응"],
      "questionTypes": ["문제 해결", "협업"]
    }
  ]
}`;

export const EXPERIENCE_MARKDOWN_SCHEMA_TEMPLATE = `# [프로젝트 제목]

## 한 줄 요약
[검증 가능한 프로젝트 요약]

## 프로젝트 메타데이터
- 출처: [프로젝트 / 저장소 / 활동 맥락]
- 조직/소속: [조직 / 팀 / 과정명]
- 역할: [프로젝트 단위에서 맡은 역할]
- 기간: [예: 2024.01 - 2024.06]
- 카테고리: [예: 백엔드 개발]

## 프로젝트 전체 기술 스택
- [프로젝트 전반 기술]

## 대표 직무 연결 키워드
- [프로젝트 전체 관점 키워드]

## 대표 활용 가능한 문항 유형
- [프로젝트 전체 관점 문항 유형]

## Facet 1 | [문항에 꽂을 수 있는 사건 제목]
### 문제 상황
- [당시 문제나 맥락]

### 내가 맡은 역할
- [그 사건에서 맡은 역할]

### 내가 한 판단
- [왜 그렇게 판단했는지]

### 내가 실제로 한 행동
- [실제로 한 행동]

### 결과
- [근거 있는 결과]

### 기술 스택
- [이 facet에 직접 걸리는 기술]

### 직무 연결 키워드
- [이 facet가 연결되는 키워드]

### 활용 가능한 문항 유형
- [이 facet를 쓸 수 있는 문항 유형]

## Facet 2 | [필요한 경우에만 추가]
### 문제 상황
- [근거가 충분할 때만 작성]
`;

export const EXPERIENCE_LOCAL_PROMPT = `아래 경험 원문을 RESUMADE 업로드용 canonical schema v2 Markdown으로 구조화해줘.

핵심 원칙:
- 반드시 프로젝트 1개만 반환해. 여러 프로젝트가 섞여 있으면 가장 근거가 풍부한 1개만 남겨.
- facet는 기술 스택 분류가 아니라 문항에 꽂을 수 있는 사건/판단/행동/결과 단위여야 해.
- minor contribution이나 근거가 약한 내용은 억지로 facet로 만들지 마.
- facet가 꼭 여러 개일 필요는 없어. 근거가 약하면 1개만 두거나 필요한 값만 "미확인"으로 적어.
- 없는 수치, 없는 역할, 없는 성과, 없는 기술은 절대 만들지 마.
- 애매하면 빈칸 대신 "미확인"을 쓰거나 bullet를 줄여.
- 프로젝트 메타데이터와 facet block을 분리해.
- "프로젝트 전체 기술 스택"에는 프로젝트 전반 스택을, 각 facet의 "기술 스택"에는 그 사건에 직접 걸리는 기술만 적어.
- 최종 출력은 업로드 가능한 Markdown 본문 하나만 반환해.
- 코드블록(\`\`\`), 설명 문장, 머리말, 꼬리말을 붙이지 마.
- 응답은 반드시 \`# \` 제목으로 시작해야 해.
- 아래 섹션 제목을 그대로 사용해.

반환 형식:
아래 구조는 자리표시자 예시이며, 대괄호 안의 문구는 실제 내용으로 치환해야 한다.

${EXPERIENCE_MARKDOWN_SCHEMA_TEMPLATE}`;

export const EXPERIENCE_GITHUB_PROMPT = `You are extracting fact-grounded experience records from a GitHub repository or project materials for RESUMADE.

Goal:
Create one upload-ready Markdown file in RESUMADE canonical schema v2.
The output must represent one project plus one or more evidence-backed facets.

Critical rules:
- Return exactly one project record.
- A facet is an event-level unit that can anchor a self-introduction answer: situation, your role, judgment, action, result.
- Do not create facets for weak or minor contributions. Fewer facets is better than invented structure.
- Codebase-first research is mandatory. Read repository structure, README, and the implementation files end-to-end before writing.
- Use only evidence visible in code, README, docs, or explicitly supplied notes.
- Never invent impact metrics, ownership scope, production scale, team size, or technologies.
- If evidence is partial, write "미확인" instead of guessing.
- Keep project-level metadata and facet-level blocks separate.
- Put broad repository stack in "프로젝트 전체 기술 스택".
- Put only facet-direct technologies in each facet's "기술 스택".
- Return Markdown only.
- Do not return JSON.
- Do not wrap the answer in code fences.
- Do not add commentary before or after the Markdown.
- The first line must start with "# ".
- Use the exact section headings from the schema.
- If there is only one solid facet, return one facet block only.

Recommended research flow:
1. Scan the repository structure.
2. Read README for product goal and architecture.
3. Trace one substantial feature end-to-end.
4. Follow related controllers, services, repositories, schedulers, listeners, DTOs, and configs.
5. Use commits only as a secondary hint for likely ownership boundaries.
6. Separate project-wide stack from facet-direct stack.
7. Write one canonical upload-ready Markdown project with evidence-backed facets.

Output schema:
${EXPERIENCE_MARKDOWN_SCHEMA_TEMPLATE}`;

export const REINDEXING_NOTES = [
  "질문 문구만 바뀌면 기존 파일을 다시 올리지 않아도 됩니다.",
  "facet 구조, 임베딩 텍스트, 검색 기준이 바뀌면 기존 경험은 재분류 또는 재업로드 후 재인덱싱하는 편이 좋습니다.",
  "legacy flat Markdown/JSON도 계속 허용되지만, facet 중심 검색 품질은 schema v2로 정리할수록 좋아집니다.",
];
