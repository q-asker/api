package com.icc.qasker.quizhistory.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.icc.qasker.ai.dto.EssayGradingResult;
import com.icc.qasker.global.component.HashUtil;
import com.icc.qasker.global.error.CustomException;
import com.icc.qasker.global.error.ExceptionMessage;
import com.icc.qasker.quizhistory.dto.ferequest.HistoryScope;
import com.icc.qasker.quizhistory.dto.feresponse.EssayHistoryDetailResponse;
import com.icc.qasker.quizhistory.dto.feresponse.EssayHistoryDetailResponse.EssayProblemWithGrade;
import com.icc.qasker.quizhistory.dto.feresponse.HistoryDetailResponse;
import com.icc.qasker.quizhistory.dto.feresponse.HistoryPageResponse;
import com.icc.qasker.quizhistory.dto.feresponse.ProblemWithAnswer;
import com.icc.qasker.quizhistory.entity.AnswerSnapshot;
import com.icc.qasker.quizhistory.entity.EssayGradeLog;
import com.icc.qasker.quizhistory.entity.QuizHistory;
import com.icc.qasker.quizhistory.mapper.QuizHistoryMapper;
import com.icc.qasker.quizhistory.repository.EssayGradeLogRepository;
import com.icc.qasker.quizhistory.repository.QuizFolderRepository;
import com.icc.qasker.quizhistory.repository.QuizHistoryRepository;
import com.icc.qasker.quizset.ProblemSetReadService;
import com.icc.qasker.quizset.dto.ferequest.enums.QuizType;
import com.icc.qasker.quizset.dto.readonly.ProblemDetail;
import com.icc.qasker.quizset.dto.readonly.ProblemSetSummary;
import com.icc.qasker.quizset.dto.readonly.SelectionDetail;
import java.lang.reflect.Constructor;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

/**
 * QuizHistoryQueryServiceImpl 회귀 세이프티 넷. 상세/서술형 상세 조회의 매핑·정오답 판정·소유권 검증 동작을 고정한다. 매핑 로직이 서비스에서
 * QuizHistoryMapper로 이관되더라도 공개 API 출력이 동일해야 한다.
 */
@ExtendWith(MockitoExtension.class)
class QuizHistoryQueryServiceImplTest {

  @Mock private QuizHistoryRepository quizHistoryRepository;
  @Mock private QuizFolderRepository quizFolderRepository;
  @Mock private EssayGradeLogRepository essayGradeLogRepository;
  @Mock private ProblemSetReadService problemSetReadService;
  @Mock private HashUtil hashUtil;

  private QuizHistoryQueryServiceImpl service;

  @BeforeEach
  void setUp() throws Exception {
    // QuizHistoryMapper는 private 생성자를 가지므로 리플렉션으로 실 인스턴스를 만들어 실제 매핑 로직을 검증한다.
    Constructor<QuizHistoryMapper> ctor =
        QuizHistoryMapper.class.getDeclaredConstructor(HashUtil.class);
    ctor.setAccessible(true);
    QuizHistoryMapper mapper = ctor.newInstance(hashUtil);

    service =
        new QuizHistoryQueryServiceImpl(
            quizHistoryRepository,
            quizFolderRepository,
            essayGradeLogRepository,
            problemSetReadService,
            hashUtil,
            mapper);

    lenient().when(hashUtil.encode(anyLong())).thenReturn("ENC");
  }

  private ProblemSetSummary summary(long id, QuizType type, int count) {
    return new ProblemSetSummary(id, type, count, "title-" + id, null);
  }

