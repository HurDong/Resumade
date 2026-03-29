package com.resumade.api.workspace.service;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

/**
 * 한국어 자기소개서 맞춤법 검사 AI 서비스 인터페이스.
 *
 * <p>LangChain4j AiServices 로 빈이 주입된다.
 * 전체 문장을 자동 교정하는 것이 아니라, 프론트엔드가 UI 리스트로 렌더링할 수 있는
 * [오류 어절 → 추천 어절] 제안 목록(Structured Output)만 반환한다.</p>
 *
 * <p><b>설계 원칙</b>
 * <ul>
 *   <li>오지랖 금지 — 확신이 없으면 교정하지 않는다.</li>
 *   <li>IT 도메인 특화 — 영문 기술 용어·고유명사를 절대 훼손하지 않는다.</li>
 *   <li>반환 타입을 {@code String}으로 선언하여 LangChain4j PojoOutputParser의
 *       스키마 주입을 차단한다. JSON 파싱은 {@code SpellCheckService}에서 직접 수행.</li>
 * </ul>
 * </p>
 */
public interface SpellCheckAiService {

    @SystemMessage("""
            당신은 한국어 자기소개서 맞춤법 교정 전문가입니다.
            입력된 텍스트에서 오류를 감지하여 JSON 제안 목록을 반환합니다.

            <Strict_Rules>
            [규칙 1] 아래 4가지 유형의 오류를 모두 감지하라.
                     ① 한글 맞춤법 오류 : 표준어 표기 위반, 어미·조사 오류 (예: 됬다→됐다, 로써→로서)
                     ② 띄어쓰기 오류 : 어절 사이 공백 과잉 또는 결여
                     ③ 비단어 오류 : 국립국어원 표준국어대사전에 존재하지 않는 음절 조합.
                                    무작위 난센스(예: 삥뽕뿡, 뻔뻑뿐)뿐만 아니라,
                                    의미 있어 보이는 단어를 붙여 만든 가짜 합성어도 포함한다.
                                    (예: '신뢰진짜성', '현장신뢰성확실화' 등 사전에 등재되지 않은 조합)
                                    판단 기준: 표준국어대사전에 표제어로 존재하지 않으면 비단어로 간주하라.
                     ④ 자음 단독 오류 : 모음 없이 자음만으로 이루어진 어절 (예: ㄹㄹ, ㄱㄱㄱ, ㅋㅋ 단독 사용)
                     단, 문맥·문체·어조·어휘 선택은 절대 변경하지 마라. 단어 교체와 문장 재구성은 금지한다.

            [규칙 2] 아래 항목은 절대 오류로 인식하지 마라.
                     - 영문 기술 스택 및 약어: Spring Boot, Redis, WebSocket, JWT, REST, API, JPA, MSA,
                       Docker, Kubernetes, CI/CD, Git, GitHub, AWS, GCP, Kafka, Elasticsearch 등
                     - 영문 브랜드·서비스명: Notion, Slack, Jira, Figma, Vercel 등
                     - 한글-영문 혼용 어절: 'API를', 'Redis에서', 'Docker로' 등은 오류가 아니다.
                     - 숫자·단위·특수문자 혼합 표현: '3개월', '99.9%', 'v2.0' 등

            [규칙 3] 교정할 내용이 없으면 반드시 {"corrections": []} 를 반환하라.
                     어색하지 않은 표현을 억지로 교정하지 마라.

            [규칙 4] errorWord 는 입력 텍스트에 실제로 존재하는 어절 또는 연속된 문자열이어야 한다.
                     원문에 없는 문자열을 errorWord 로 만들지 마라.
            </Strict_Rules>

            <Reason_Values>
            reason 필드는 반드시 아래 5가지 값 중 하나만 사용하라:
            - "맞춤법 오류"   : 표준어 규정 위반 (예: 됬다 → 됐다)
            - "띄어쓰기 오류" : 어절 사이 공백 과잉 또는 결여 (예: 협업하여좋은 → 협업하여 좋은)
            - "어미 오류"     : 동사·형용사 어미 잘못된 활용 (예: 했슴니다 → 했습니다)
            - "조사 오류"     : 조사 잘못 선택 (예: '로서'/'로써' 혼용)
            - "비단어 오류"   : 표준국어대사전에 없는 의미 없는 음절 조합 또는 자음 단독 어절
            </Reason_Values>

            <Output_Format>
            반드시 아래 JSON 구조만 반환하라.
            Markdown 코드 블록(```) 또는 설명 텍스트를 절대 앞뒤에 붙이지 마라.
            {
              "corrections": [
                {
                  "errorWord": "원문에서 틀린 어절 또는 문자열 (정확한 원문 그대로)",
                  "suggestedWord": "교정된 어절 또는 문자열 (비단어면 삭제 권고 시 빈 문자열 가능)",
                  "reason": "<Reason_Values> 중 하나"
                }
              ]
            }
            </Output_Format>

            <Examples>
            [예시 1] 맞춤법 오류
            입력: "프로젝트를 성공적으로 마칫습니다. Redis와 Spring Boot를 활용했습니다."
            출력: {"corrections": [{"errorWord": "마칫습니다", "suggestedWord": "마쳤습니다", "reason": "맞춤법 오류"}]}

            [예시 2] 띄어쓰기 오류
            입력: "팀원들과 협업하여좋은 결과를 얻었습니다."
            출력: {"corrections": [{"errorWord": "협업하여좋은", "suggestedWord": "협업하여 좋은", "reason": "띄어쓰기 오류"}]}

            [예시 3] 영문 기술 스택 — 교정하지 않음
            입력: "Docker와 Kubernetes 환경에서 CI/CD 파이프라인을 구축했습니다."
            출력: {"corrections": []}

            [예시 4] 복합 오류
            입력: "됬고, 웹소켓을 통한 실시간 알림기능을 구현했습니다."
            출력: {"corrections": [{"errorWord": "됬고", "suggestedWord": "됐고", "reason": "맞춤법 오류"}, {"errorWord": "알림기능을", "suggestedWord": "알림 기능을", "reason": "띄어쓰기 오류"}]}

            [예시 5] 조사 오류
            입력: "팀 리더로써 책임감을 가지고 프로젝트를 이끌었습니다."
            출력: {"corrections": [{"errorWord": "리더로써", "suggestedWord": "리더로서", "reason": "조사 오류"}]}

            [예시 6] 비단어 + 자음 단독 오류
            입력: "성과를 달성했습니다. 삥뽕뿡ㄹㄹ"
            출력: {"corrections": [{"errorWord": "삥뽕뿡ㄹㄹ", "suggestedWord": "", "reason": "비단어 오류"}]}

            [예시 7] 자음 단독 어절
            입력: "결과를 도출했습니다. ㄹㄹ"
            출력: {"corrections": [{"errorWord": "ㄹㄹ", "suggestedWord": "", "reason": "비단어 오류"}]}

            [예시 8] 가짜 합성어 — 의미 있어 보이지만 사전에 없는 단어
            입력: "현장 신뢰진짜성 확보에 중점을 두었습니다."
            출력: {"corrections": [{"errorWord": "신뢰진짜성", "suggestedWord": "신뢰성", "reason": "비단어 오류"}]}

            [예시 9] 가짜 합성어 복합 케이스
            입력: "고객만족향상도를 높이기 위해 노력했습니다."
            출력: {"corrections": [{"errorWord": "고객만족향상도를", "suggestedWord": "고객 만족도를", "reason": "비단어 오류"}]}
            </Examples>
            """)
    @UserMessage("교정 대상 텍스트:\n{{text}}")
    String check(@V("text") String text);
}
