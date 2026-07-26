package com.icc.qasker.ai.service;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.icc.qasker.ai.dto.GenerationRequestToAI;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** strategyValue 문자열 키로 올바른 오케스트레이터에 라우팅되는지 검증한다(FR-001 회귀 방지 — REAL_BLANK가 BLANK로 접히지 않음). */
class QuizOrchestratorServiceImplTest {

  private QuizTypeOrchestrator blank;
  private QuizTypeOrchestrator realBlank;
  private QuizOrchestratorServiceImpl service;

  @BeforeEach
  void setUp() {
    blank = mock(QuizTypeOrchestrator.class);
    when(blank.getSupportedType()).thenReturn("BLANK");
    realBlank = mock(QuizTypeOrchestrator.class);
    when(realBlank.getSupportedType()).thenReturn("REAL_BLANK");
    service = new QuizOrchestratorServiceImpl(List.of(blank, realBlank));
  }

  @Test
  @DisplayName("strategyValue=REAL_BLANK는 REAL_BLANK 오케스트레이터로만 라우팅된다")
  void routes_real_blank_to_real_blank_orchestrator_only() {
    GenerationRequestToAI request =
        GenerationRequestToAI.builder().strategyValue("REAL_BLANK").build();

    service.generateQuiz(request);

    verify(realBlank).generateQuiz(request);
    verify(blank, never()).generateQuiz(request);
  }

  @Test
  @DisplayName("strategyValue=BLANK는 BLANK 오케스트레이터로만 라우팅된다 (회귀 방지)")
  void routes_blank_to_blank_orchestrator_only() {
    GenerationRequestToAI request = GenerationRequestToAI.builder().strategyValue("BLANK").build();

    service.generateQuiz(request);

    verify(blank).generateQuiz(request);
    verify(realBlank, never()).generateQuiz(request);
  }
}
