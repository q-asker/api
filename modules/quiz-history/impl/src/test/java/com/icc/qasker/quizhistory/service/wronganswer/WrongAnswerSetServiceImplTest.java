package com.icc.qasker.quizhistory.service.wronganswer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.icc.qasker.global.component.HashUtil;
import com.icc.qasker.global.error.CustomException;
import com.icc.qasker.quizhistory.dto.ferequest.CreateWrongAnswerSetRequest;
import com.icc.qasker.quizhistory.dto.feresponse.WrongAnswerSetResponse;
import com.icc.qasker.quizhistory.dto.feresponse.WrongAnswerSetResponse.CreatedSet;
import com.icc.qasker.quizhistory.dto.feresponse.WrongAnswerSetResponse.EmptyReason;
import com.icc.qasker.quizhistory.entity.AnswerSnapshot;
import com.icc.qasker.quizhistory.entity.QuizFolder;
import com.icc.qasker.quizhistory.entity.QuizHistory;
import com.icc.qasker.quizhistory.entity.QuizHistory.QuizHistoryStatus;
import com.icc.qasker.quizhistory.repository.QuizFolderRepository;
import com.icc.qasker.quizhistory.repository.QuizHistoryRepository;
import com.icc.qasker.quizset.ProblemSetReadService;
import com.icc.qasker.quizset.dto.ferequest.enums.QuizType;
import com.icc.qasker.quizset.dto.readonly.ProblemDetail;
import com.icc.qasker.quizset.dto.readonly.ProblemSetSummary;
import com.icc.qasker.quizset.dto.readonly.SelectionDetail;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

/** 수집 범위 격리(SC-003)·서술형 제외·빈 결과 사유·부분 실패 격리·멱등 동작을 고정한다. */
@ExtendWith(MockitoExtension.class)
class WrongAnswerSetServiceImplTest {

  private static final String USER = "me";
  private static final String FOLDER_HASH = "FOLDER";
  private static final long FOLDER_ID = 42L;
  private static final String KEY = "idem-key";

  @Mock private QuizFolderRepository quizFolderRepository;
  @Mock private QuizHistoryRepository quizHistoryRepository;
  @Mock private ProblemSetReadService problemSetReadService;
  @Mock private WrongAnswerSetFactory factory;
  @Mock private HashUtil hashUtil;

  private WrongAnswerSetServiceImpl service;

  @BeforeEach
  void setUp() {
    service =
        new WrongAnswerSetServiceImpl(
            quizFolderRepository, quizHistoryRepository, problemSetReadService, factory, hashUtil);
    lenient().when(hashUtil.decode(FOLDER_HASH)).thenReturn(FOLDER_ID);
  }

  private void folderIsMine() {
    when(quizFolderRepository.findByIdAndUserId(FOLDER_ID, USER))
        .thenReturn(
            Optional.of(QuizFolder.builder().id(FOLDER_ID).userId(USER).name("폴더").build()));
  }

  private void historiesInFolder(QuizHistory... histories) {
    when(quizHistoryRepository.findAllByUserIdAndFolderIdAndStatusOrderByCreatedAtDesc(
            USER, FOLDER_ID, QuizHistoryStatus.COMPLETED))
        .thenReturn(List.of(histories));
  }

  private QuizHistory history(long problemSetId, int questionCount) {
    QuizHistory history =
        QuizHistory.builder().userId(USER).problemSetId(problemSetId).folderId(FOLDER_ID).build();
    // 전부 3번을 골라 오답(정답은 2번)
    history.completeQuiz(
        java.util.stream.IntStream.rangeClosed(1, questionCount)
            .mapToObj(number -> new AnswerSnapshot(number, 3, false, null))
            .toList(),
        0,
        "00:10");
    return history;
  }

  private ProblemSetSummary summary(long id, QuizType type, int count) {
    return new ProblemSetSummary(id, type, count, "원본", Instant.parse("2026-01-01T00:00:00Z"));
  }

