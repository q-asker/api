package com.icc.qasker.quizmake.service.generation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.icc.qasker.ai.dto.GenerationRequestToAI;
import com.icc.qasker.global.component.HashUtil;
import com.icc.qasker.quizmake.SseNotificationService;
import com.icc.qasker.quizmake.adapter.AIServerAdapter;
import com.icc.qasker.quizmake.dto.ferequest.GenerationRequest;
import com.icc.qasker.quizmake.dto.ferequest.enums.Language;
import com.icc.qasker.quizset.QuizCommandService;
import com.icc.qasker.quizset.QuizQueryService;
import com.icc.qasker.quizset.dto.ferequest.enums.QuizType;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class GenerationCommandServiceImplTest {

  private AIServerAdapter aiServerAdapter;
  private SseNotificationService notificationService;
  private QuizCommandService quizCommandService;
  private QuizQueryService quizQueryService;
  private HashUtil hashUtil;
  private GenerationResultRecorder resultRecorder;
  private GenerationCommandServiceImpl service;

  @BeforeEach
  void setUp() {
    aiServerAdapter = mock(AIServerAdapter.class);
    notificationService = mock(SseNotificationService.class);
    quizCommandService = mock(QuizCommandService.class);
    quizQueryService = mock(QuizQueryService.class);
    hashUtil = mock(HashUtil.class);
    resultRecorder = mock(GenerationResultRecorder.class);

    when(quizCommandService.initProblemSet(any(), any(), any(), anyInt(), any(), any(), any()))
        .thenReturn(1L);

    service =
        new GenerationCommandServiceImpl(
            aiServerAdapter,
            notificationService,
            quizCommandService,
            quizQueryService,
            hashUtil,
            resultRecorder);
  }

  @Test
  @DisplayName("REAL_BLANK 요청은 DB에는 REAL_BLANK 그대로 저장한다")
  void real_blank_request_persists_real_blank_to_db() {
    GenerationRequest request = request(QuizType.REAL_BLANK);

    service.triggerGeneration("user-1", request);

    verify(quizCommandService, timeout(2000))
        .initProblemSet(
            eq("user-1"), any(), any(), anyInt(), eq(QuizType.REAL_BLANK), any(), any());
  }

  @Test
  @DisplayName("REAL_BLANK 요청은 AI 서버에 strategyValue=BLANK로 전달한다")
  void real_blank_request_calls_ai_with_blank_strategy() {
    GenerationRequest request = request(QuizType.REAL_BLANK);

    service.triggerGeneration("user-1", request);

    ArgumentCaptor<GenerationRequestToAI> captor =
        ArgumentCaptor.forClass(GenerationRequestToAI.class);
    verify(aiServerAdapter, timeout(2000)).streamRequest(captor.capture());
    assertThat(captor.getValue().strategyValue()).isEqualTo("BLANK");
  }

  @Test
  @DisplayName("BLANK 요청은 AI 서버에 strategyValue=BLANK로 전달한다 (회귀 방지)")
  void blank_request_calls_ai_with_blank_strategy() {
    GenerationRequest request = request(QuizType.BLANK);

    service.triggerGeneration("user-1", request);

    ArgumentCaptor<GenerationRequestToAI> captor =
        ArgumentCaptor.forClass(GenerationRequestToAI.class);
    verify(aiServerAdapter, timeout(2000)).streamRequest(captor.capture());
    assertThat(captor.getValue().strategyValue()).isEqualTo("BLANK");
  }

  @Test
  @DisplayName("MULTIPLE 요청은 AI 서버에 strategyValue=MULTIPLE로 전달한다 (회귀 방지)")
  void multiple_request_calls_ai_with_multiple_strategy() {
    GenerationRequest request = request(QuizType.MULTIPLE);

    service.triggerGeneration("user-1", request);

    ArgumentCaptor<GenerationRequestToAI> captor =
        ArgumentCaptor.forClass(GenerationRequestToAI.class);
    verify(aiServerAdapter, timeout(2000)).streamRequest(captor.capture());
    assertThat(captor.getValue().strategyValue()).isEqualTo("MULTIPLE");
  }

  private GenerationRequest request(QuizType quizType) {
    return new GenerationRequest(
        null,
        UUID.randomUUID().toString(),
        "https://example.com/file.pdf",
        "title",
        5,
        quizType,
        List.of(1, 2),
        Language.KO);
  }
}
