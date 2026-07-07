package com.icc.qasker.ai.dto;

/**
 * 문항 품질 검증 결과(이진 판정 + 검증 불가). feedback은 미달 사유·개선 지침이며 통과 시 null이다. Result는 QualityStatus로 1:1 대응된다
 * (PASS→OK, BELOW_THRESHOLD→BELOW_THRESHOLD, UNVERIFIABLE→UNVERIFIABLE).
 */
public record QualityVerdict(Result result, String feedback) {

  public enum Result {
    PASS,
    BELOW_THRESHOLD,
    UNVERIFIABLE
  }

  public boolean passed() {
    return result == Result.PASS;
  }

  public static QualityVerdict pass() {
    return new QualityVerdict(Result.PASS, null);
  }

  public static QualityVerdict below(String feedback) {
    return new QualityVerdict(Result.BELOW_THRESHOLD, feedback);
  }

  public static QualityVerdict unverifiable(String reason) {
    return new QualityVerdict(Result.UNVERIFIABLE, reason);
  }
}
