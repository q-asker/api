package com.icc.qasker.board.service;

import com.icc.qasker.board.controller.dto.PostFeedbackRequest;
import com.icc.qasker.board.entity.FeedbackBoard;
import com.icc.qasker.board.repository.FeedbackBoardRepository;
import com.icc.qasker.global.component.GithubIssueClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class FeedbackService {

  private static final int TITLE_SUMMARY_LIMIT = 50;

  private final FeedbackBoardRepository feedbackBoardRepository;
  private final GithubIssueClient githubIssueClient;

  public void postFeedback(String userId, PostFeedbackRequest request) {
    FeedbackBoard feedbackBoard =
        FeedbackBoard.builder().userId(userId).content(request.content()).build();
    FeedbackBoard saved = feedbackBoardRepository.save(feedbackBoard);

    githubIssueClient.asyncCreateIssue(buildIssueTitle(saved), buildIssueBody(saved));
  }

  private String buildIssueTitle(FeedbackBoard feedback) {
    String content = feedback.getContent() == null ? "" : feedback.getContent().strip();
    String summary = content.replaceAll("\\s+", " ");
    if (summary.length() > TITLE_SUMMARY_LIMIT) {
      summary = summary.substring(0, TITLE_SUMMARY_LIMIT) + "…";
    }
    if (summary.isBlank()) {
      summary = "(내용 없음)";
    }
    return "[피드백] %s".formatted(summary);
  }

  private String buildIssueBody(FeedbackBoard feedback) {
    return """
        > 사용자 피드백이 자동 등록되었습니다.

        - **작성 시각**: %s

        ## 원문

        %s

        ---
        <sub>이 이슈는 `feedback_board` 저장 시 백엔드가 자동 생성했습니다. 팀 멤버가 `/review` 코멘트를 남기면 \
        Claude가 프론트엔드/백엔드 작업을 분석해 댓글로 답합니다.</sub>
        """
        .formatted(feedback.getCreatedAt(), feedback.getContent());
  }
}
