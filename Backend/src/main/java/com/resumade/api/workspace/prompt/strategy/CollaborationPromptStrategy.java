package com.resumade.api.workspace.prompt.strategy;

import com.resumade.api.workspace.prompt.DraftParams;
import com.resumade.api.workspace.prompt.FewShotExample;
import com.resumade.api.workspace.prompt.PromptStrategy;
import com.resumade.api.workspace.prompt.QuestionCategory;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 협업 / 팀워크 / 리더십 문항 전용 프롬프트 전략.
 */
@Component
public class CollaborationPromptStrategy implements PromptStrategy {

    @Override
    public QuestionCategory getCategory() {
        return QuestionCategory.COLLABORATION;
    }

    @Override
    public String buildSystemPrompt() {
        return """
                You are an expert Korean cover letter writer specializing in 협업/팀워크/조율 questions.

                <Question_Intent>
                This is a COLLABORATION question. The evaluator checks:
                1. SHARED GOAL — what common objective the team was trying to achieve.
                2. ROLE CLARITY — what the applicant specifically owned inside that shared goal.
                3. COORDINATION / FRICTION HANDLING — how differing opinions, handoff issues, schedule pressure, or blockers were managed.
                4. OUTCOME ATTRIBUTION — what the team achieved and what the applicant specifically contributed.
                5. COMMUNICATION PROCESS — how alignment was actually built: meetings, documentation, shared tools, simulation, experiment, feedback loops.

                Priority order:
                  [PRIMARY]  Shared goal + applicant's specific role and concrete action
                  [SECONDARY] How coordination or disagreement was navigated
                  [TERTIARY] Team outcome and personal learning about collaboration
                </Question_Intent>

                <Draft_Structure>
                (Lead)      공동 목표와 팀 맥락 — 몇 명이 어떤 목표를 위해 일했고 내 역할은 무엇이었는지
                (Role)      내가 맡은 범위와 책임 — contributor/coordinator/lead 중 무엇이었는지 구체화
                (Process)   조율 과정 — 역할 분담, 정보 공유, 설득, 피드백, 문서화, 시뮬레이션, 실험 등 실제 협업 방식
                (Challenge) 갈등이나 coordination challenge — 의견 차이, 우선순위 충돌, 일정 압박, handoff 문제 등. 없으면 억지로 만들지 말고 실제 조율 난점을 쓰기
                (Result)    팀 성과와 내 기여를 분리해 제시 — 정량 결과, 프로세스 개선, 팀 반응, 품질 향상 등
                (Learning)  이 경험에서 배운 협업 원칙 — 지원 직무에서 어떻게 적용할 것인지 자연스럽게 연결
                </Draft_Structure>

                <Strict_Rules>
                1. Return ONLY valid JSON: {"title":"...","text":"..."}
                2. "title" field: title text only — no brackets, no JSON special chars inside the value.
                3. "text" field: body only — do NOT repeat the title inside the text.
                4. Count ONLY characters inside "text" value for the character limit.
                5. Never exceed maxLength. Never write below minTarget.
                6. Title must be specific about the shared goal, role, coordination method, or conflict context — NOT 협업 경험, 팀워크 역량, 소통의 중요성.
                7. Be honest about the team's challenges — overly positive "우리 팀은 항상 잘 맞았습니다" answers are not credible.
                8. Clearly distinguish "what the team achieved" from "what I specifically did".
                9. No parenthetical labels. No bullet lists unless requested.
                10. Write in natural Korean narrative voice.
                11. Keep the voice believable for a junior applicant: emphasize coordination, role clarity, documentation, stakeholder alignment, and feedback acceptance over inflated leadership claims.
                12. If there was no explicit interpersonal conflict, use a real coordination challenge instead of fabricating a dramatic conflict.
                13. CRITICAL — Solo project integrity: If the provided experience data indicates a solo/individual project (e.g., Role mentions '1인', '단독', '혼자', or there is no mention of team members, teammates, or collaborators), do NOT fabricate team collaboration or invent imaginary teammates. Instead, pivot to a REAL adjacent collaboration story: interactions with a client, mentor, external API provider, academic advisor, or cross-domain stakeholder. If no such adjacent story exists, acknowledge the solo nature honestly and describe the self-driven decision-making, self-review, and delivery process as a form of self-managed collaboration discipline.
                </Strict_Rules>

                <Output_Format>
                Return ONLY: {"title": "제목 텍스트", "text": "본문..."}
                </Output_Format>
                """;
    }

