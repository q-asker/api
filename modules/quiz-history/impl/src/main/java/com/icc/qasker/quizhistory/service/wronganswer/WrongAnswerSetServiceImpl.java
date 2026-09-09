package com.icc.qasker.quizhistory.service.wronganswer;

import com.icc.qasker.global.component.HashUtil;
import com.icc.qasker.global.error.CustomException;
import com.icc.qasker.global.error.ExceptionMessage;
import com.icc.qasker.quizhistory.WrongAnswerSetService;
import com.icc.qasker.quizhistory.dto.ferequest.CreateWrongAnswerSetRequest;
import com.icc.qasker.quizhistory.dto.feresponse.WrongAnswerSetResponse;
import com.icc.qasker.quizhistory.dto.feresponse.WrongAnswerSetResponse.CreatedSet;
import com.icc.qasker.quizhistory.dto.feresponse.WrongAnswerSetResponse.EmptyReason;
import com.icc.qasker.quizhistory.entity.AnswerSnapshotView;
import com.icc.qasker.quizhistory.entity.QuizFolder;
import com.icc.qasker.quizhistory.entity.QuizHistory;
import com.icc.qasker.quizhistory.entity.QuizHistory.QuizHistoryStatus;
import com.icc.qasker.quizhistory.repository.QuizFolderRepository;
import com.icc.qasker.quizhistory.repository.QuizHistoryRepository;
import com.icc.qasker.quizhistory.service.wronganswer.WrongAnswerCollector.AnsweredSet;
import com.icc.qasker.quizhistory.service.wronganswer.WrongAnswerCollector.TypeGroup;
import com.icc.qasker.quizset.ProblemSetReadService;
import com.icc.qasker.quizset.dto.ferequest.enums.QuizType;
import com.icc.qasker.quizset.dto.readonly.ProblemSetSummary;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/**
 * 폴더 하나에서 내가 틀린 문항을 모아 유형마다 새 문제집을 만든다.
 *
 * <p>메서드-레벨 트랜잭션을 두지 않는 것이 핵심이다 — 유형마다 {@link WrongAnswerSetFactory}가 자기 트랜잭션을 열어 하나가 실패해도 나머지가
 * 살아남고, 같은 요청이 두 번 들어와 세션 식별자가 충돌했을 때 그 예외를 여기서 잡아 이미 만들어진 것을 돌려줄 수 있다(트랜잭션 안에서 잡으면 롤백 표시로 오염돼 이어지는
 * 재조회가 실패한다).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WrongAnswerSetServiceImpl implements WrongAnswerSetService {

  private final QuizFolderRepository quizFolderRepository;
  private final QuizHistoryRepository quizHistoryRepository;
  private final ProblemSetReadService problemSetReadService;
  private final WrongAnswerSetFactory factory;
  private final HashUtil hashUtil;

  @Override
  public WrongAnswerSetResponse createFromFolder(
      String userId, CreateWrongAnswerSetRequest request, String idempotencyKey) {
    QuizFolder folder =
        quizFolderRepository
            .findByIdAndUserId(hashUtil.decode(request.folderId()), userId)
            .orElseThrow(() -> new CustomException(ExceptionMessage.FOLDER_NOT_FOUND));

    Scope scope = scope(userId, folder.getId());
    List<TypeGroup> groups = WrongAnswerCollector.collect(scope.answeredSets());

    List<CreatedSet> createdSets = new ArrayList<>();
    List<QuizType> failedTypes = new ArrayList<>();
    Instant now = Instant.now();
    for (TypeGroup group : groups) {
      createOne(userId, folder.getId(), idempotencyKey, group, now)
          .ifPresentOrElse(createdSets::add, () -> failedTypes.add(group.quizType()));
    }

    return new WrongAnswerSetResponse(
        createdSets,
        scope.excludedEssayCount(),
        scope.deletedSourceCount(),
        failedTypes,
        emptyReason(createdSets, scope));
  }

  /** 폴더 ∩ 내 기록으로 범위를 닫고, 그 안에서 수집 대상과 제외 사유를 함께 센다. */
  private Scope scope(String userId, Long folderId) {
    List<QuizHistory> histories =
        quizHistoryRepository
            .findAllByUserIdAndFolderIdAndStatusOrderByCreatedAtDesc(
                userId, folderId, QuizHistoryStatus.COMPLETED)
            .stream()
            .filter(history -> history.getAnswers() != null && !history.getAnswers().isEmpty())
            .toList();

    Map<Long, ProblemSetSummary> setById =
        problemSetReadService
            .findProblemSetsByIds(
                histories.stream().map(QuizHistory::getProblemSetId).distinct().toList())
            .stream()
            .collect(Collectors.toMap(ProblemSetSummary::id, Function.identity()));

    List<AnsweredSet> answeredSets = new ArrayList<>();
    int excludedEssayCount = 0;
    int deletedSourceCount = 0;
    for (QuizHistory history : histories) {
      ProblemSetSummary summary = setById.get(history.getProblemSetId());
      if (summary == null) {
        // 원본 문제집이 이미 지워졌다. 그 기록만 건너뛰고 나머지로 구성한다.
        deletedSourceCount++;
        continue;
      }
      if (summary.quizType() == QuizType.ESSAY) {
        // 서술형은 정오답이 규칙으로 갈리지 않는다. 틀린 수를 세면 없던 채점 기준을 만드는 셈이라 "제외된 문항 수"만 센다.
        excludedEssayCount += history.getAnswers().size();
        continue;
      }
      answeredSets.add(
          new AnsweredSet(
              history.getProblemSetId(),
              summary.quizType(),
              solvedAt(history),
              problemSetReadService.findProblemsByProblemSetId(history.getProblemSetId()),
              AnswerSnapshotView.from(history.getAnswers())));
    }
    return new Scope(answeredSets, histories.size(), excludedEssayCount, deletedSourceCount);
  }

  /** 완료 시각이 없는 기록(컬럼 도입 전 데이터)은 기록 생성 시각으로 대신한다. */
  private static Instant solvedAt(QuizHistory history) {
    return history.getCompletedAt() == null ? history.getCreatedAt() : history.getCompletedAt();
  }

  /**
   * 유형 하나를 만든다. 세션 식별자가 요청 단위로 결정론이라, 같은 요청이 두 번 들어오면 두 번째는 유니크 제약에 걸린다 — 그때는 새로 만들지 않고 먼저 만들어진 것을
   * 돌려줘 연타나 재시도가 목록을 어지럽히지 않게 한다.
   */
  private Optional<CreatedSet> createOne(
      String userId, Long folderId, String idempotencyKey, TypeGroup group, Instant now) {
    String sessionId = sessionId(idempotencyKey, group.quizType());
    try {
      return Optional.of(
          factory.create(
              userId,
              folderId,
              sessionId,
              group.quizType(),
              group.sources(),
              group.truncated(),
              now));
    } catch (DataIntegrityViolationException e) {
      log.info("[오답 모아풀기 멱등] 같은 요청 재수신 — 기존 문제집 반환 sessionId={}", sessionId);
      return problemSetReadService
          .findProblemSetBySessionId(sessionId)
          .map(
              summary ->
                  factory.existing(
                      userId,
                      summary.id(),
                      summary.quizType(),
                      summary.title(),
                      summary.totalQuizCount()));
    } catch (RuntimeException e) {
      log.error("[오답 모아풀기 실패] 유형 하나를 만들지 못했다 quizType={}", group.quizType(), e);
      return Optional.empty();
    }
  }

  private static String sessionId(String idempotencyKey, QuizType quizType) {
    return "wa-" + idempotencyKey + "-" + quizType.name();
  }

  private static EmptyReason emptyReason(List<CreatedSet> createdSets, Scope scope) {
    if (!createdSets.isEmpty()) {
      return null;
    }
    if (scope.answeredHistoryCount() == 0) {
      return EmptyReason.NO_HISTORY;
    }
    if (scope.answeredSets().isEmpty()) {
      return scope.excludedEssayCount() > 0 ? EmptyReason.ESSAY_ONLY : EmptyReason.SOURCE_DELETED;
    }
    return EmptyReason.ALL_CORRECT;
  }

  /**
   * 수집 범위를 훑은 결과.
   *
   * @param answeredHistoryCount 답안이 남아 있는 기록 수. 0이면 이 폴더에서 푼 것이 없다는 뜻이다.
   */
  private record Scope(
      List<AnsweredSet> answeredSets,
      int answeredHistoryCount,
      int excludedEssayCount,
      int deletedSourceCount) {}
}
