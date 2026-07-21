package com.icc.qasker.ai.service.essay;

import com.icc.qasker.ai.GeminiFileService;
import com.icc.qasker.ai.dto.AIProblem;
import com.icc.qasker.ai.mapper.GeminiEssayQuestionMapper;
import com.icc.qasker.ai.properties.QAskerAiProperties;
import com.icc.qasker.ai.service.AbstractChunkedQuizOrchestrator;
import com.icc.qasker.ai.service.essay.prompt.EssayRequestPrompt;
import com.icc.qasker.ai.service.quality.QualityGate;
import com.icc.qasker.ai.service.support.GeminiMetricsRecorder;
import com.icc.qasker.ai.structure.GeminiEssayQuestion;
import com.icc.qasker.ai.structure.GeminiEssayResponse;
import com.icc.qasker.ai.structure.GeminiEssayResponseSchema;
import java.util.List;
import java.util.Optional;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * 서술형(ESSAY) 퀴즈 오케스트레이터. 청크형 골격 {@link AbstractChunkedQuizOrchestrator}를 {@code
 * GeminiEssayQuestion}으로 고정해 선지형과 동일한 실행모델(청크 분할·비동기 품질검증·컨텍스트 캐시·멀티턴 대화)을 공유한다. 서술형은 선지가 없으므로
 * {@link #accept}는 항상 true이고, {@link #toProblem}은 매퍼가 만드는 단일 선지(모범답안=content, 해설=explanation,
 * correct=true)를 그대로 보존한다(하류 채점이 이 규약으로 모범답안·루브릭을 추출).
 */
@Component
public class EssayQuizOrchestrator extends AbstractChunkedQuizOrchestrator<GeminiEssayQuestion> {

  public EssayQuizOrchestrator(
      GeminiFileService geminiFileService,
      ChatModel chatModel,
      ObjectMapper objectMapper,
      GeminiMetricsRecorder metricsRecorder,
      QAskerAiProperties aiProperties,
      QualityGate qualityGate) {
    super(geminiFileService, chatModel, objectMapper, metricsRecorder, aiProperties, qualityGate);
  }

  @Override
  public String getSupportedType() {
    return "ESSAY";
  }

  @Override
  protected Class<GeminiEssayQuestion> elementType() {
    return GeminiEssayQuestion.class;
  }

  @Override
  protected String responseSchema(String customInstruction) {
    return GeminiEssayResponseSchema.forInstruction(customInstruction);
  }

  @Override
  protected boolean accept(GeminiEssayQuestion question) {
    // 서술형은 선지 개념이 없으므로 drop 규칙 없음.
    return true;
  }

  @Override
  protected AIProblem toProblem(GeminiEssayQuestion question, List<Integer> sourcePages) {
    // 매퍼 결과를 그대로 보존(재조립 금지) — modelAnswer→선지.content, explanation→선지.explanation, correct=true.
    return GeminiEssayQuestionMapper.toDto(List.of(question), sourcePages).quiz().getFirst();
  }

  @Override
  protected Optional<GeminiEssayQuestion> parseFirst(String text) {
    GeminiEssayResponse parsed = new BeanOutputConverter<>(GeminiEssayResponse.class).convert(text);
    if (parsed == null || parsed.questions() == null || parsed.questions().isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(parsed.questions().getFirst());
  }

  @Override
  protected String dedupInstruction() {
    return EssayRequestPrompt.DEDUP_INSTRUCTION;
  }
}
