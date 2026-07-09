package com.icc.qasker.quizset.service.quality;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * 해설 마크다운의 정형 구조를 정규식으로 검증한다(결정론적, AI 미사용). {@code ExplanationMarkdownBuilder}가 생성하는 구조를 기준으로 하며,
 * 규칙은 코드에 상수로 두어 형식 요구사항 변화에 대응한다(SQL 미박제). 필수 규칙 위반이 하나라도 있으면 미달(passed=false), 권고 위반은 요약에만 남긴다.
 */
@Component
public class ExplanationFormatValidator {

  private static final Pattern CORRECT_HEADER =
      Pattern.compile("(?m)^## (정답 선택지|Correct Answer)\\s*$");
  private static final Pattern WRONG_HEADER = Pattern.compile("(?m)^## (오답 선택지|Wrong Answer)\\s*$");
  private static final Pattern BLOOMS_TAG = Pattern.compile("(?m)^- \\*\\*평가 수준\\*\\*:");
  private static final Pattern BLOCKQUOTE = Pattern.compile("(?m)^> ");
  private static final int MIN_LENGTH = 100;

  /** 해설을 검증한다. 필수 규칙 위반이 없으면 passed=true. summary는 위반 요약(위반 없으면 null). */
  public Result validate(String explanation) {
    if (explanation == null || explanation.isBlank()) {
      return new Result(false, "해설이 비어 있음");
    }

    List<String> mandatory = new ArrayList<>();
    List<String> advisory = new ArrayList<>();

    if (!CORRECT_HEADER.matcher(explanation).find()) {
      mandatory.add("정답 선택지 헤더 누락(## 정답 선택지)");
    }
    if (!WRONG_HEADER.matcher(explanation).find()) {
      mandatory.add("오답 선택지 헤더 누락(## 오답 선택지)");
    }
    if (!BLOCKQUOTE.matcher(explanation).find()) {
      mandatory.add("선지 인용(blockquote '> ') 누락");
    }
    if (explanation.strip().length() < MIN_LENGTH) {
      mandatory.add("최소 분량 미달(" + MIN_LENGTH + "자)");
    }
    if (!BLOOMS_TAG.matcher(explanation).find()) {
      advisory.add("평가 수준 태그 누락(- **평가 수준**:) [권고]");
    }

    List<String> all = new ArrayList<>(mandatory);
    all.addAll(advisory);
    String summary = all.isEmpty() ? null : String.join("; ", all);
    return new Result(mandatory.isEmpty(), summary);
  }

  /** 검증 결과. passed=필수 규칙 통과 여부, summary=위반 요약(위반 없으면 null). */
  public record Result(boolean passed, String summary) {}
}
