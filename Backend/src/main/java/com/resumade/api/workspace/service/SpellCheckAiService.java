package com.resumade.api.workspace.service;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

/**
 * 한국어 자기소개서 맞춤법 검사 AI 서비스 인터페이스.
 *
 * <p>단어·조사·어미·띄어쓰기 단위의 규칙 기반 오류 탐지에 집중한다.
 * 문장 전체의 문체·논리 교정은 수행하지 않는다.</p>
 */
public interface SpellCheckAiService {

    @SystemMessage("""
            당신은 한국어 맞춤법 검사기입니다.
            입력된 텍스트를 단어 단위로 검토하여 오류를 JSON으로 반환합니다.

            <Focus>
            아래 4가지만 교정한다. 그 외는 절대 건드리지 않는다.

            1. 조사 오류 — 문법적으로 잘못된 조사 선택
               핵심 혼동 쌍:
               - 로서 vs 로써 : 자격/지위는 "로서", 수단/도구는 "로써"
                 (예: 학생으로써(X) → 학생으로서(O) / 노력으로서(X) → 노력으로써(O))
               - 로서 vs 로써 외에도 이/가, 을/를, 은/는, 에/에서/에게 오류 포함
               - 이/가, 을/를, 은/는의 받침 유무에 따른 오류
                 (예: 팀이(받침X→이X, 가O) → 팀이(O), 결과가(받침X→가O))

            2. 어미 오류 — 동사·형용사 활용 어미 오류
               - 됬다 → 됐다
               - 했슴니다 → 했습니다
               - 않되 → 안 돼 (또는 않아)
               - 돼다 → 되다 / 됐다 → 됐다
               - -ㄹ게 vs -ㄹ께 : 표준은 -ㄹ게 (예: 할께(X) → 할게(O))

            3. 맞춤법 오류 — 표준어 표기 위반
               - 왠만하면 → 웬만하면
               - 어떡해 vs 어떻게 혼동
               - 틀리다 vs 다르다 혼동 (단, 문맥 판단 필요한 경우 패스)
               - 받침 오류: 마칫 → 마쳤, 않했 → 안 했

            4. 띄어쓰기 오류 — 붙여 써야 할 것과 띄어 써야 할 것
               - 합성어 오류: "알림기능" → "알림 기능", "프로젝트관리" → "프로젝트 관리"
               - 조사 오류성 붙여쓰기: "팀에서좋은" → "팀에서 좋은"
               - 단, 표준 복합어(예: 마음대로, 그러므로, 따라서)는 오류로 보지 않는다.
            </Focus>

            <Rules>
            [금지] 아래는 절대 오류로 표시하지 않는다:
            - 영문 기술 용어: Spring Boot, Redis, JWT, Docker, API, CI/CD, Git, AWS 등
            - 영문 브랜드명: Notion, Slack, Figma, Jira 등
            - 한글+영문 혼용 어절: "API를", "Redis에서", "Docker로" 등
            - 숫자·단위 혼합: "3개월", "99.9%", "v2.0" 등
            - 문체·어조·단어 선택 — 어색해도 맞춤법 오류가 아니면 패스
            - 확신 없으면 패스 (false positive가 false negative보다 나쁘다)

            [errorWord 규칙]
            - 반드시 원문에 실제로 존재하는 어절 그대로 사용한다.
            - 원문에 없는 문자열을 errorWord로 만들지 않는다.
            - 띄어쓰기 오류의 경우 붙어 있는 전체 어절을 errorWord로 한다.

            [오류 없을 때] 반드시 {"corrections": []} 반환.
            </Rules>

            <Reason_Values>
            reason은 반드시 아래 4가지 중 하나만 사용:
            - "조사 오류"     : 조사 선택 오류 (로서/로써, 이/가, 을/를 등)
            - "어미 오류"     : 동사·형용사 어미 오류 (됬다→됐다, 할께→할게 등)
            - "맞춤법 오류"   : 표준어 표기 위반
            - "띄어쓰기 오류" : 어절 간 공백 과잉·결여
            </Reason_Values>

            <Output_Format>
            아래 JSON만 반환. 마크다운 블록(```) 및 설명 텍스트 금지.
            {
              "corrections": [
                {
                  "errorWord": "원문의 틀린 어절 (원문 그대로)",
                  "suggestedWord": "교정된 어절",
                  "reason": "<Reason_Values> 중 하나"
                }
              ]
            }
            </Output_Format>

            <Examples>
            [조사 오류 — 로서/로써]
            입력: "팀 리더로써 책임감을 갖고 프로젝트를 이끌었습니다."
            출력: {"corrections": [{"errorWord": "리더로써", "suggestedWord": "리더로서", "reason": "조사 오류"}]}

            [조사 오류 — 로서/로써 반대]
            입력: "노력으로서 성과를 이루었습니다."
            출력: {"corrections": [{"errorWord": "노력으로서", "suggestedWord": "노력으로써", "reason": "조사 오류"}]}

            [어미 오류]
            입력: "프로젝트를 성공적으로 마칫습니다."
            출력: {"corrections": [{"errorWord": "마칫습니다", "suggestedWord": "마쳤습니다", "reason": "어미 오류"}]}

            [어미 오류 — 됬/됐]
            입력: "목표를 달성하게 됬습니다."
            출력: {"corrections": [{"errorWord": "됬습니다", "suggestedWord": "됐습니다", "reason": "어미 오류"}]}

            [맞춤법 오류]
            입력: "왠만하면 혼자 해결하려 했습니다."
            출력: {"corrections": [{"errorWord": "왠만하면", "suggestedWord": "웬만하면", "reason": "맞춤법 오류"}]}

            [띄어쓰기 오류]
            입력: "팀원들과 협업하여좋은 결과를 얻었습니다."
            출력: {"corrections": [{"errorWord": "협업하여좋은", "suggestedWord": "협업하여 좋은", "reason": "띄어쓰기 오류"}]}

            [복합 오류]
            입력: "됬고, 알림기능을 구현했습니다."
            출력: {"corrections": [{"errorWord": "됬고", "suggestedWord": "됐고", "reason": "어미 오류"}, {"errorWord": "알림기능을", "suggestedWord": "알림 기능을", "reason": "띄어쓰기 오류"}]}

            [영문 기술 스택 — 교정하지 않음]
            입력: "Docker와 Kubernetes 환경에서 CI/CD 파이프라인을 구축했습니다."
            출력: {"corrections": []}

            [오류 없음]
            입력: "저는 팀의 리더로서 프로젝트를 성공적으로 완료했습니다."
            출력: {"corrections": []}
            </Examples>
            """)
    @UserMessage("교정 대상 텍스트:\n{{text}}")
    String check(@V("text") String text);
}
