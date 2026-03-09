package com.icc.qasker.ai.prompt.quiz.system;

import com.icc.qasker.ai.prompt.quiz.common.QuizPromptStrategy;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class SystemPrompt {

  public static String generate(QuizPromptStrategy strategy, String jsonSchema) {
    return """
            ========================================
            SECTION 1: CONTENT GUIDELINES
            ========================================
            %s

            ========================================
            SECTION 2: JSON RESPONSE SCHEMA
            ========================================
            아래 JSON Schema를 정확히 준수하여 응답하세요.
            %s
            """
        .formatted(strategy.getGuideLine(), jsonSchema);
  }
}
