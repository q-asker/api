package com.icc.qasker.quizmake.service.generation;

import com.icc.qasker.global.component.HashUtil;
import com.icc.qasker.global.component.SlackNotifier;
import com.icc.qasker.global.properties.QAskerProperties;
import com.icc.qasker.quizset.dto.ferequest.enums.QuizType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class GenerationSlackNotifier {

  private final SlackNotifier slackNotifier;
  private final HashUtil hashUtil;
  private final QAskerProperties qAskerProperties;

  public void notifySuccess(
      Long problemSetId, QuizType quizType, long generatedCount, long ttfqMs) {
    String encodedId = hashUtil.encode(problemSetId);
    String quizUrl = buildQuizUrl(encodedId);
    slackNotifier.asyncNotifyText(
        """
        ✅ [퀴즈 생성 완료 알림]
        ProblemSetId: <%s|%s>
        퀴즈 타입: %s
        문제 수: %d
        TTFQ: %s
        """
            .formatted(quizUrl, encodedId, quizType, generatedCount, formatTtfq(ttfqMs)));
  }

  public void notifyPartialSuccess(
      Long problemSetId, QuizType quizType, long generatedCount, long quizCount, long ttfqMs) {
    String encodedId = hashUtil.encode(problemSetId);
    String quizUrl = buildQuizUrl(encodedId);
    slackNotifier.asyncNotifyText(
        """
        ⚠️ [퀴즈 생성 부분 완료]
        ProblemSetId: <%s|%s>
        퀴즈 타입: %s
        생성된 문제 수: %d개 / 총 문제 수: %d개
        TTFQ: %s
        """
            .formatted(
                quizUrl, encodedId, quizType, generatedCount, quizCount, formatTtfq(ttfqMs)));
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

  private String formatTtfq(long ttfqMs) {
    if (ttfqMs < 0) {
      return "N/A";
    }
    return ttfqMs >= 1000 ? String.format("%.1f초", ttfqMs / 1000.0) : ttfqMs + "ms";
  }
}
