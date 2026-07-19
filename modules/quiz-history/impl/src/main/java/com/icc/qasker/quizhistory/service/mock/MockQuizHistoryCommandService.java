package com.icc.qasker.quizhistory.service.mock;

import com.icc.qasker.global.component.HashUtil;
import com.icc.qasker.quizhistory.QuizHistoryCommandService;
import com.icc.qasker.quizhistory.dto.ferequest.InitHistoryRequest;
import com.icc.qasker.quizhistory.dto.ferequest.SaveHistoryRequest;
import com.icc.qasker.quizhistory.entity.QuizHistory;
import com.icc.qasker.quizhistory.repository.QuizHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 부하 트레이스용 quiz-history 커맨드 mock(@Profile("mock")). 모든 write를 자기정리(save→delete) throwaway로 순증 0으로
 * 태운다. problemSetId는 FK가 없는 일반 컬럼이라 실 데이터와 겹치지 않는 sentinel({@code 0})을 써 unique 충돌을 피한다.
 */
@Service
@Primary
@Profile("mock")
@RequiredArgsConstructor
public class MockQuizHistoryCommandService implements QuizHistoryCommandService {

  /** 실 (user_id, problem_set_id) 행과 겹치지 않는 throwaway 전용 problemSetId(실 값은 모두 양수). */
  private static final Long SENTINEL_PROBLEM_SET_ID = 0L;

  private final QuizHistoryRepository quizHistoryRepository;
  private final HashUtil hashUtil;

  @Override
  public String initHistory(String userId, InitHistoryRequest request) {
    return selfCleanWrite(userId, request.title());
  }

  @Override
  public String saveHistory(String userId, SaveHistoryRequest request) {
    return selfCleanWrite(userId, request.title());
  }

  @Override
  public void updateHistoryTitle(String userId, String historyId, String title) {
    selfCleanWrite(userId, title);
  }

  @Override
  public void deleteAllHistory(String userId) {
    selfCleanWrite(userId, "mock");
  }

  @Override
  public void deleteHistory(String userId, String historyId) {
    selfCleanWrite(userId, "mock");
  }

  /** throwaway QuizHistory를 save→delete하고 인코딩된 id를 돌려준다(순증 0). */
  @Transactional
  public String selfCleanWrite(String userId, String title) {
    QuizHistory history =
        QuizHistory.builder()
            .userId(userId)
            .problemSetId(SENTINEL_PROBLEM_SET_ID)
            .title(title)
            .build();
    quizHistoryRepository.save(history);
    String encodedId = hashUtil.encode(history.getId());
    quizHistoryRepository.delete(history);
    return encodedId;
  }
}
