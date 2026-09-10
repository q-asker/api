package com.icc.qasker.ai.service;

import com.icc.qasker.ai.dto.AIProblem;
import com.icc.qasker.ai.mapper.GeminiQuestionMapper;
import com.icc.qasker.ai.structure.GeminiQuestion;
import com.icc.qasker.ai.structure.GeminiResponse;
import com.icc.qasker.ai.structure.GeminiResponseSchema;
import java.util.List;
import java.util.Optional;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.springframework.ai.converter.BeanOutputConverter;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class SelectionQuizSupport {

  public static Class<GeminiQuestion> elementType() {
    return GeminiQuestion.class;
  }

  public static String responseSchema(String customInstruction) {
    return GeminiResponseSchema.forInstruction(customInstruction);
  }

  public static boolean accept(GeminiQuestion question, int maxSelectionCount) {
    return question.selections() == null || question.selections().size() <= maxSelectionCount;
  }

  public static AIProblem toProblem(GeminiQuestion question, List<Integer> sourcePages) {
    return GeminiQuestionMapper.toDto(List.of(question), sourcePages).quiz().getFirst();
  }

  public static Optional<GeminiQuestion> parseFirst(String text) {
    GeminiResponse parsed = new BeanOutputConverter<>(GeminiResponse.class).convert(text);
    if (parsed.questions() == null || parsed.questions().isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(parsed.questions().getFirst());
  }
}
