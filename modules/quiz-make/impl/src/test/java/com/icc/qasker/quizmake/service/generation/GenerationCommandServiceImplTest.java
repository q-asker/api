package com.icc.qasker.quizmake.service.generation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.icc.qasker.ai.dto.AIProblem;
import com.icc.qasker.ai.dto.AIProblemSet;
import com.icc.qasker.ai.dto.AISelection;
import com.icc.qasker.ai.dto.GenerationRequestToAI;
import com.icc.qasker.global.component.HashUtil;
import com.icc.qasker.quizmake.SseNotificationService;
import com.icc.qasker.quizmake.adapter.AIServerAdapter;
import com.icc.qasker.quizmake.dto.ferequest.GenerationRequest;
import com.icc.qasker.quizmake.dto.ferequest.enums.Language;
import com.icc.qasker.quizset.GenerationStatus;
import com.icc.qasker.quizset.QuizCommandService;
import com.icc.qasker.quizset.QuizQueryService;
import com.icc.qasker.quizset.dto.airesponse.ProblemSetGeneratedEvent.QuizGeneratedFromAI;
import com.icc.qasker.quizset.dto.ferequest.enums.QuizType;
import com.icc.qasker.quizset.view.QuizView;
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

  @Test
  @DisplayName("배치 소비: 문제에 1..n 순차 번호를 부여하고 saveBatch로 저장한다")
  void batch_consumer_assigns_sequential_numbers_and_saves() {
    GenerationRequest request = request(QuizType.MULTIPLE);
    AIProblemSet batch = batchOf(multipleProblem(), multipleProblem());
    stubConsumer(batch, List.of(1, 2), List.of(quizView(1), quizView(2)));

    service.triggerGeneration("user-1", request);

    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<QuizGeneratedFromAI>> captor = ArgumentCaptor.forClass(List.class);
    verify(quizCommandService, timeout(2000)).saveBatch(captor.capture(), eq(1L));
    assertThat(captor.getValue()).extracting(QuizGeneratedFromAI::getNumber).containsExactly(1, 2);
  }

  @Test
  @DisplayName("배치 소비: SSE eventId는 저장된 마지막 문제 번호로 전송된다")
  void batch_consumer_sends_last_number_as_event_id() {
    GenerationRequest request = request(QuizType.MULTIPLE);
    AIProblemSet batch = batchOf(multipleProblem(), multipleProblem());
    stubConsumer(batch, List.of(1, 2), List.of(quizView(1), quizView(2)));

    service.triggerGeneration("user-1", request);

    verify(notificationService, timeout(2000)).sendCreatedMessageWithId(any(), eq("2"), any());
  }

  @Test
  @DisplayName("배치 소비: MULTIPLE 선택지는 순서만 바뀌고 집합은 보존된다")
  void multiple_selections_are_shuffled_preserving_set() {
    GenerationRequest request = request(QuizType.MULTIPLE);
    AIProblemSet batch = batchOf(multipleProblem());
    stubConsumer(batch, List.of(1), List.of(quizView(1)));

    service.triggerGeneration("user-1", request);

    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<QuizGeneratedFromAI>> captor = ArgumentCaptor.forClass(List.class);
    verify(quizCommandService, timeout(2000)).saveBatch(captor.capture(), eq(1L));
    List<String> contents =
        captor.getValue().get(0).getSelections().stream()
            .map(QuizGeneratedFromAI.SelectionsOfAI::getContent)
            .toList();
    assertThat(contents).containsExactlyInAnyOrder("A", "B", "C", "D");
  }

  @Test
  @DisplayName("배치 소비: OX 선택지는 X가 1번이면 O가 항상 1번이 되도록 정규화된다")
  void ox_selections_are_normalized_so_o_is_first() {
    GenerationRequest request =
        new GenerationRequest(
            null,
            UUID.randomUUID().toString(),
            "https://example.com/file.pdf",
            "title",
            5,
            QuizType.OX,
            List.of(1, 2),
            Language.KO);
    AIProblemSet batch =
        batchOf(
            new AIProblem(
                "질문",
                "이해",
                List.of(new AISelection("X", "틀림 해설", false), new AISelection("O", "맞음 해설", true)),
                List.of(1),
                null));
    stubConsumer(batch, List.of(1), List.of(quizView(1)));

    service.triggerGeneration("user-1", request);

    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<QuizGeneratedFromAI>> captor = ArgumentCaptor.forClass(List.class);
    verify(quizCommandService, timeout(2000)).saveBatch(captor.capture(), eq(1L));
    assertThat(captor.getValue().get(0).getSelections().get(0).getContent()).isEqualTo("O");
  }

  @Test
  @DisplayName("finalize: 생성 수가 0이면 FAILED 상태로 마감하고 에러를 기록한다")
  void finalize_marks_failed_when_nothing_generated() {
    GenerationRequest request = request(QuizType.MULTIPLE);
    when(aiServerAdapter.streamRequest(any())).thenReturn(3);

    service.triggerGeneration("user-1", request);

    verify(quizCommandService, timeout(2000)).updateStatus(eq(1L), eq(GenerationStatus.FAILED));
    verify(notificationService, timeout(2000)).sendFinishWithError(any(), any());
    verify(resultRecorder, timeout(2000)).recordError(eq(1L), eq(QuizType.MULTIPLE), any());
  }

  @Test
  @DisplayName("finalize: 생성 수가 요청 수와 같으면 COMPLETED + 성공 기록")
  void finalize_marks_completed_on_full_success() {
    GenerationRequest request = request(QuizType.MULTIPLE); // quizCount=5
    AIProblemSet batch = batchOf(multipleProblem());
    stubConsumer(
        batch,
        List.of(1, 2, 3, 4, 5),
        List.of(quizView(1), quizView(2), quizView(3), quizView(4), quizView(5)));

    service.triggerGeneration("user-1", request);

    verify(quizCommandService, timeout(2000)).updateStatus(eq(1L), eq(GenerationStatus.COMPLETED));
    verify(notificationService, timeout(2000)).sendComplete(any());
    verify(resultRecorder, timeout(2000))
        .recordSuccess(eq(1L), eq(QuizType.MULTIPLE), eq(5L), anyLong(), anyLong());
  }

  @Test
  @DisplayName("finalize: 일부만 생성되면 COMPLETED + 부분 성공 기록")
  void finalize_marks_partial_success() {
    GenerationRequest request = request(QuizType.MULTIPLE); // quizCount=5
    AIProblemSet batch = batchOf(multipleProblem());
    stubConsumer(batch, List.of(1, 2, 3), List.of(quizView(1), quizView(2), quizView(3)));

    service.triggerGeneration("user-1", request);

    verify(quizCommandService, timeout(2000)).updateStatus(eq(1L), eq(GenerationStatus.COMPLETED));
    verify(resultRecorder, timeout(2000))
        .recordPartialSuccess(eq(1L), eq(QuizType.MULTIPLE), eq(3L), eq(5L), anyLong(), anyLong());
  }

  @Test
  @DisplayName("finalize: 스트림 예외 발생 시 FAILED 상태로 마감한다")
  void finalize_marks_failed_when_stream_throws() {
    GenerationRequest request = request(QuizType.MULTIPLE);
    when(aiServerAdapter.streamRequest(any())).thenThrow(new RuntimeException("boom"));

    service.triggerGeneration("user-1", request);

    verify(quizCommandService, timeout(2000)).updateStatus(eq(1L), eq(GenerationStatus.FAILED));
    verify(resultRecorder, timeout(2000)).recordError(eq(1L), eq(QuizType.MULTIPLE), any());
  }

  // ── helpers ────────────────────────────────────────────────

  /** streamRequest가 호출되면 넘겨받은 consumer에 batch를 전달하도록 스텁한다. */
  private void stubConsumer(AIProblemSet batch, List<Integer> savedNumbers, List<QuizView> views) {
    when(quizCommandService.saveBatch(anyList(), eq(1L))).thenReturn(savedNumbers);
    when(quizQueryService.getQuizViews(anyLong(), anyList())).thenReturn(views);
    when(aiServerAdapter.streamRequest(any()))
        .thenAnswer(
            invocation -> {
              GenerationRequestToAI req = invocation.getArgument(0);
              req.questionsConsumer().accept(batch);
              return 3;
            });
  }

  private AIProblemSet batchOf(AIProblem... problems) {
    return new AIProblemSet(List.of(problems));
  }

  private AIProblem multipleProblem() {
    return new AIProblem(
        "질문",
        "이해",
        List.of(
            new AISelection("A", "해설 A", true),
            new AISelection("B", "해설 B", false),
            new AISelection("C", "해설 C", false),
            new AISelection("D", "해설 D", false)),
        List.of(1),
        null);
  }

  private QuizView quizView(int number) {
    return new QuizView(number, "title-" + number, 0, false, List.of(), null);
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
