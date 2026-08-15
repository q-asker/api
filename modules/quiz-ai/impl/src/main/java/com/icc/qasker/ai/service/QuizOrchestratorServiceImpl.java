package com.icc.qasker.ai.service;

import com.icc.qasker.ai.QuizOrchestrationService;
import com.icc.qasker.ai.dto.GenerationRequestToAI;
import com.icc.qasker.ai.strategy.QuizType;
import com.icc.qasker.global.error.CustomException;
import com.icc.qasker.global.error.ExceptionMessage;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/** 퀴즈 타입에 따라 적절한 오케스트레이터로 라우팅한다. */
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
    QuizType quizType;
    try {
      quizType = QuizType.valueOf(request.quizType());
    } catch (IllegalArgumentException e) {
      throw new CustomException(
          ExceptionMessage.DEFAULT_ERROR, "지원하지 않는 퀴즈 타입: " + request.quizType(), e);
    }
    orchestrators.get(quizType).generateQuiz(request);
  }
}
