package com.icc.qasker.board.service;

import com.icc.qasker.board.controller.dto.PostFeedbackRequest;
import com.icc.qasker.board.entity.FeedbackBoard;
import com.icc.qasker.board.repository.FeedbackBoardRepository;
import com.icc.qasker.global.component.GithubIssueClient;
import com.icc.qasker.global.component.SlackNotifier;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class FeedbackServiceImpl implements FeedbackService {

  private static final int TITLE_SUMMARY_LIMIT = 50;
  private static final int PREVIEW_LIMIT = 100;

  private final FeedbackBoardRepository feedbackBoardRepository;
  private final GithubIssueClient githubIssueClient;
  private final SlackNotifier slackNotifier;

  @Override
  public void postFeedback(String userId, PostFeedbackRequest request) {
    FeedbackBoard feedbackBoard =
        FeedbackBoard.builder().userId(userId).content(request.content()).build();
    FeedbackBoard saved = feedbackBoardRepository.save(feedbackBoard);

    // 이슈 생성이 끝나면 그 링크로 Slack 알림을 쏜다. 이슈 생성이 비활성/실패면 링크가 null이라 Slack은 건너뛴다.
    githubIssueClient
        .asyncCreateIssue(buildIssueTitle(saved), buildIssueBody(saved))
        .thenAccept(
            issueUrl -> {
              if (issueUrl != null) {
                slackNotifier.asyncNotifyFeedback(buildSlackMessage(saved, issueUrl));
              }
            });
  }

  private String buildSlackMessage(FeedbackBoard feedback, String issueUrl) {
    return """
        :memo: *새 피드백이 등록되었습니다*

        > %s

        <%s|GitHub 이슈에서 보기>"""
        .formatted(buildPreview(feedback), issueUrl);
  }

  private String buildPreview(FeedbackBoard feedback) {
    String content = feedback.getContent() == null ? "" : feedback.getContent().strip();
    String preview = content.replaceAll("\\s+", " ");
    if (preview.length() > PREVIEW_LIMIT) {
      preview = preview.substring(0, PREVIEW_LIMIT) + "…";
    }
    return preview.isBlank() ? "(내용 없음)" : preview;
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
