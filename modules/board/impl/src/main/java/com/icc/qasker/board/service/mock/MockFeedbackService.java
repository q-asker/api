package com.icc.qasker.board.service.mock;

import com.icc.qasker.board.controller.dto.PostFeedbackRequest;
import com.icc.qasker.board.entity.FeedbackBoard;
import com.icc.qasker.board.repository.FeedbackBoardRepository;
import com.icc.qasker.board.service.FeedbackService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 부하 트레이스용 feedback mock(@Profile("mock")). feedback_board write를 자기정리(save→delete)로 순증 0으로 태우고, 외부
 * 부작용인 GitHub 이슈 생성·Slack 알림은 생략한다(실 이슈가 쌓이지 않도록).
 */
@Service
@Primary
@Profile("mock")
@RequiredArgsConstructor
public class MockFeedbackService implements FeedbackService {

  private final FeedbackBoardRepository feedbackBoardRepository;

  @Override
  @Transactional
  public void postFeedback(String userId, PostFeedbackRequest request) {
    FeedbackBoard feedbackBoard =
        FeedbackBoard.builder().userId(userId).content(request.content()).build();
    feedbackBoardRepository.save(feedbackBoard);
    feedbackBoardRepository.delete(feedbackBoard);
  }
}
