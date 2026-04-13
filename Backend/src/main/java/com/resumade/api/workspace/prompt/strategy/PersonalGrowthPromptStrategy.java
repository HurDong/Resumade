package com.resumade.api.workspace.prompt.strategy;

import com.resumade.api.workspace.prompt.DraftParams;
import com.resumade.api.workspace.prompt.FewShotExample;
import com.resumade.api.workspace.prompt.PromptStrategy;
import com.resumade.api.workspace.prompt.QuestionCategory;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 성장과정 / 가치관 형성 / 인생 서사 문항 전용 프롬프트 전략.
 *
 * <p>사용자 피드백 지침 반영:
 * - 연대기/가족사 배제, 대학 이후 경험 우선
 * - 현재의 '일하는 기준' 정의 -> 형성 배경 -> 구체적 행동 증거 -> 직무 연결 구조
 */
@Component
public class PersonalGrowthPromptStrategy implements PromptStrategy {

    @Override
    public QuestionCategory getCategory() {
        return QuestionCategory.PERSONAL_GROWTH;
    }

    @Override
    public String buildSystemPrompt() {
        return """
                당신은 한국 채용 자기소개서 중 "성장과정" 문항만 전문적으로 작성하는 엔진입니다.
                이 문항을 절대로 인생 연대기, 가족사 소개, 미담 모음으로 쓰지 말고,
                지원자의 현재 일하는 방식, 직업관, 문제 해결 태도, 직무 선택 계기가 어떻게 형성되었는지를 보여주는 문항으로 해석하십시오.

                <Core_Principles>
                1. 성장과정 = 현재의 가치관/일하는 기준 + 그것이 형성된 배경 + 최근 행동 증거 + 직무 연결입니다.
                2. 전체 생애를 나열하지 말고, 결정적 경험 1~2개만 선택하십시오.
                3. 기본적으로 대학 입학 이후 경험을 우선 사용하십시오.
                4. 초중고/가족사/어린 시절 경험은 원칙적으로 배제하십시오. (단, 직무 방향 결정의 전환점일 때만 1~2문장으로 제한적 허용)
                5. 추상적 성격 묘사(성실함 등) 대신 반드시 실제 행동, 판단 과정, 결과, 배음을 서술하십시오.
                6. 직무와 무관하거나 감성적인 서사는 제거하십시오.
                7. 지원자 정보를 과장하거나 없는 경험을 만들어내지 마십시오.
                8. 문체는 담백하고 단정하게 유지하되, 상투적인 표현(화목한 가정 등)을 엄격히 금지합니다.
                9. 회사/직무 적합성을 암시해야 하지만, 성장과정 문항 자체를 지원동기로 바꾸지는 마십시오.
                10. 문장마다 정보 밀도를 높이고, 같은 뜻의 문장을 반복하지 마십시오.
                </Core_Principles>

                <Draft_Structure>
                - (Lead) 현재의 핵심 가치관 또는 일하는 기준 제시 (예: "저는 ~한 기준을 가지고 성장해왔습니다")
                - (Background) 그 기준이 형성된 배경 또는 결정적 전환점 서술
                - (Evidence) 대학 이후의 구체적 경험(프로젝트, 인턴 등)으로 해당 가치관이 발현된 행동과 결과를 증명
                - (Connect) 이 기준이 지원 직무에서 어떤 강점이 되는지 연결하며 마무리
                </Draft_Structure>

                <Strict_Output_Rules>
                1. Return ONLY valid JSON: {"title":"...","text":"..."}
                2. "title": 핵심 가치관이나 일하는 기준이 드러나는 제목 (성장과정/가치관 같은 단어 금지)
                3. "text": 위 구조를 따르는 본문 (소제목 없이 텍스트만)
                4. 글자 수 제한(maxLength)을 엄수하며 95~100% 범위를 목표로 합니다.
                5. 입력 정보가 부족하여 지침을 따를 수 없는 경우, 꾸며내지 말고 부족한 정보가 무엇인지 "성장과정 생성에 필요한 추가 정보: ..." 형식으로 한 줄만 반환하십시오.
                </Strict_Output_Rules>
                """;
    }

    @Override
    public List<FewShotExample> getFewShotExamples() {
        return List.of(
                new FewShotExample(
                        """
                        [TASK]
                        Company: 토스 (비바리퍼블리카)
                        Position: 프론트엔드 개발자
                        Question: 성장과정 및 본인의 가치관을 기술해 주세요. (500자)
                        Hard limit: 500 characters
                        Context: 대학 시절 오픈소스 기여 경험, '사용자 편의성'에 대한 집착, 처음엔 기능 구현만 하다가 접근성 이슈를 겪으며 가치관 변화
                        """,
                        """
                        {"title": "사용자의 '불편'을 해결의 시작점으로 삼는 기준", "text": "저는 기술의 완성도가 아닌 사용자의 실제 편의를 기여의 척도로 삼으며 성장해왔습니다. 대학 3학년 시절 처음 오픈소스 프로젝트에 참여하며 화려한 기능을 제안했지만, 실제 사용자들로부터 '어디에 기능이 있는지 모르겠다'는 피드백을 받고 큰 충격을 받았습니다. 개발자의 만족보다 사용자의 도달 가능성이 우선되어야 함을 깨달은 결정적 순간이었습니다.\\n\\n이후 모든 프로젝트에서 '당연한 인터뷰'를 루틴으로 만들었습니다. 지난 프론트엔드 인턴십 당시에도 결제 페이지의 이탈률을 줄이기 위해 실제 유저 10명의 마우스 동선을 추적하며 레이아웃을 전면 개편했습니다. 그 결과 로딩 시각화를 0.5초 개선하는 것보다 버튼 위치를 20px 옮기는 것이 이탈률 15% 감소라는 실질적 결과로 이어짐을 증명했습니다. 토스에서도 이러한 '사용자 중심의 집요함'을 바탕으로 가시적인 임팩트를 만드는 개발자가 되겠습니다."}
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

                <Instructions>
                현재의 일하는 기준(가치관) 1개를 먼저 설정하고, 그 배경과 대학 이후의 실질적 증거 경험을 연결하십시오.
                초중고 경험이나 가족사는 배제하거나 최소화하십시오.
                
                글자 수 제한: 최대 %d자 (목표: %d~%d자)
                </Instructions>

                <Provided_Context>
                ## 지원자 인생 서사 소재 (가장 적합한 서사 재료를 선별하십시오)
                %s

                ## 작성 가이드라인 (WRITING_GUIDE — 아래 지침을 본문 작성 전략에 반드시 반영하십시오)
                %s

                ## 회사 및 공고 분석 (연결 지점으로 활용)
                %s

                ## 다른 문항 내용 (소재 중복 회피용)
                %s
                </Provided_Context>

                ## 추가 요청 사항
                %s

                Return JSON format: {"title": "...", "text": "..."}
                """.formatted(
                nullSafe(params.company()), nullSafe(params.position()),
                nullSafe(params.questionTitle()), params.maxLength(),
                params.minTarget(), params.maxTarget(),
                nullSafe(params.experienceContext()),
                nullSafe(params.writingGuideContext()),
                nullSafe(params.companyContext()),
                nullSafe(params.othersContext()), nullSafe(params.directive())
        );
    }

    private static String nullSafe(String v) { return v != null ? v : ""; }
}
