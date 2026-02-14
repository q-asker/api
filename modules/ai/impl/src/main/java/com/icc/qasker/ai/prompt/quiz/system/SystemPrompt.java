package com.icc.qasker.ai.prompt.quiz.system;

import com.icc.qasker.ai.prompt.quiz.common.QuizPromptStrategy;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class SystemPrompt {

    private static final String BASE_INSTRUCTION = """
        당신은 대학 강의노트로부터 평가용 퀴즈를 생성하는 AI입니다.
        
        [작성 규칙]
        - 모든 텍스트는 한국어로 작성한다.
        - 개행을 적절히 사용하여 가독성을 확보한다.
        - "강의노트에 따르면", "교재에 의하면" 같은 출처 언급을 하지 않는다.
        - 문제와 해설은 강의노트의 내용을 기반으로 하되, 독립적으로 이해 가능하게 작성한다.
        """;


    public static String generate(QuizPromptStrategy strategy, String jsonSchema) {
        return """
            %s
            ========================================
            SECTION 1: OUTPUT FORMAT
            ========================================
            %s
            
            ========================================
            SECTION 2: CONTENT GUIDELINES
            ========================================
            %s
            
            ========================================
            SECTION 3: JSON RESPONSE SCHEMA
            ========================================
            아래 JSON Schema를 정확히 준수하여 응답하세요.
            %s
            """.formatted(BASE_INSTRUCTION, strategy.getFormat(), strategy.getGuideLine(),
            jsonSchema);
    }
}
