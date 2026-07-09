package com.icc.qasker.ai.service.quality;

import com.icc.qasker.ai.dto.AIProblem;
import com.icc.qasker.ai.dto.AISelection;
import com.icc.qasker.ai.dto.QualityVerdict;
import com.icc.qasker.ai.dto.QualityVerificationRequest;
import com.icc.qasker.ai.dto.QualityVerificationRequest.Mode;
import com.icc.qasker.ai.service.QualityVerifier;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * 생성 게이트(Pass 1)의 문항 단위 검증 진입점. AIProblem을 검증 요청으로 변환해 QualityVerifier에 위임한다. 통과/검증불가는
 * 노출(fail-open), 미달은 보류→재생성 대상이 되도록 오케스트레이터가 판단한다.
 */
@Component
public class QualityGate {

  private final QualityVerifier verifier;

  public QualityGate(QualityVerifier verifier) {
    this.verifier = verifier;
  }

  public QualityVerdict verify(
      AIProblem problem, String quizType, String language, String customInstruction) {
    return verify(problem, quizType, language, customInstruction, null);
  }

  /** cacheName이 있으면 검증기가 해당 캐시(검증 루브릭+PDF 원문)로 원문 대조 검증한다(Pass 1). */
  public QualityVerdict verify(
      AIProblem problem,
      String quizType,
      String language,
      String customInstruction,
      String cacheName) {
    return verifier.verify(
        toRequest(problem, quizType, language, customInstruction, Mode.PASS_1, cacheName));
  }

  /** Pass 1 원문 대조 검증 캐시를 세션당 1개 생성한다(실패 시 빈 값 → 캐시 없이 검증). */
  public Optional<String> createPass1Cache(String pdfUri, String quizType, String language) {
    return verifier.createPass1Cache(pdfUri, quizType, language);
  }

  /** createPass1Cache로 만든 캐시를 삭제한다(세션 종료 시). */
  public void deletePass1Cache(String cacheName) {
    verifier.deletePass1Cache(cacheName);
  }

  private QualityVerificationRequest toRequest(
      AIProblem problem,
      String quizType,
      String language,
      String customInstruction,
      Mode mode,
      String cacheName) {
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
        customInstruction,
        problem.appliedInstruction(),
        mode,
        cacheName);
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
