package com.icc.qasker.ai.service;

import com.icc.qasker.ai.QuizOrchestrationService;
import com.icc.qasker.ai.dto.GenerationRequestToAI;
import com.icc.qasker.global.quiz.QuizType;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class QuizOrchestratorServiceImpl implements QuizOrchestrationService {

  private final Map<QuizType, QuizTypeOrchestrator> orchestrators = new EnumMap<>(QuizType.class);

  public QuizOrchestratorServiceImpl(List<QuizTypeOrchestrator> orchestratorList) {
    orchestratorList.forEach(o -> orchestrators.put(o.getSupportedType(), o));
    for (QuizType type : QuizType.values()) {
      if (!orchestrators.containsKey(type))
        throw new IllegalStateException("오케스트레이터 구현이 필요합니다: " + type.toString());
    }
  }

  @Override
  public void generateQuiz(GenerationRequestToAI request) {
    orchestrators.get(request.quizType()).generateQuiz(request);
  }
}
