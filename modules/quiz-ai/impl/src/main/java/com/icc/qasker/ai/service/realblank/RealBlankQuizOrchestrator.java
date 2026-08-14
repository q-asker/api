package com.icc.qasker.ai.service.realblank;

import com.icc.qasker.ai.GeminiFileService;
import com.icc.qasker.ai.dto.AIProblem;
import com.icc.qasker.ai.mapper.GeminiRealBlankQuestionMapper;
import com.icc.qasker.ai.properties.QAskerAiProperties;
import com.icc.qasker.ai.service.AbstractChunkedQuizOrchestrator;
import com.icc.qasker.ai.service.quality.QualityGate;
import com.icc.qasker.ai.service.realblank.prompt.RealBlankRequestPrompt;
import com.icc.qasker.ai.service.support.GeminiMetricsRecorder;
import com.icc.qasker.ai.strategy.QuizType;
import com.icc.qasker.ai.structure.GeminiRealBlankQuestion;
import com.icc.qasker.ai.structure.GeminiRealBlankResponse;
import com.icc.qasker.ai.structure.GeminiRealBlankResponseSchema;
import java.util.List;
import java.util.Optional;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * REAL_BLANK(직접 입력 단답) 퀴즈 오케스트레이터. ESSAY와 동일하게 청크형 골격 {@link AbstractChunkedQuizOrchestrator}를 전용
 * 파싱 구조 {@code GeminiRealBlankQuestion}으로 고정한다. 오답 선택지를 만들지 않고(FR-008), 정답(answer)을 단일 선지로, 인정
 * 범위(acceptedAnswers)를 그 선지에 실어 보존한다.
 */
@Component
public class RealBlankQuizOrchestrator
    extends AbstractChunkedQuizOrchestrator<GeminiRealBlankQuestion> {

  public RealBlankQuizOrchestrator(
      GeminiFileService geminiFileService,
      ChatModel chatModel,
      ObjectMapper objectMapper,
      GeminiMetricsRecorder metricsRecorder,
      QAskerAiProperties aiProperties,
      QualityGate qualityGate) {
    super(geminiFileService, chatModel, objectMapper, metricsRecorder, aiProperties, qualityGate);
  }

  @Override
  public QuizType getSupportedType() {
    return QuizType.REAL_BLANK;
  }

  @Override
  protected Class<GeminiRealBlankQuestion> elementType() {
    return GeminiRealBlankQuestion.class;
  }

  @Override
  protected String responseSchema(String customInstruction) {
    return GeminiRealBlankResponseSchema.forInstruction(customInstruction);
  }

  @Override
  protected boolean accept(GeminiRealBlankQuestion question) {
    // 직접 입력 단답은 오답 선지 개념이 없으므로 drop 규칙 없음.
    return true;
  }

  @Override
  protected AIProblem toProblem(GeminiRealBlankQuestion question, List<Integer> sourcePages) {
    return GeminiRealBlankQuestionMapper.toDto(List.of(question), sourcePages).quiz().getFirst();
  }

  @Override
  protected Optional<GeminiRealBlankQuestion> parseFirst(String text) {
    GeminiRealBlankResponse parsed =
        new BeanOutputConverter<>(GeminiRealBlankResponse.class).convert(text);
    if (parsed == null || parsed.questions() == null || parsed.questions().isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(parsed.questions().getFirst());
  }

  @Override
  protected String dedupInstruction() {
    return RealBlankRequestPrompt.DEDUP_INSTRUCTION;
  }
}
