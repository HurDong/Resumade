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

export const EXPERIENCE_LOCAL_PROMPT = `아래 경험 원문을 RESUMADE 업로드용 사실 기반 파일로 구조화해줘.

중요 규칙:
- 절대 없는 수치, 없는 도구, 없는 성과를 만들지 마.
- 애매하면 비워두거나 "미확인"이라고 써.
- 추상적인 미사여구보다 인터뷰에서 검증 가능한 사실을 우선해.
- 문제 상황 / 내가 맡은 역할 / 내가 한 판단 / 내가 실제로 한 행동 / 결과를 분리해.
- 기술 스택은 실제로 사용한 것만 적어.
- 결과는 과장하지 말고 근거가 있는 것만 적어.
- 최종 출력은 Markdown 하나만 반환해.

반환 형식:
${EXPERIENCE_MARKDOWN_TEMPLATE}`;

export const EXPERIENCE_GITHUB_PROMPT = `You are extracting fact-grounded experience records from a GitHub repository or project materials for RESUMADE.

Goal:
Create a single Markdown experience file that can be uploaded into an experience vault for RAG retrieval and interview-verifiable self-introduction writing.

Rules:
- Use only evidence that is actually visible from the repository, README, issues, docs, commits, or explicitly supplied notes.
- Do not invent impact metrics, responsibilities, production scale, team size, or technologies.
- If evidence is partial, say "미확인" instead of guessing.
- Separate situation, role, judgment, actions, and results.
- Prefer concrete implementation details over polished summary language.
- Add jobKeywords and questionTypes based on evidence-backed inference only.
- Return Markdown only.

Output format:
${EXPERIENCE_MARKDOWN_TEMPLATE}`;

export const REINDEXING_NOTES = [
  "질문 문구나 검색 로직만 바꾸면 기존 파일을 다시 넣지 않아도 됩니다.",
  "경험을 임베딩하는 서술 구조나 인덱싱 텍스트를 바꾸면 기존 경험을 다시 재인덱싱해야 효과가 납니다.",
  "원문 Markdown/JSON 자체를 더 구조화해서 보강하면 재업로드보다 재편집 후 재분류가 더 좋습니다.",
];
