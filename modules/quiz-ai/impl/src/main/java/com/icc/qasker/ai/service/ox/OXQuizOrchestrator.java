package com.icc.qasker.ai.service.ox;

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

/** OX 퀴즈 오케스트레이터. */
@Component
public class OXQuizOrchestrator extends AbstractChunkedQuizOrchestrator {

  public OXQuizOrchestrator(
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
    return "OX";
  }

  @Override
  protected int maxSelectionCount() {
    return 2;
  }

  @Override
  protected String dedupInstruction() {
    return "\n\n> **CRITICAL RULE**: 위 직전 문항 목록과 주제·표현·정답(O/X 분포)이 겹치지 않게 이번 청크 문항을 작성한다."
        + " stemSummary와 동일·유사한 진술은 다른 각도(다른 강의노트 페이지, 다른 개념 차원)로 재구성하고,"
        + " 정답(answerIndex)이 직전 청크와 한쪽으로 쏠리지 않게 분산한다.";
  }

  @Override
  protected List<AISelection> arrangeSelections(List<AISelection> selections) {
    return SelectionArrangement.normalizeOxOrder(selections);
  }
}
