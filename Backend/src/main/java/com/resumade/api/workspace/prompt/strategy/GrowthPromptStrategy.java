package com.resumade.api.workspace.prompt.strategy;

import com.resumade.api.workspace.prompt.DraftParams;
import com.resumade.api.workspace.prompt.FewShotExample;
import com.resumade.api.workspace.prompt.PromptStrategy;
import com.resumade.api.workspace.prompt.QuestionCategory;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 성장 경험 / 자기 개발 / 배움 문항 전용 프롬프트 전략.
 */
@Component
public class GrowthPromptStrategy implements PromptStrategy {

    @Override
    public QuestionCategory getCategory() {
        return QuestionCategory.GROWTH;
    }

    @Override
    public String buildSystemPrompt() {
        return """
                You are an expert Korean cover letter writer specializing in 성장/자기개발 (growth & learning) questions.

                <Question_Intent>
                This is a GROWTH question. The evaluator looks for:
                1. CONCRETE TRIGGER — what specifically prompted the growth effort? (not just "배우고 싶어서")
                2. INTENTIONAL PROCESS — what deliberate steps were taken? (books, projects, mentors, etc.)
                3. MEASURABLE CHANGE — what was different before and after? (skill, output quality, speed)
                4. APPLICATION — how was the learning applied in real work context?

                Priority order:
                  [PRIMARY]  Specific before/after skill or mindset change with evidence
                  [SECONDARY] The concrete learning method and why it was chosen
                  [TERTIARY]  Connection to how this growth is relevant for the target role
                </Question_Intent>

                <Strict_Rules>
                1. Return ONLY valid JSON: {"text":"..."}
                2. Count ONLY characters inside "text" value.
                3. Never exceed maxLength. Never write below minTarget.
                4. [제목]: must name the specific skill or change, NOT [성장 경험] or [자기 개발].
                5. Avoid vague openers like "저는 항상 배우는 것을 좋아합니다".
                6. Show the cost of growth — what was sacrificed, what was hard.
                7. Do NOT invent learning resources or projects not in experience context.
                8. Natural Korean narrative, no labels, no bullet lists unless requested.
                </Strict_Rules>

                <Output_Format>
                Return ONLY: {"text": "[제목]\\n\\n본문..."}
                </Output_Format>
                """;
    }

    @Override
    public List<FewShotExample> getFewShotExamples() {
        return List.of(
                new FewShotExample(
                        """
                        [EXAMPLE TASK]
                        Company: 당근마켓
                        Position: 백엔드 개발자
                        Question: 스스로의 한계를 극복하며 성장한 경험을 서술해 주세요. (600자 이내)
                        Hard limit: 600 characters | Target: 480 ~ 600 characters
                        """,
                        """
                        {"text": "[Java 동시성 이론 공부 6개월, 실제 Race Condition을 코드로 재현하며 이해]\\n\\n입사 첫 해에 멀티스레드 환경에서 가끔 발생하는 데이터 불일치 버그를 감으로 수정하는 제 자신이 부끄러웠습니다. 책에서 읽은 synchronized, volatile 키워드를 설명할 수는 있었지만, 정확히 언제 필요한지 확신이 없었습니다.\\n\\n주말마다 'Java Concurrency in Practice'를 읽으며 각 챕터의 예제를 직접 재현하는 사이드 프로젝트를 6개월간 진행했습니다. 이 과정에서 ABA Problem이 실제 코드에서 어떻게 발생하는지 재현에 성공했고, 이후 회사 코드베이스에서 유사 패턴을 발견해 CAS 기반으로 리팩토링해 버그를 선제 방지했습니다. 지금도 신규 코드 리뷰 시 동시성 관련 코멘트를 가장 먼저 확인합니다."}
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

                ## Relevant Experience Data (find learning journey or skill acquisition stories here)
                %s

                ## Other questions already written (avoid reusing same learning experience already described)
                %s
                </Context>

                <Strict_Rules>
                Hard character limit: %d
                Target range: %d ~ %d characters
                </Strict_Rules>

                ## Additional User Directive
                %s

                <Output_Format>
                Return ONLY valid JSON:
                {"text": "[제목]\\n\\n본문..."}
                - [제목]: names the specific skill or capability that grew
                - Show concrete trigger → process → measurable change → application
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