  @Test
  @DisplayName("getHistoryDetail: 정답/오답 판정 + inReview/textAnswer 매핑 + 스냅샷 없는 문항 기본값")
  void getHistoryDetail_mapsAnswersAndCorrectness() {
    String userId = "user1";
    QuizHistory history =
        QuizHistory.builder()
            .id(10L)
            .userId(userId)
            .problemSetId(100L)
            .title("t")
            .score(50)
            .totalTime("60")
            .answers(List.of(new AnswerSnapshot(1, 2, true, "myText")))
            .build();
    when(hashUtil.decode("h10")).thenReturn(10L);
    when(quizHistoryRepository.findById(10L)).thenReturn(Optional.of(history));
    when(problemSetReadService.findProblemSetById(100L))
        .thenReturn(Optional.of(summary(100L, QuizType.MULTIPLE, 2)));

    // P1: 정답 인덱스 2 (b), 사용자 답 2 → 정답. P2: 정답 인덱스 1, 스냅샷 없음 → 기본값 0 → 오답
    ProblemDetail p1 =
        new ProblemDetail(
            1,
            "problem-1",
            List.of(
                new SelectionDetail("a", false),
                new SelectionDetail("b", true),
                new SelectionDetail("c", false)),
            "expl-1");
    ProblemDetail p2 =
        new ProblemDetail(
            2,
            "problem-2",
            List.of(new SelectionDetail("x", true), new SelectionDetail("y", false)),
            "expl-2");
    when(problemSetReadService.findProblemsByProblemSetId(100L)).thenReturn(List.of(p1, p2));

    HistoryDetailResponse response = service.getHistoryDetail(userId, "h10");

    assertThat(response.problems()).hasSize(2);
    ProblemWithAnswer first = response.problems().get(0);
    assertThat(first.number()).isEqualTo(1);
    assertThat(first.userAnswer()).isEqualTo(2);
    assertThat(first.correct()).isTrue();
    assertThat(first.inReview()).isTrue();
    assertThat(first.textAnswer()).isEqualTo("myText");
    assertThat(first.selections()).hasSize(3);
    assertThat(first.selections().get(0).id()).isEqualTo(1);
    assertThat(first.selections().get(1).id()).isEqualTo(2);
    assertThat(first.selections().get(1).correct()).isTrue();

    ProblemWithAnswer second = response.problems().get(1);
    assertThat(second.userAnswer()).isEqualTo(0);
    assertThat(second.correct()).isFalse();
    assertThat(second.inReview()).isFalse();
    assertThat(second.textAnswer()).isNull();
  }

  @Test
  @DisplayName("getHistoryDetail: 정답 없는 selections는 correctIndex -1 → 어떤 답도 오답")
  void getHistoryDetail_noCorrectSelection_alwaysIncorrect() {
    String userId = "user1";
    QuizHistory history =
        QuizHistory.builder()
            .id(11L)
            .userId(userId)
            .problemSetId(101L)
            .score(0)
            .answers(List.of(new AnswerSnapshot(1, 1, false, null)))
            .build();
    when(hashUtil.decode("h11")).thenReturn(11L);
    when(quizHistoryRepository.findById(11L)).thenReturn(Optional.of(history));
    when(problemSetReadService.findProblemSetById(101L))
        .thenReturn(Optional.of(summary(101L, QuizType.MULTIPLE, 1)));
    ProblemDetail p1 =
        new ProblemDetail(
            1,
            "problem-1",
            List.of(new SelectionDetail("a", false), new SelectionDetail("b", false)),
            "expl");
    when(problemSetReadService.findProblemsByProblemSetId(101L)).thenReturn(List.of(p1));

    HistoryDetailResponse response = service.getHistoryDetail(userId, "h11");

    assertThat(response.problems().get(0).correct()).isFalse();
  }

