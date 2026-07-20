package com.icc.qasker.ai.service.multiple;

import com.icc.qasker.ai.GeminiFileService;
import com.icc.qasker.ai.dto.AISelection;
import com.icc.qasker.ai.properties.QAskerAiProperties;
import com.icc.qasker.ai.service.AbstractChunkedQuizOrchestrator;
import com.icc.qasker.ai.service.quality.QualityGate;
import com.icc.qasker.ai.service.support.GeminiMetricsRecorder;
import com.icc.qasker.ai.service.support.SelectionArrangement;
import java.util.List;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/** 객관식(MULTIPLE) 퀴즈 오케스트레이터. */
@Component
public class MultipleQuizOrchestrator extends AbstractChunkedQuizOrchestrator {

  public MultipleQuizOrchestrator(
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
    return "MULTIPLE";
  }

  @Override
  protected int maxSelectionCount() {
    return 4;
  }

  @Override
  protected String dedupInstruction() {
    return "\n\n> **CRITICAL RULE**: 위 직전 문항 목록과 주제·표현·정답 분포(answerIndex)·Bloom's 하위 과정이"
        + " 겹치지 않게 이번 청크 문항을 작성한다."
        + " stemSummary와 동일·유사한 주제는 다른 각도(다른 패턴 라벨, 다른 강의노트 페이지)로 재구성하고,"
        + " 정답 위치(answerIndex)가 직전 청크와 같은 쪽으로 쏠리지 않게 분산한다.";
  }

  @Override
  protected List<AISelection> arrangeSelections(List<AISelection> selections) {
    return SelectionArrangement.shuffle(selections);
  }
}
