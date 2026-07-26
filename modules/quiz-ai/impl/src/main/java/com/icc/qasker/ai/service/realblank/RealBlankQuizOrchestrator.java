package com.icc.qasker.ai.service.realblank;

import com.icc.qasker.ai.GeminiFileService;
import com.icc.qasker.ai.dto.AISelection;
import com.icc.qasker.ai.properties.QAskerAiProperties;
import com.icc.qasker.ai.service.SelectionChunkedQuizOrchestrator;
import com.icc.qasker.ai.service.quality.QualityGate;
import com.icc.qasker.ai.service.realblank.prompt.RealBlankRequestPrompt;
import com.icc.qasker.ai.service.support.GeminiMetricsRecorder;
import java.util.List;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/** 직접 입력형 빈칸채우기(REAL_BLANK) 퀴즈 오케스트레이터. 오답 선택지를 만들지 않는다. */
@Component
public class RealBlankQuizOrchestrator extends SelectionChunkedQuizOrchestrator {

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
  public String getSupportedType() {
    return "REAL_BLANK";
  }

  @Override
  protected int maxSelectionCount() {
    return 1;
  }

  @Override
  protected String dedupInstruction() {
    return RealBlankRequestPrompt.DEDUP_INSTRUCTION;
  }

  @Override
  protected List<AISelection> arrangeSelections(List<AISelection> selections) {
    return selections;
  }

  @Override
  protected boolean includeAcceptedAnswers() {
    return true;
  }
}
