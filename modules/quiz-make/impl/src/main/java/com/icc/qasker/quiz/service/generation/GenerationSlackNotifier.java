package com.icc.qasker.quiz.service.generation;

import com.icc.qasker.global.component.HashUtil;
import com.icc.qasker.global.component.SlackNotifier;
import com.icc.qasker.global.properties.QAskerProperties;
import com.icc.qasker.quiz.dto.ferequest.enums.QuizType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class GenerationSlackNotifier {

  private final SlackNotifier slackNotifier;
  private final HashUtil hashUtil;
  private final QAskerProperties qAskerProperties;

  public void notifySuccess(Long problemSetId, QuizType quizType, long generatedCount) {
    String encodedId = hashUtil.encode(problemSetId);
    String quizUrl = buildQuizUrl(encodedId);
    slackNotifier.asyncNotifyText(
        """
        ✅ [퀴즈 생성 완료 알림]
        ProblemSetId: <%s|%s>
        퀴즈 타입: %s
        문제 수: %d
        """
            .formatted(quizUrl, encodedId, quizType, generatedCount));
  }

  public void notifyPartialSuccess(
      Long problemSetId, QuizType quizType, long generatedCount, long quizCount) {
    String encodedId = hashUtil.encode(problemSetId);
    String quizUrl = buildQuizUrl(encodedId);
    slackNotifier.asyncNotifyText(
        """
        ⚠️ [퀴즈 생성 부분 완료]
        ProblemSetId: <%s|%s>
        퀴즈 타입: %s
        생성된 문제 수: %d개 / 총 문제 수: %d개
        """
            .formatted(quizUrl, encodedId, quizType, generatedCount, quizCount));
  }

  public void notifyError(Long problemSetId, String errorMessage) {
    String encodedId = hashUtil.encode(problemSetId);
    String quizUrl = buildQuizUrl(encodedId);
    slackNotifier.asyncNotifyText(
        """
        ❌ [퀴즈 생성 실패]
        ProblemSetId: <%s|%s>
        원인: %s
        """
            .formatted(quizUrl, encodedId, errorMessage));
  }

  private String buildQuizUrl(String encodedId) {
    return qAskerProperties.getFrontendDeployUrl() + "/quiz/" + encodedId;
  }
}