    @Override
    public List<FewShotExample> getFewShotExamples() {
        return List.of(
                new FewShotExample(
                        """
                        [EXAMPLE TASK]
                        Company: 라인
                        Position: 프론트엔드 개발자
                        Question: 팀 내 갈등을 해결하거나 협업을 이끌었던 경험을 구체적으로 서술해 주세요. (600자 이내)
                        Hard limit: 600 characters | Target: 480 ~ 600 characters
                        """,
                        """
                        {"title": "디자인-개발 간 스펙 충돌, RFC 문서로 합의 프로세스를 만들다", "text": "신규 컴포넌트 라이브러리 구축 프로젝트에서 디자이너와 개발팀 사이에 반복적인 스펙 충돌이 발생했습니다. 회의 중에는 합의가 됐다가 구현 단계에서 인식이 달랐던 것입니다. 저는 이 문제의 원인이 '구두 합의'에 있다고 판단하고, Notion 기반의 Component RFC 템플릿을 제안했습니다.\\n\\n각 컴포넌트의 Props API, 상태 전이, 접근성 요건을 문서화하고 디자이너-개발자 공동 리뷰를 의무화했습니다. 처음엔 문서 작성 오버헤드에 대한 저항이 있었지만, 3스프린트 후 QA 피드백 건수가 절반으로 줄면서 팀 전체가 프로세스를 자발적으로 유지하게 됐습니다."}
                        """
                ),
                new FewShotExample(
                        """
                        [EXAMPLE TASK]
                        Company: 현대오토에버
                        Position: 서비스 운영
                        Question: 공동 목표를 위해 협업했던 경험과 본인의 역할을 설명해 주세요. (650자 이내)
                        Hard limit: 650 characters | Target: 520 ~ 650 characters
                        """,
                        """
                        {"title": "1000명 규모 행사 운영, 공유 문서로 역할 공백을 줄이다", "text": "교내 컨퍼런스 운영진으로 참여했을 때 가장 중요한 공동 목표는 1000명 규모 행사를 차질 없이 진행하는 것이었습니다. 저는 실무 담당자로서 참가자 응대와 현장 운영 동선을 정리하는 역할을 맡았습니다. 준비 초반에는 홍보, 등록, 현장 대응 팀이 각자 따로 움직이면서 문의 대응 기준과 일정 공유가 자주 어긋났습니다. 저는 이 문제를 줄이기 위해 구글 스프레드시트와 Notion으로 역할별 체크리스트와 문의 대응 기준을 한 문서로 통합했고, 매일 짧은 점검 회의에서 변경 사항을 즉시 반영했습니다. 그 결과 현장에서는 문의 응답이 빨라졌고, 팀원들도 서로의 진행 상황을 쉽게 확인할 수 있었습니다. 이 경험을 통해 협업에서 중요한 것은 열심히 하는 태도보다 정보 기준을 맞추는 구조라는 점을 배웠고, 현대오토에버에서도 여러 이해관계자와 기준을 맞추며 안정적인 운영에 기여하겠습니다."}
                        """
                ),
                new FewShotExample(
                        """
                        [EXAMPLE TASK]
                        Company: 삼성디스플레이
                        Position: 공정기술
                        Question: 팀 내 의견 차이를 조율해 성과를 낸 경험을 작성해 주세요. (700자 이내)
                        Hard limit: 700 characters | Target: 560 ~ 700 characters
                        """,
                        """
                        {"title": "실험 방향 이견, 추가 검증으로 합의의 기준을 만들다", "text": "디스플레이 실험 프로젝트에서 팀의 목표는 휘도를 개선하는 공정 조건을 찾는 것이었습니다. 저는 데이터 정리와 실험 결과 해석을 담당했습니다. 진행 중 팀원들 사이에서 변수 A를 먼저 조정할지, 변수 B를 먼저 조정할지를 두고 의견이 갈렸고, 각자 이전 경험만을 근거로 주장하면서 논의가 길어졌습니다. 저는 어느 한쪽 의견을 밀기보다, 추가 실험 조건을 최소 단위로 나눠 비교해 보자는 방식을 제안했습니다. 실험 항목과 결과를 공동 문서에 정리하고, 항목별 해석 기준도 함께 적어 모두가 같은 데이터를 보며 판단하도록 했습니다. 그 결과 실험 방향이 빠르게 정리됐고, 최종적으로 목표 휘도를 기존 대비 크게 높일 수 있었습니다. 이 경험을 통해 협업에서 설득은 목소리의 크기가 아니라 판단 기준을 함께 만드는 과정이라는 점을 배웠습니다. 입사 후에도 공정기술 직무에서 부서 간 이견을 데이터와 검증 기준으로 정리해 팀 성과에 기여하겠습니다."}
                        """
                )
        );
    }

    @Override
    public String buildUserMessage(DraftParams params) {
        return """
                Company: %s
                Position: %s
                Question: %s

                <Context>
                ## Company & JD Analysis
                %s

                ## Relevant Experience Data (find team/collaboration stories here)
                %s

                ## Other questions already written (avoid reusing same team project or same conflict story)
                %s
                </Context>

                <Collaboration_Checklist>
                - 공동 목표는 무엇이었는가?
                - 팀 규모와 내 역할은 무엇이었는가?
                - 내가 실제로 한 조율, 설득, 문서화, 피드백, 실험 제안은 무엇이었는가?
                - 갈등이나 coordination challenge가 있었다면 원인과 해결 방식은 무엇이었는가?
                - 팀 성과와 내 기여는 어떻게 구분되는가?
                - 이 경험으로 배운 협업 원칙을 직무에 어떻게 연결할 것인가?
                </Collaboration_Checklist>

                <Strict_Rules>
                Hard character limit: %d
                Target range: %d ~ %d characters
                - 협업을 추상적으로 칭찬하지 말고, 구체적 상황과 조율 방식으로 증명할 것
                - 갈등이 없었다면 억지로 만들지 말고 실제 역할 충돌, 일정 압박, 의사결정 지연 같은 coordination challenge를 쓸 것
                - 협업 도구나 방식이 있다면 (공유 문서, 회의, 시뮬레이션, 실험, 피드백 루프 등) 구체적으로 드러낼 것
                </Strict_Rules>

                ## Additional User Directive
                %s

                <Output_Format>
                Return ONLY valid JSON:
                {"title": "제목 텍스트", "text": "본문..."}
                - "title": names the specific collaboration context, no brackets
                - "text": body — clearly distinguish "I did" from "the team did", show friction honestly
                </Output_Format>
                """.formatted(
                nullSafe(params.company()), nullSafe(params.position()),
                nullSafe(params.questionTitle()), nullSafe(params.companyContext()),
                nullSafe(params.experienceContext()), nullSafe(params.othersContext()),
                params.maxLength(), params.minTarget(), params.maxTarget(),
                nullSafe(params.directive())
        );
    }

    private static String nullSafe(String v) { return v != null ? v : ""; }
}
