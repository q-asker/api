package com.icc.qasker.board.service;

import com.icc.qasker.board.controller.dto.PostFeedbackRequest;
import com.icc.qasker.board.entity.FeedbackBoard;
import com.icc.qasker.board.repository.FeedbackBoardRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class FeedbackService {

  private final FeedbackBoardRepository feedbackBoardRepository;

  public void postFeedback(String userId, PostFeedbackRequest request) {
    FeedbackBoard feedbackBoard =
        FeedbackBoard.builder().userId(userId).content(request.content()).build();
    feedbackBoardRepository.save(feedbackBoard);
  }
}
