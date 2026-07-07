package com.icc.qasker.ai.service.quality;

import com.icc.qasker.ai.dto.AIProblem;
import com.icc.qasker.ai.dto.AISelection;
import com.icc.qasker.ai.dto.QualityVerdict;
import com.icc.qasker.ai.dto.QualityVerificationRequest;
import com.icc.qasker.ai.dto.QualityVerificationRequest.Mode;
import com.icc.qasker.ai.service.QualityVerifier;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 생성 게이트(Pass 1)의 문항 단위 검증 진입점. AIProblem을 검증 요청으로 변환해 QualityVerifier에 위임하고, 판정 결과를 저장용 품질 상태 문자열로
 * 매핑한다. 통과/검증불가는 노출(fail-open), 미달은 보류→재생성 대상이 되도록 오케스트레이터가 판단한다.
 */
@Component
public class QualityGate {

  private final QualityVerifier verifier;

  public QualityGate(QualityVerifier verifier) {
    this.verifier = verifier;
  }

  public QualityVerdict verify(
      AIProblem problem, String quizType, String language, String customInstruction) {
    return verifier.verify(toRequest(problem, quizType, language, customInstruction, Mode.PASS_1));
  }

  /** QualityVerdict를 저장용 QualityStatus 이름으로 변환한다(PASS→OK, 그 외는 결과명 그대로). */
  public static String toStatus(QualityVerdict verdict) {
    return verdict.result() == QualityVerdict.Result.PASS ? "OK" : verdict.result().name();
  }

  private QualityVerificationRequest toRequest(
      AIProblem problem, String quizType, String language, String customInstruction, Mode mode) {
    List<QualityVerificationRequest.Selection> selections =
        problem.selections() == null
            ? List.of()
            : problem.selections().stream()
                .map(s -> new QualityVerificationRequest.Selection(s.content(), s.correct()))
                .toList();
    return new QualityVerificationRequest(
        quizType,
        language,
        problem.content(),
        selections,
        extractModelAnswer(quizType, problem.selections()),
        problem.rationale(),
        customInstruction,
        problem.appliedInstruction(),
        mode);
  }

  /** ESSAY는 정답(correct=true) 선지 내용이 모범답안이다. 객관형은 모범답안 개념이 없어 null. */
  private static String extractModelAnswer(String quizType, List<AISelection> selections) {
    if (!"ESSAY".equals(quizType) || selections == null) {
      return null;
    }
    return selections.stream()
        .filter(AISelection::correct)
        .map(AISelection::content)
        .findFirst()
        .orElse(null);
  }
}