  private void problemsOf(long problemSetId, int count) {
    when(problemSetReadService.findProblemsByProblemSetId(problemSetId))
        .thenReturn(
            java.util.stream.IntStream.rangeClosed(1, count)
                .mapToObj(
                    number ->
                        new ProblemDetail(
                            number,
                            "문항",
                            List.of(
                                new SelectionDetail("1번", false), new SelectionDetail("2번", true)),
                            "해설"))
                .toList());
  }

  private void factoryCreatesSuccessfully() {
    when(factory.create(anyString(), anyLong(), anyString(), any(), any(), anyBoolean(), any()))
        .thenAnswer(
            invocation ->
                new CreatedSet(
                    "PSID",
                    "HID",
                    invocation.getArgument(3),
                    "제목",
                    ((List<?>) invocation.getArgument(4)).size(),
                    invocation.getArgument(5)));
  }

  private WrongAnswerSetResponse run() {
    return service.createFromFolder(USER, new CreateWrongAnswerSetRequest(FOLDER_HASH), KEY);
  }

  @Test
  @DisplayName("내 폴더가 아니면 만들지 않는다")
  void rejectsForeignFolder() {
    when(quizFolderRepository.findByIdAndUserId(FOLDER_ID, USER)).thenReturn(Optional.empty());

    assertThatThrownBy(this::run).isInstanceOf(CustomException.class);
  }

  @Test
  @DisplayName("수집 범위는 내 기록 ∩ 이 폴더 ∩ 끝까지 푼 것으로만 닫힌다 (SC-003)")
  void scopeIsClosedByUserAndFolder() {
    folderIsMine();
    historiesInFolder();

    run();

    // 다른 조건으로 기록을 끌어오는 경로가 없어야 폴더 밖·타인 기록이 샐 자리가 없다.
    verify(quizHistoryRepository)
        .findAllByUserIdAndFolderIdAndStatusOrderByCreatedAtDesc(
            USER, FOLDER_ID, QuizHistoryStatus.COMPLETED);
  }

  @Test
  @DisplayName("서술형은 수집하지 않고 제외된 문항 수만 알린다 (FR-016·FR-016a)")
  void excludesEssay() {
    folderIsMine();
    historiesInFolder(history(1L, 3), history(2L, 2));
    when(problemSetReadService.findProblemSetsByIds(any()))
        .thenReturn(List.of(summary(1L, QuizType.ESSAY, 3), summary(2L, QuizType.MULTIPLE, 2)));
    problemsOf(2L, 2);
    factoryCreatesSuccessfully();

    WrongAnswerSetResponse response = run();

    assertThat(response.excludedEssayCount()).isEqualTo(3);
    assertThat(response.createdSets())
        .singleElement()
        .satisfies(set -> assertThat(set.quizType()).isEqualTo(QuizType.MULTIPLE));
  }

  @Test
  @DisplayName("원본이 지워진 기록은 건너뛰고 나머지로 구성한다")
  void skipsDeletedSource() {
    folderIsMine();
    historiesInFolder(history(1L, 2), history(2L, 2));
    when(problemSetReadService.findProblemSetsByIds(any()))
        .thenReturn(List.of(summary(2L, QuizType.MULTIPLE, 2)));
    problemsOf(2L, 2);
    factoryCreatesSuccessfully();

    WrongAnswerSetResponse response = run();

    assertThat(response.deletedSourceCount()).isEqualTo(1);
    assertThat(response.createdSets()).hasSize(1);
    assertThat(response.emptyReason()).isNull();
  }

  @Test
  @DisplayName("푼 기록이 없으면 사유가 NO_HISTORY 다")
  void emptyWithoutHistory() {
    folderIsMine();
    historiesInFolder();

    assertThat(run().emptyReason()).isEqualTo(EmptyReason.NO_HISTORY);
  }

  @Test
  @DisplayName("수집 가능한 것이 서술형뿐이면 사유가 ESSAY_ONLY 다")
  void emptyWithEssayOnly() {
    folderIsMine();
    historiesInFolder(history(1L, 3));
    when(problemSetReadService.findProblemSetsByIds(any()))
        .thenReturn(List.of(summary(1L, QuizType.ESSAY, 3)));

    WrongAnswerSetResponse response = run();

    assertThat(response.emptyReason()).isEqualTo(EmptyReason.ESSAY_ONLY);
    assertThat(response.excludedEssayCount()).isEqualTo(3);
  }

