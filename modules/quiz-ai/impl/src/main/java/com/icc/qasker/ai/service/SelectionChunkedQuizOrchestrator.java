package com.icc.qasker.ai.service;

import com.icc.qasker.ai.GeminiFileService;
import com.icc.qasker.ai.dto.AIProblem;
import com.icc.qasker.ai.dto.AISelection;
import com.icc.qasker.ai.mapper.GeminiQuestionMapper;
import com.icc.qasker.ai.properties.QAskerAiProperties;
import com.icc.qasker.ai.service.quality.QualityGate;
import com.icc.qasker.ai.service.support.GeminiMetricsRecorder;
import com.icc.qasker.ai.structure.GeminiQuestion;
import com.icc.qasker.ai.structure.GeminiResponse;
import com.icc.qasker.ai.structure.GeminiResponseSchema;
import java.util.List;
import java.util.Optional;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.converter.BeanOutputConverter;
import tools.jackson.databind.ObjectMapper;

/**
 * 선지형(MULTIPLE/BLANK/OX) 오케스트레이터의 공통 훅 구현. 제네릭 골격 {@link AbstractChunkedQuizOrchestrator}를 {@code
 * GeminiQuestion}으로 고정하고, 선지(AISelection) 기반 스키마·매핑·drop 규칙을 채운다. 타입별로 달라지는 선지 상한·정렬은 하위 구현체가 제공하도록
 * {@link #maxSelectionCount()}·{@link #arrangeSelections(List)}를 다시 추상으로 남긴다.
 */
public abstract class SelectionChunkedQuizOrchestrator
    extends AbstractChunkedQuizOrchestrator<GeminiQuestion> {

  protected SelectionChunkedQuizOrchestrator(
      GeminiFileService geminiFileService,
      ChatModel chatModel,
      ObjectMapper objectMapper,
      GeminiMetricsRecorder metricsRecorder,
      QAskerAiProperties aiProperties,
      QualityGate qualityGate) {
    super(geminiFileService, chatModel, objectMapper, metricsRecorder, aiProperties, qualityGate);
  }

  /** 문항 최대 선택지 수. 초과 문항은 drop 된다 (MULTIPLE/BLANK=4, OX=2). */
  protected abstract int maxSelectionCount();

  /**
   * 선지를 최종 저장 순서로 정렬한다(MULTIPLE/BLANK 셔플, OX O-우선 정규화). 저장 순서 = 대화 히스토리 순서 = 선지에 인라인으로 붙는 해설 정렬
   * 기준이므로 이 시점에 확정한다.
   */
  protected abstract List<AISelection> arrangeSelections(List<AISelection> selections);

  @Override
  protected Class<GeminiQuestion> elementType() {
    return GeminiQuestion.class;
  }

  @Override
  protected String responseSchema(String customInstruction) {
    return GeminiResponseSchema.forInstruction(customInstruction);
  }

  @Override
  protected boolean accept(GeminiQuestion question) {
    return question.selections() == null || question.selections().size() <= maxSelectionCount();
  }

  @Override
  protected AIProblem toProblem(GeminiQuestion question, List<Integer> sourcePages) {
    AIProblem mapped = GeminiQuestionMapper.toDto(List.of(question), sourcePages).quiz().getFirst();
    List<AISelection> arranged =
        mapped.selections() == null ? List.of() : arrangeSelections(mapped.selections());
    return new AIProblem(
        mapped.content(),
        mapped.bloomsLevel(),
        arranged,
        mapped.referencedPages(),
        mapped.appliedInstruction());
  }

  @Override
  protected Optional<GeminiQuestion> parseFirst(String text) {
    GeminiResponse parsed = new BeanOutputConverter<>(GeminiResponse.class).convert(text);
    if (parsed == null || parsed.questions() == null || parsed.questions().isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(parsed.questions().getFirst());
  }
}
