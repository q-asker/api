package com.icc.qasker.quizhistory.service.wronganswer;

import com.icc.qasker.global.component.HashUtil;
import com.icc.qasker.global.quiz.QuizType;
import com.icc.qasker.quizhistory.dto.feresponse.WrongAnswerSetResponse.CreatedSet;
import com.icc.qasker.quizhistory.entity.QuizHistory;
import com.icc.qasker.quizhistory.repository.QuizHistoryRepository;
import com.icc.qasker.quizset.WrongAnswerSetCreationService;
import com.icc.qasker.quizset.dto.WrongAnswerSetCreation;
import com.icc.qasker.quizset.dto.readonly.ProblemLineage;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 유형 하나 몫의 문제집을 만든다. <b>유형마다 별도의 트랜잭션</b>이라, 한 유형이 실패해도 이미 만들어진 다른 유형은 그대로 남고 실패한 유형은 반쯤 만들어진 채 남지
 * 않는다.
 *
 * <p>문제집과 함께 풀이 기록(미완료)을 출처 폴더에 만든다. 이 레포에서 폴더는 문제집이 아니라 풀이 기록에 붙고 사용자 목록도 기록을 훑으므로, 기록이 없으면 만들어진
 * 문제집이 목록에도 폴더에도 나타나지 않는다.
 */
@Component
@RequiredArgsConstructor
public class WrongAnswerSetFactory {

  // 사용자에게 보이는 제목이라 서비스 사용자의 시간대로 찍는다(서버 시간대에 좌우되지 않게 고정).
  private static final ZoneId TITLE_ZONE = ZoneId.of("Asia/Seoul");
  private static final DateTimeFormatter TITLE_TIME = DateTimeFormatter.ofPattern("MM/dd HH:mm");
  private static final String TITLE_PREFIX = "오답 모음";

  private final WrongAnswerSetCreationService creationService;
  private final QuizHistoryRepository quizHistoryRepository;
  private final HashUtil hashUtil;

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public CreatedSet create(
      String userId,
      Long folderId,
      String sessionId,
      QuizType quizType,
      List<ProblemLineage> sources,
      boolean truncated,
      Instant now) {
    String title = title(quizType, now);
    Long problemSetId =
        creationService.create(
            new WrongAnswerSetCreation(userId, sessionId, title, quizType, folderId, sources));
    QuizHistory history = saveHistory(userId, folderId, problemSetId, title);
    return new CreatedSet(
        hashUtil.encode(problemSetId),
        hashUtil.encode(history.getId()),
        quizType,
        title,
        sources.size(),
        truncated);
  }

  /** 이미 만들어져 있던 세트를 그대로 돌려준다(같은 요청이 두 번 들어온 경우). 기록 행도 이미 있으므로 찾아 쓴다. */
  @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
  public CreatedSet existing(
      String userId, Long problemSetId, QuizType quizType, String title, int questionCount) {
    String historyId =
        quizHistoryRepository
            .findByUserIdAndProblemSetId(userId, problemSetId)
            .map(h -> hashUtil.encode(h.getId()))
            .orElse(null);
    return new CreatedSet(
        hashUtil.encode(problemSetId),
        historyId,
        quizType,
        title,
        questionCount,
        // 이미 만들어진 세트는 몇 문항이 잘렸는지 남겨 두지 않는다. 안내는 처음 만든 응답에서 이미 전달됐다.
        false);
  }

  private QuizHistory saveHistory(String userId, Long folderId, Long problemSetId, String title) {
    return quizHistoryRepository.save(
        QuizHistory.builder()
            .userId(userId)
            .problemSetId(problemSetId)
            .folderId(folderId)
            .title(title)
            .build());
  }

  private String title(QuizType quizType, Instant now) {
    return TITLE_PREFIX
        + " · "
        + QuizTypeLabel.of(quizType)
        + " · "
        + TITLE_TIME.format(now.atZone(TITLE_ZONE));
  }
}
