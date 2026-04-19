package com.icc.qasker.ai.service;

import com.icc.qasker.ai.QuizOrchestrationService;
import com.icc.qasker.ai.dto.GenerationRequestToAI;
import com.icc.qasker.global.error.CustomException;
import com.icc.qasker.global.error.ExceptionMessage;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/** 퀴즈 타입에 따라 적절한 오케스트레이터로 라우팅한다. */
@Service
public class QuizOrchestratorServiceImpl implements QuizOrchestrationService {

  private final Map<String, QuizTypeOrchestrator> orchestrators;

  public QuizOrchestratorServiceImpl(List<QuizTypeOrchestrator> orchestratorList) {
    this.orchestrators =
        orchestratorList.stream()
            .collect(Collectors.toMap(QuizTypeOrchestrator::getSupportedType, Function.identity()));
  }

  @Override
  public int generateQuiz(GenerationRequestToAI request) {
    QuizTypeOrchestrator orchestrator = orchestrators.get(request.strategyValue());
    if (orchestrator == null) {
      throw new CustomException(ExceptionMessage.AI_SERVER_RESPONSE_ERROR);
    }
    return orchestrator.generateQuiz(request);
  }
}