  @Test
  @DisplayName("getEssayHistoryDetail: 최신 채점 로그 매핑 + 로그 없는 문항 gradeResult null")
  void getEssayHistoryDetail_mapsGradeLogs() {
    String userId = "user1";
    QuizHistory history =
        QuizHistory.builder()
            .id(20L)
            .userId(userId)
            .problemSetId(200L)
            .totalTime("120")
            .answers(
                List.of(
                    new AnswerSnapshot(1, 0, true, "answer-1"),
                    new AnswerSnapshot(2, 0, false, "answer-2")))
            .build();
    when(hashUtil.decode("h20")).thenReturn(20L);
    when(quizHistoryRepository.findById(20L)).thenReturn(Optional.of(history));
    when(problemSetReadService.findProblemSetById(200L))
        .thenReturn(Optional.of(summary(200L, QuizType.ESSAY, 2)));
    ProblemDetail p1 =
        new ProblemDetail(1, "essay-1", List.of(new SelectionDetail("model-1", true)), "rubric-1");
    ProblemDetail p2 =
        new ProblemDetail(2, "essay-2", List.of(new SelectionDetail("model-2", true)), "rubric-2");
    when(problemSetReadService.findProblemsByProblemSetId(200L)).thenReturn(List.of(p1, p2));

    EssayGradeLog log =
        EssayGradeLog.builder()
            .userId(userId)
            .problemSetId(200L)
            .problemNumber(1)
            .question("essay-1")
            .studentAnswer("answer-1")
            .attemptCount(1)
            .totalScore(8)
            .maxScore(10)
            .overallFeedback("good")
            .elementScores(
                List.of(new EssayGradingResult.ElementScore("정확성", 5, 4, "상", "well done")))
            .build();
    when(essayGradeLogRepository.findLatestByUserIdAndProblemSetId(userId, 200L))
        .thenReturn(List.of(log));

    EssayHistoryDetailResponse response = service.getEssayHistoryDetail(userId, "h20");

    assertThat(response.problems()).hasSize(2);
    EssayProblemWithGrade first = response.problems().get(0);
    assertThat(first.textAnswer()).isEqualTo("answer-1");
    assertThat(first.inReview()).isTrue();
    assertThat(first.selections()).hasSize(1);
    assertThat(first.selections().get(0).id()).isEqualTo(1);
    assertThat(first.gradeResult()).isNotNull();
    assertThat(first.gradeResult().totalScore()).isEqualTo(8);
    assertThat(first.gradeResult().maxScore()).isEqualTo(10);
    assertThat(first.gradeResult().overallFeedback()).isEqualTo("good");
    assertThat(first.gradeResult().elementScores()).hasSize(1);
    assertThat(first.gradeResult().elementScores().get(0).element()).isEqualTo("정확성");
    assertThat(first.gradeResult().elementScores().get(0).earnedPoints()).isEqualTo(4);

    EssayProblemWithGrade second = response.problems().get(1);
    assertThat(second.gradeResult()).isNull();
    assertThat(second.inReview()).isFalse();
  }

  @Test
  @DisplayName("getHistoryList: 문제세트가 삭제되어 조회되지 않는 히스토리는 결과에서 제외")
  void getHistoryList_excludesHistoryWithMissingProblemSet() {
    String userId = "user1";
    QuizHistory h1 =
        QuizHistory.builder().id(1L).userId(userId).problemSetId(100L).title("kept").build();
    QuizHistory h2 =
        QuizHistory.builder().id(2L).userId(userId).problemSetId(200L).title("dropped").build();
    Pageable pageable = Pageable.ofSize(10).withPage(0);
    Page<QuizHistory> page = new PageImpl<>(List.of(h1, h2), pageable, 2);
    when(quizHistoryRepository.findAllByUserIdOrderByCreatedAtDesc(eq(userId), eq(pageable)))
        .thenReturn(page);
    // 100번 문제세트만 살아있고 200번은 삭제됨
    when(problemSetReadService.findProblemSetsByIds(List.of(100L, 200L)))
        .thenReturn(List.of(summary(100L, QuizType.MULTIPLE, 3)));

    HistoryPageResponse response = service.getHistoryList(userId, HistoryScope.ALL, null, 0, 10);

    assertThat(response.content()).hasSize(1);
    assertThat(response.content().get(0).title()).isEqualTo("kept");
  }

