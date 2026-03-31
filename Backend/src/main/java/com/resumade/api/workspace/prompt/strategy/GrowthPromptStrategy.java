package com.resumade.api.workspace.prompt.strategy;

import com.resumade.api.workspace.prompt.DraftParams;
import com.resumade.api.workspace.prompt.FewShotExample;
import com.resumade.api.workspace.prompt.PromptStrategy;
import com.resumade.api.workspace.prompt.QuestionCategory;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 기술적 성장 / CS 기본기 / 딥다이브형 학습 문항 전용 프롬프트 전략.
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
                You are an expert Korean cover letter writer specializing in CS/deep-dive growth questions for junior developers.

                <Question_Intent>
                This is a GROWTH question. The evaluator looks for:
                1. TECHNICAL TRIGGER — what concrete limitation, bug, or knowledge gap triggered the learning? (not just "배우고 싶어서")
                2. DEEP-DIVE PROCESS — how did the applicant go beyond surface-level tutorials? (official docs, reproducing issues, experiments, mentor feedback, CS concepts)
                3. APPLIED CHANGE — what code, decision quality, debugging ability, or delivery speed changed afterward?
                4. NEW-GRAD SIGNAL — does the story show learning agility, humility, and solid fundamentals rather than inflated senior ownership?

                Priority order:
                  [PRIMARY]  Specific technical concept and the before/after difference with evidence
                  [SECONDARY] The deep-dive process and why that method was chosen
                  [TERTIARY]  How the learning now shapes the applicant's engineering approach for the target role
                </Question_Intent>

                <Strict_Rules>
                1. Return ONLY valid JSON: {"text":"..."}
                2. Count ONLY characters inside "text" value.
                3. Never exceed maxLength. Never write below minTarget.
                4. [제목]: must name the specific technical capability or concept, NOT [성장 경험] or [자기 개발].
                5. Avoid vague openers like "저는 항상 배우는 것을 좋아합니다".
                6. Do NOT turn this into a generic certificate/course list. The learning must be tied to a concrete technical episode.
                7. Show the cost of growth — what was difficult, confusing, or slow at first.
                8. Do NOT invent learning resources, projects, or outcomes not in experience context.
                9. Keep the tone believable for a junior applicant: emphasize learning curve, fundamentals, and applied growth over grand strategic ownership.
                10. Natural Korean narrative, no labels, no bullet lists unless requested.
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
                        Company: 라인플러스
                        Position: 백엔드 개발자
                        Question: 부족했던 기술 역량을 깊이 있게 학습해 실제 프로젝트에 적용하며 성장한 경험을 서술해 주세요. (600자 이내)
                        Hard limit: 600 characters | Target: 480 ~ 600 characters
                        """,
                        """
                        {"text": "[Docker 격리 개념을 끝까지 파고들어 배포 병목을 줄인 경험]\\n\\n팀 프로젝트 초기에는 Docker를 단순한 배포 도구 정도로만 이해해, 컨테이너와 이미지 개념을 혼동한 채 환경을 맞추느라 이틀 가까이 시간을 허비했습니다. 원인을 제대로 이해하지 못한 채 명령어만 따라 치는 방식으로는 반복된다고 판단했습니다.\\n\\n이후 공식 문서를 기준으로 네트워크, 볼륨, 레이어 구조를 다시 정리하고, 작은 토이 프로젝트에서 직접 컨테이너를 분리하며 동작을 검증했습니다. 그 내용을 팀 프로젝트에 다시 적용해 프론트엔드와 백엔드 실행 환경을 분리했고, 배포 스크립트도 정리했습니다. 그 결과 환경 불일치로 생기던 오류가 크게 줄었고, 이후에는 새 기능 배포 준비 시간을 절반 수준으로 줄일 수 있었습니다. 이 경험을 통해 기술은 사용법보다 원리를 이해해야 제대로 다룰 수 있다는 기준을 갖게 됐습니다."}
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

                ## Relevant Experience Data (find technical learning journey, CS deep-dive, or feedback-driven growth stories here)
                %s

                ## Other questions already written (avoid reusing the same learning arc or the same technical growth story already described)
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
                - [제목]: names the specific technical capability or concept that deepened
                - Show concrete trigger → deep-dive process → applied change → current engineering standard
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
