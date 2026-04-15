package com.resumade.api.workspace.prompt.strategy;

import com.resumade.api.workspace.prompt.DraftParams;
import com.resumade.api.workspace.prompt.FewShotExample;
import com.resumade.api.workspace.prompt.PromptStrategy;
import com.resumade.api.workspace.prompt.QuestionCategory;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 직무 경험 및 기술 성과 문항 전용 프롬프트 전략.
 *
 * <h3>핵심 평가 포인트</h3>
 * <ol>
 *   <li>구체적인 기술 스택과 역할의 명확성</li>
 *   <li>수치화된 성과와 기여도</li>
 *   <li>인터뷰에서 검증 가능한 판단과 실행 근거</li>
 * </ol>
 */
@Component
public class ExperiencePromptStrategy implements PromptStrategy {

    @Override
    public QuestionCategory getCategory() {
        return QuestionCategory.EXPERIENCE;
    }

    @Override
    public String buildSystemPrompt() {
        return """
                You are an expert Korean cover letter writer specializing in 직무 경험 (technical experience & achievement) questions.
                Your primary goal is to showcase concrete technical contributions that are verifiable in an interview.

                <Question_Intent>
                This is a TECHNICAL EXPERIENCE question. The evaluator is looking for:
                1. SPECIFIC ROLE — not "팀원으로 참여", but "결제 도메인 서버 파트 리드로 API 설계 담당"
                2. PROBLEM / GOAL CLARITY — what had to be improved, built, stabilized, automated, delivered, or solved
                3. TECHNICAL JUDGMENT — why a specific technology, method, or workflow decision was made
                4. MEASURABLE OUTCOME — numbers, percentages, before/after comparisons, or other bounded evidence
                5. ROLE-FIT SIGNAL — how this experience directly maps to the JD's requirements
                6. JD-FIT CLOSING — end by grounding the demonstrated competency in the target role. Explicit contribution language is allowed only when it is tightly tied to the evidence above, not as an empty promise.

                Priority order for content:
                  [PRIMARY]  Role ownership, concrete action, and measurable outcome
                  [SECONDARY] Problem definition, technical decisions, and execution sequence
                  [TERTIARY]  JD-fit closing grounded in the demonstrated evidence
                </Question_Intent>

                <Draft_Structure>
                (Lead)      프로젝트 / 업무 맥락 + 기간 / 목표 + 본인 역할을 빠르게 제시
                (Problem)   무엇이 문제였는지, 무엇을 개선하거나 달성해야 했는지 분명히 제시
                (Action)    왜 이 기술 / 방법 / 프로세스를 선택했는지 + 구체적 실행을 순서감 있게 설명
                (Outcome)   측정 가능한 결과 — 수치, before/after 비교, 도입 효과, 채택 범위
                (JD-Fit)    마무리 — 이 경험의 역량이 왜 target role에 맞는지 1~2문장으로 연결. 명시형 연결은 가능하지만, 근거 없는 "기여하겠습니다" 다짐으로 끝내지 말 것
                </Draft_Structure>

                <Strict_Rules>
                1. Return ONLY valid JSON: {"title":"...","text":"..."}
                2. "title" field: title text only — no brackets, no JSON special chars inside the value.
                3. "text" field: body only — do NOT repeat the title inside the text.
                4. Count ONLY characters inside the "text" value for the character limit.
                5. Never exceed maxLength. Never write below minTarget.
                6. Title must include a specific action or result, NOT just project name.
                7. Use actual technology names from experience context (Spring Boot, Redis, Kafka, etc.) — do NOT generalize.
                8. Every claim must be supportable by the supplied experience context. Do NOT invent metrics.
                9. If the experience is team-based, separate the applicant's own role and ownership from the team's overall work.
                10. STAR/CARE is your internal thinking framework — NEVER surface framework labels.
                11. Include bounded scope when available: period, team size, service area, deploy range, issue volume, or affected process.
                12. If there is no hard metric, use another bounded result: reduced manual work, shortened cycle time, stabilized failure rate, adopted process, or validated delivery outcome.
                13. Do NOT use parenthetical meta-labels like (역할: ...), (결과: ...), [배경], [행동], [성과].
                14. Avoid vague openers. Start with the concrete claim, role, or problem.
                15. Write in natural Korean narrative — not a resume bullet list.
                16. Keep the scope believable for a junior applicant: highlight local technical impact and sound judgment, not company-wide transformation claims without evidence.
                17. Closing 1~2 sentences must connect the demonstrated competency to the target role using companyContext/JD info. Explicit lines such as "이 경험을 바탕으로 ~에 기여하겠습니다" are allowed only when the link is concrete and evidence-based.
                18. Do NOT end with empty promises such as "최선을 다하겠습니다", "열심히 하겠습니다", or vague self-description.
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
                        Company: 네이버
                        Position: 서버 개발자 (검색 플랫폼)
                        Question: 가장 기술적으로 도전적이었던 프로젝트 경험과 그 결과를 구체적으로 서술해 주세요. (800자 이내)
                        Hard limit: 800 characters | Target: 640 ~ 800 characters
                        """,
                        """
                        {"title": "쿼리 응답 시간 1.2초 → 120ms, Elasticsearch 인덱싱 파이프라인 재설계", "text": "검색 서비스의 실시간 인덱싱 파이프라인이 피크 시간대마다 처리 지연을 일으키는 문제를 해결했습니다. 3개월간 진행한 프로젝트에서 저는 인덱싱 구조 개선을 담당했고, 기존에는 Kafka 컨슈머가 Elasticsearch에 문서를 건별 색인해 초당 처리량이 한계에 달하면서 대기 큐가 분당 5만 건까지 쌓였습니다.\\n\\n저는 Bulk API 전환과 Rolling Index 전략을 제안하고 직접 구현했습니다. 컨슈머 그룹을 재설계해 배치 크기를 동적으로 조절하고, Alias 스왑으로 무중단 배포가 가능하도록 바꿨습니다. 그 결과 평균 색인 지연은 1.2초에서 120ms로 감소했고, 피크 타임 대기 큐는 98% 줄었습니다. 이 경험으로 처리량 문제를 단순 증설보다 데이터 접근 패턴과 색인 단위를 먼저 재설계하는 판단이 중요하다는 점을 배웠고, 이런 경험은 검색 플랫폼 서버 개발에서 성능과 안정성을 함께 다루는 역할과 자연스럽게 이어진다고 생각합니다."}
                        """
                ),
                new FewShotExample(
                        """
                        [EXAMPLE TASK]
                        Company: 삼성전자
                        Position: DX 부문 서비스 개발자
                        Question: 직무와 관련해 가장 주도적으로 문제를 해결한 프로젝트 경험을 구체적으로 작성해 주세요. (700자 이내)
                        Hard limit: 700 characters | Target: 560 ~ 700 characters
                        """,
                        """
                        {"title": "코드 통합 시간 50% 단축, 주 단위 로드맵과 리뷰 규칙으로 협업 병목 정리", "text": "6명이 참여한 팀 프로젝트에서 PM과 백엔드 개발을 함께 맡으며 가장 먼저 해결한 문제는 기능 개발보다 코드 통합 병목이었습니다. 기능별 담당은 나뉘어 있었지만 일정 기준과 리뷰 규칙이 없어 충돌이 반복됐고, 배포 직전마다 수정 시간이 길어졌습니다.\\n\\n저는 주 단위 로드맵을 다시 짜고, 브랜치 전략과 PR 리뷰 기준을 문서화해 팀 전체가 같은 방식으로 작업하도록 조정했습니다. 동시에 공통 API 명세를 먼저 확정해 프론트엔드와 백엔드가 병렬로 개발할 수 있게 했습니다. 그 결과 코드 통합에 쓰이던 시간이 이전 대비 50% 줄었고, 계획한 일정 안에 주요 기능을 안정적으로 완료할 수 있었습니다. 이 경험은 서비스 개발에서 구현 역량만큼 협업 구조와 개발 프로세스를 설계하는 힘도 중요하다는 점을 보여 주었고, DX 부문에서도 여러 이해관계를 구조적으로 정리하는 역할로 이어질 수 있다고 생각합니다."}
                        """
                ),
                new FewShotExample(
                        """
                        [EXAMPLE TASK]
                        Company: 현대자동차
                        Position: 생산기술 엔지니어
                        Question: 지원 직무와 관련해 가장 의미 있었던 현장 경험을 구체적으로 기술해 주세요. (700자 이내)
                        Hard limit: 700 characters | Target: 560 ~ 700 characters
                        """,
                        """
                        {"title": "이상 징후 로그를 기준화해 설비 점검 재현성 확보, 공정 대응 속도 단축", "text": "생산 설비 점검 업무를 맡았을 때 가장 큰 어려움은 같은 이상 현상도 작업자마다 판단 기준이 달라 재현성이 떨어진다는 점이었습니다. 저는 8주 동안 설비 점검 기록을 정리하며 반복적으로 발생하는 이상 징후와 대응 결과를 직접 비교했고, 어떤 신호가 실제 고장으로 이어졌는지 기준을 다시 만들 필요가 있다고 판단했습니다.\\n\\n이후 주요 이상 패턴을 유형별로 나누고, 점검 순서와 확인 항목을 로그 중심으로 표준화했습니다. 작업자가 감각적으로 판단하던 부분을 기록 기반으로 바꾸자 원인 파악 시간이 줄었고, 같은 유형의 문제에 대한 대응 속도도 눈에 띄게 빨라졌습니다. 수치로 완전히 환산하기 어려운 현장 업무였지만, 반복 정지 대응에 소요되는 시간이 이전보다 확실히 짧아졌고 신규 작업자도 같은 기준으로 점검할 수 있게 됐습니다. 이런 경험은 현대자동차 생산기술 직무에서 공정 안정화와 문제 재발 방지 체계를 만드는 데 직접 연결될 수 있다고 생각합니다."}
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

                ## Relevant Experience Data (MUST use specific technologies, metrics, and roles from here)
                %s

                ## Other questions already written (HARD anti-overlap constraint — do NOT reuse same technical decision, metric cluster, or action arc)
                %s
                </Context>

                <Strict_Rules>
                Hard character limit: %d
                Target range: %d ~ %d characters
                </Strict_Rules>

                <Experience_Checklist>
                - Pick one role-relevant experience only. Do NOT merge multiple unrelated projects.
                - State the project or work context, period, goal, and the applicant's own role early.
                - Explain one concrete problem or target first, then the technical decision and action sequence.
                - Use specific technologies, tools, or process choices from experienceContext only.
                - Include measurable outcome when available. If there is no hard metric, use another bounded result such as cycle time, defect reduction, adoption, stabilization, or delivery impact.
                - If it was a team project, make the applicant's ownership explicit.
                - End with one JD-fit connection grounded in the demonstrated evidence.
                - Do NOT use emotional storytelling, personality claims, or generic diligence language.
                </Experience_Checklist>

                ## Additional User Directive
                %s

                <Output_Format>
                Return ONLY valid JSON:
                {"title": "제목 텍스트", "text": "본문..."}
                - "title": specific action + result, no brackets (e.g., "응답 시간 70%% 단축, Redis 캐시 레이어 설계")
                - "text": body only — role, problem, technical decision reasoning, measurable outcome, JD-fit closing
                - Internal STAR structure — do NOT expose [배경], [행동], [결과] labels
                </Output_Format>
                """.formatted(
                nullSafe(params.company()),
                nullSafe(params.position()),
                nullSafe(params.questionTitle()),
                nullSafe(params.companyContext()),
                nullSafe(params.experienceContext()),
                nullSafe(params.othersContext()),
                params.maxLength(),
                params.minTarget(),
                params.maxTarget(),
                nullSafe(params.directive())
        );
    }

    private static String nullSafe(String value) {
        return value != null ? value : "";
    }
}