  @Test
  @DisplayName("원본이 전부 지워졌으면 사유가 SOURCE_DELETED 다")
  void emptyWithDeletedSources() {
    folderIsMine();
    historiesInFolder(history(1L, 2));
    when(problemSetReadService.findProblemSetsByIds(any())).thenReturn(List.of());

    assertThat(run().emptyReason()).isEqualTo(EmptyReason.SOURCE_DELETED);
  }

  @Test
  @DisplayName("전부 맞혔으면 사유가 ALL_CORRECT 다")
  void emptyWhenAllCorrect() {
    folderIsMine();
    QuizHistory allCorrect =
        QuizHistory.builder().userId(USER).problemSetId(1L).folderId(FOLDER_ID).build();
    allCorrect.completeQuiz(List.of(new AnswerSnapshot(1, 2, false, null)), 1, "00:10");
    historiesInFolder(allCorrect);
    when(problemSetReadService.findProblemSetsByIds(any()))
        .thenReturn(List.of(summary(1L, QuizType.MULTIPLE, 1)));
    problemsOf(1L, 1);

    assertThat(run().emptyReason()).isEqualTo(EmptyReason.ALL_CORRECT);
  }

  @Test
  @DisplayName("한 유형이 실패해도 성공한 유형은 남고 실패한 유형만 알린다 (FR-018)")
  void isolatesFailure() {
    folderIsMine();
    historiesInFolder(history(1L, 1), history(2L, 1));
    when(problemSetReadService.findProblemSetsByIds(any()))
        .thenReturn(List.of(summary(1L, QuizType.MULTIPLE, 1), summary(2L, QuizType.OX, 1)));
    problemsOf(1L, 1);
    problemsOf(2L, 1);
    when(factory.create(anyString(), anyLong(), anyString(), any(), any(), anyBoolean(), any()))
        .thenAnswer(
            invocation -> {
              if (invocation.getArgument(3) == QuizType.OX) {
                throw new IllegalStateException("저장 실패");
              }
              return new CreatedSet("PSID", "HID", QuizType.MULTIPLE, "제목", 1, false);
            });

    WrongAnswerSetResponse response = run();

    assertThat(response.createdSets())
        .singleElement()
        .satisfies(set -> assertThat(set.quizType()).isEqualTo(QuizType.MULTIPLE));
    assertThat(response.failedTypes()).containsExactly(QuizType.OX);
  }

  @Test
  @DisplayName("같은 요청이 두 번 들어오면 새로 만들지 않고 먼저 만들어진 것을 돌려준다")
  void isIdempotent() {
    folderIsMine();
    historiesInFolder(history(1L, 1));
    when(problemSetReadService.findProblemSetsByIds(any()))
        .thenReturn(List.of(summary(1L, QuizType.MULTIPLE, 1)));
    problemsOf(1L, 1);
    when(factory.create(anyString(), anyLong(), anyString(), any(), any(), anyBoolean(), any()))
        .thenThrow(new DataIntegrityViolationException("uk_problem_set_session"));
    when(problemSetReadService.findProblemSetBySessionId("wa-" + KEY + "-MULTIPLE"))
        .thenReturn(Optional.of(summary(9L, QuizType.MULTIPLE, 1)));
    when(factory.existing(eq(USER), eq(9L), eq(QuizType.MULTIPLE), anyString(), eq(1)))
        .thenReturn(new CreatedSet("PSID", "HID", QuizType.MULTIPLE, "원본", 1, false));

    WrongAnswerSetResponse response = run();

    assertThat(response.createdSets())
        .singleElement()
        .satisfies(
            set -> {
              assertThat(set.problemSetId()).isEqualTo("PSID");
            });
    assertThat(response.failedTypes()).isEmpty();
  }
}
