package com.icc.qasker.quizset.grading;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * REAL_BLANK 표기 정규화 (contract.md §7.3). 프론트 채점(blank-scoring.ts)과 <b>문자 단위로 동일</b>해야 한다 — 백엔드는 인정 답
 * 목록 sanitize(FR-002a 함정 겹침 판정)에만 사용하고, 실제 채점 판정은 프론트가 수행한다.
 *
 * <p>파이프라인: NFKC → 소문자화(Locale.ROOT) → 공백 전부 제거 → {@code \p{P}} 문장부호 제거. 심볼({@code \p{S}}: + # = <
 * > 등)은 보존한다 — {@code C++}↔{@code C}, {@code C#}↔{@code C}를 과관용으로 뭉개지 않기 위함(SC-002).
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class BlankAnswerNormalizer {

  // (?U) = UNICODE_CHARACTER_CLASS: 순수 \s(ASCII)가 아니라 JS \s(유니코드 공백 U+2028·U+FEFF 등)와 동일 집합을 매칭.
  private static final Pattern WHITESPACE = Pattern.compile("(?U)\\s+");
  // \p{P}(문장부호) 제거 — 단 '#'은 예외로 보존한다. '#'은 유니코드 Po(문장부호)라 순수 \p{P}에 걸리지만, contract.md §7.3의
  // 보존 목록(+ # = < >)에 명시돼 있고 C#↔C 구분에 필요하다(SC-002). 나머지 보존 대상(+ = < >)은 \p{S}라 애초에 제거되지 않는다.
  // JS 파리티: /[\p{P}--[#]]/gv (또는 replace(/\p{P}/gu, m => m === '#' ? '#' : '')).
  private static final Pattern PUNCTUATION = Pattern.compile("[\\p{P}&&[^#]]+");

  public static String normalize(String s) {
    if (s == null) {
      return "";
    }
    String n = Normalizer.normalize(s, Normalizer.Form.NFKC);
    n = n.toLowerCase(Locale.ROOT);
    n = WHITESPACE.matcher(n).replaceAll("");
    n = PUNCTUATION.matcher(n).replaceAll("");
    return n;
  }
}
