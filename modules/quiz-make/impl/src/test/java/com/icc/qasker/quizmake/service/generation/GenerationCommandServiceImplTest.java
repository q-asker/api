package com.icc.qasker.quizmake.service.generation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.icc.qasker.ai.dto.AIProblem;
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
            resultRecorder,
            mock(com.icc.qasker.quizset.QualityLogService.class));
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
  @DisplayName("문제 저장: saveProblem 호출마다 1..n 순차 번호를 부여한다")
  void save_problem_assigns_sequential_numbers() {
    GenerationRequest request = request(QuizType.MULTIPLE);
    stubSink(2);

    service.triggerGeneration("user-1", request);

    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<QuizGeneratedFromAI>> captor = ArgumentCaptor.forClass(List.class);
    verify(quizCommandService, timeout(2000).times(2)).saveBatch(captor.capture(), eq(1L));
    assertThat(captor.getAllValues())
        .allSatisfy(saved -> assertThat(saved).hasSize(1))
        .flatExtracting(saved -> saved)
        .extracting(QuizGeneratedFromAI::getNumber)
        .containsExactly(1, 2);
  }

  @Test
  @DisplayName("문제 저장: SSE eventId는 방금 저장된 문제 번호로 전송된다")
  void save_problem_sends_number_as_event_id() {
    GenerationRequest request = request(QuizType.MULTIPLE);
    stubSink(2);

    service.triggerGeneration("user-1", request);

    verify(notificationService, timeout(2000)).sendCreatedMessageWithId(any(), eq("2"), any());
  }

  @Test
  @DisplayName("문제 저장: 선택지 집합은 보존된다 (셔플은 오케스트레이터 책임으로 이관)")
  void save_problem_preserves_selection_set() {
    GenerationRequest request = request(QuizType.MULTIPLE);
    stubSink(1);

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
  @DisplayName("finalize: 생성 수가 0이면 FAILED 상태로 마감하고 에러를 기록한다")
  void finalize_marks_failed_when_nothing_generated() {
    GenerationRequest request = request(QuizType.MULTIPLE);
    // streamRequest는 아무 문항도 전달하지 않는다(mock 기본 no-op) → generatedCount 0 → FAILED

    service.triggerGeneration("user-1", request);

    verify(quizCommandService, timeout(2000)).updateStatus(eq(1L), eq(GenerationStatus.FAILED));
    verify(notificationService, timeout(2000)).sendFinishWithError(any(), any());
    verify(resultRecorder, timeout(2000)).recordError(eq(1L), eq(QuizType.MULTIPLE), any());
  }

  @Test
  @DisplayName("finalize: 생성 수가 요청 수와 같으면 COMPLETED + 성공 기록")
  void finalize_marks_completed_on_full_success() {
    GenerationRequest request = request(QuizType.MULTIPLE); // quizCount=5
    stubSink(5);

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
    stubSink(3);

    service.triggerGeneration("user-1", request);

    verify(quizCommandService, timeout(2000)).updateStatus(eq(1L), eq(GenerationStatus.COMPLETED));
    verify(resultRecorder, timeout(2000))
        .recordPartialSuccess(eq(1L), eq(QuizType.MULTIPLE), eq(3L), eq(5L), anyLong(), anyLong());
  }

  @Test
  @DisplayName("finalize: 스트림 예외 발생 시 FAILED 상태로 마감한다")
  void finalize_marks_failed_when_stream_throws() {
    GenerationRequest request = request(QuizType.MULTIPLE);
    doThrow(new RuntimeException("boom")).when(aiServerAdapter).streamRequest(any());

    service.triggerGeneration("user-1", request);

    verify(quizCommandService, timeout(2000)).updateStatus(eq(1L), eq(GenerationStatus.FAILED));
    verify(resultRecorder, timeout(2000)).recordError(eq(1L), eq(QuizType.MULTIPLE), any());
  }

  // ── helpers ────────────────────────────────────────────────

  /** streamRequest 호출 시 sink에 deliverCount건의 문제를 순차 저장하도록 스텁한다(배치 인터리빙 Phase 1). */
  private void stubSink(int deliverCount) {
    when(quizCommandService.saveBatch(anyList(), eq(1L)))
        .thenAnswer(
            invocation -> {
              List<QuizGeneratedFromAI> quizzes = invocation.getArgument(0);
              return quizzes.stream().map(QuizGeneratedFromAI::getNumber).toList();
            });
    when(quizQueryService.getQuizViews(anyLong(), anyList()))
        .thenAnswer(
            invocation -> {
              List<Integer> nums = invocation.getArgument(1);
              return nums.stream().map(this::quizView).toList();
            });
    doAnswer(
            invocation -> {
              GenerationRequestToAI req = invocation.getArgument(0);
              for (int i = 0; i < deliverCount; i++) {
                req.sink().saveProblem(multipleProblem());
              }
              return null;
            })
        .when(aiServerAdapter)
        .streamRequest(any());
  }

  private AIProblem multipleProblem() {
    return new AIProblem(
        "질문",
        "이해",
        List.of(
            new AISelection("A", null, true),
            new AISelection("B", null, false),
            new AISelection("C", null, false),
            new AISelection("D", null, false)),
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
