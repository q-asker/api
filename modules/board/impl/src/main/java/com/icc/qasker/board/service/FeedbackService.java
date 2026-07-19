package com.icc.qasker.board.service;

import com.icc.qasker.board.controller.dto.PostFeedbackRequest;

/**
 * 피드백 등록. mock 프로파일에서 자기정리(save→delete) 구현({@code MockFeedbackService})으로 교체돼, 실 GitHub 이슈·Slack 알림
 * 없이 순증 0으로 트레이스한다.
 */
public interface FeedbackService {

  void postFeedback(String userId, PostFeedbackRequest request);
}
