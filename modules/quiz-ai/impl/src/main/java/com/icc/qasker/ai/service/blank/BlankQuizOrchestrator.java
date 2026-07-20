package com.icc.qasker.ai.service.blank;

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

/** 빈칸채우기(BLANK) 퀴즈 오케스트레이터. */
@Component
public class BlankQuizOrchestrator extends AbstractChunkedQuizOrchestrator {

  public BlankQuizOrchestrator(
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
    return "BLANK";
  }

  @Override
  protected int maxSelectionCount() {
    return 4;
  }

  @Override
  protected String dedupInstruction() {
    return "\n\n> **CRITICAL RULE**: 위 직전 문항 목록과 빈칸 핵심 어휘·맥락·정답 분포(answerIndex)가 겹치지 않게 이번 청크 문항을 작성한다."
        + " stemSummary와 동일·유사한 맥락은 다른 단원·다른 페이지에서 가져와 재구성하고,"
        + " 정답 위치(answerIndex)가 직전 청크와 같은 쪽으로 쏠리지 않게 분산한다.";
  }

  @Override
  protected List<AISelection> arrangeSelections(List<AISelection> selections) {
    return SelectionArrangement.shuffle(selections);
  }
}