  @Test
  @DisplayName("getHistoryList FOLDER: 특정 폴더 쿼리로 조회하고 folderId/folderName을 매핑한다")
  void getHistoryList_folderScope_mapsFolderName() {
    String userId = "user1";
    QuizHistory h =
        QuizHistory.builder()
            .id(1L)
            .userId(userId)
            .problemSetId(100L)
            .title("in-folder")
            .folderId(5L)
            .build();
    Pageable pageable = Pageable.ofSize(10).withPage(0);
    Page<QuizHistory> page = new PageImpl<>(List.of(h), pageable, 1);
    when(hashUtil.decode("f5")).thenReturn(5L);
    when(quizHistoryRepository.findAllByUserIdAndFolderIdOrderByCreatedAtDesc(
            eq(userId), eq(5L), eq(pageable)))
        .thenReturn(page);
    when(problemSetReadService.findProblemSetsByIds(List.of(100L)))
        .thenReturn(List.of(summary(100L, QuizType.MULTIPLE, 3)));
    when(quizFolderRepository.findAllById(List.of(5L)))
        .thenReturn(
            List.of(
                com.icc.qasker.quizhistory.entity.QuizFolder.builder().id(5L).name("수학").build()));

    HistoryPageResponse response = service.getHistoryList(userId, HistoryScope.FOLDER, "f5", 0, 10);

    assertThat(response.content()).hasSize(1);
    assertThat(response.content().get(0).folderId()).isEqualTo("ENC");
    assertThat(response.content().get(0).folderName()).isEqualTo("수학");
  }

  @Test
  @DisplayName("getHistoryList UNCLASSIFIED: 미분류(folderId IS NULL) 쿼리로 조회한다")
  void getHistoryList_unclassifiedScope_usesNullFolderQuery() {
    String userId = "user1";
    QuizHistory h =
        QuizHistory.builder().id(1L).userId(userId).problemSetId(100L).title("none").build();
    Pageable pageable = Pageable.ofSize(10).withPage(0);
    Page<QuizHistory> page = new PageImpl<>(List.of(h), pageable, 1);
    when(quizHistoryRepository.findAllByUserIdAndFolderIdIsNullOrderByCreatedAtDesc(
            eq(userId), eq(pageable)))
        .thenReturn(page);
    when(problemSetReadService.findProblemSetsByIds(List.of(100L)))
        .thenReturn(List.of(summary(100L, QuizType.MULTIPLE, 3)));

    HistoryPageResponse response =
        service.getHistoryList(userId, HistoryScope.UNCLASSIFIED, null, 0, 10);

    assertThat(response.content()).hasSize(1);
    assertThat(response.content().get(0).folderId()).isNull();
    assertThat(response.content().get(0).folderName()).isNull();
  }

  @Test
  @DisplayName("getHistoryList FOLDER: folderId 누락 시 400(BAD_REQUEST)")
  void getHistoryList_folderScope_missingFolderId_throwsBadRequest() {
    assertThatThrownBy(() -> service.getHistoryList("user1", HistoryScope.FOLDER, null, 0, 10))
        .isInstanceOf(CustomException.class)
        .extracting(e -> ((CustomException) e).getHttpStatus())
        .isEqualTo(org.springframework.http.HttpStatus.BAD_REQUEST);
  }

  @Test
  @DisplayName("getHistoryDetail: 타 사용자의 히스토리는 QUIZ_HISTORY_NOT_FOUND")
  void getHistoryDetail_otherUser_throwsNotFound() {
    QuizHistory history = QuizHistory.builder().id(30L).userId("owner").problemSetId(300L).build();
    when(hashUtil.decode("h30")).thenReturn(30L);
    when(quizHistoryRepository.findById(30L)).thenReturn(Optional.of(history));

    assertThatThrownBy(() -> service.getHistoryDetail("intruder", "h30"))
        .isInstanceOf(CustomException.class)
        .extracting(e -> ((CustomException) e).getMessage())
        .isEqualTo(ExceptionMessage.QUIZ_HISTORY_NOT_FOUND.getMessage());
  }
}
