package com.icc.qasker.ai.service.blank;

import com.icc.qasker.ai.GeminiFileService;
import com.icc.qasker.ai.dto.AISelection;
import com.icc.qasker.ai.properties.QAskerAiProperties;
import com.icc.qasker.ai.service.SelectionChunkedQuizOrchestrator;
import com.icc.qasker.ai.service.blank.prompt.BlankRequestPrompt;
import com.icc.qasker.ai.service.quality.QualityGate;
import com.icc.qasker.ai.service.support.GeminiMetricsRecorder;
import com.icc.qasker.ai.service.support.SelectionArrangement;
import java.util.List;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/** 빈칸채우기(BLANK) 퀴즈 오케스트레이터. */
@Component
public class BlankQuizOrchestrator extends SelectionChunkedQuizOrchestrator {

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
    return BlankRequestPrompt.DEDUP_INSTRUCTION;
  }

  @Override
  protected List<AISelection> arrangeSelections(List<AISelection> selections) {
    return SelectionArrangement.shuffle(selections);
  }
}
