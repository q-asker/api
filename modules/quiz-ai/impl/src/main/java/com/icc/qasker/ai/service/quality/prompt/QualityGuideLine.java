package com.icc.qasker.ai.service.quality.prompt;

import com.icc.qasker.ai.dto.QualityVerificationRequest.Mode;
import com.icc.qasker.global.quiz.QuizType;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * 검증관 시스템 프롬프트. 역할·판정 규칙·검증 항목·실격 사유로 이루어지며, 유형과 모드(Pass 1/2)에 따라 구성이 달라진다.
 *
 * <p>검증관에게 생성 지침(GuideLine)을 주지 않는 것이 이 프롬프트의 핵심 결정이다 — 주면 검증이 "생성 지시를 지켰는가" 검사로 수렴해 독립 판정이 되지 않는다.
 * 대신 문면만 보고 셀 수 있는 실격 사유를 준다.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class QualityGuideLine {

  /**
   * 검증관 시스템 프롬프트를 만든다. pdfGrounded=true면 첨부 PDF 원문과 직접 대조하도록 지시한다(Pass 1 캐시 검증 — 환각·출처 이탈 탐지).
   * false면 PDF 없이 문항 자체로 판정한다(폴백·Pass 2 현행). 어느 경우든 문항 자체(+첨부 PDF 원문)만 보고 판정한다.
   */
  public static String build(
      QuizType quizType,
      Mode mode,
      boolean pdfGrounded,
      Map<QualityCriterion, Strictness> criteria) {
    String grounding =
        pdfGrounded
            ? """
            # 원문 대조 (중요)
            첨부된 PDF가 이 문항의 출처 원문이다. 정답 근거(answer-grounded-in-source)와 범위 밖 지식 여부(no-outside-knowledge)를
            반드시 **첨부 PDF 원문과 직접 대조**해 판정하라. 원문에 없는 사실로만 정답이 성립하면 미달이다.

            """
            : "";

    String pass2 =
        mode == Mode.PASS_2
            ? """

            # 추가 심층 검증 (Pass 2 — 더 엄격)
            - 세트 내 문항 다양성·중복 회피
            - 해설-문항 정합성
            - 인지적 깊이·지름길(shortcut) 풀이 방지
            - 출처 충실성 심화(정답이 강의노트 원문 범위 내 근거로 성립하는가)
            - 재생성 반영 검증: 입력의 priorRoundFeedback(이전 라운드 미달 사유)이 비어있지 않으면, 현재 문항은 그 사유를 고치려고 재생성된 개선본이다. 각 지적이 실제로 해소됐는지 판정하고, feedback에 '어떤 지적이 어떻게 반영/미반영됐는지'를 항목별로 구체적으로 서술한다. 미해소·부분해소가 있으면 미달로 본다.
            """
            : "";

    return """
        # 역할
        당신은 AI가 생성한 %s 문항의 품질을 검수하는 엄격한 검증관이다.
        아래 '검증 항목'을 기준으로 문항을 점검하고, 이진 판정(통과/미달)을 내린다.

        """
            .formatted(quizType.name())
        + grounding
        + """
        # 판정 규칙
        - 필수 항목 중 **하나라도 실패하면 미달(passed=false)**. 모든 필수 항목을 통과해야 통과(passed=true).
        - 가중 점수 합산이 아니라 치명 항목 이진 판정이다.
        - 검증 항목의 엄격도: **strict = 경미한 위반도 미달**, **normal = 명백한 위반만 미달**(애매하면 통과).
        - 미달 시 feedback에 실패 항목과 개선 방향을 구체적으로 적는다. 통과 시 feedback은 빈 문자열.

        # 필수 항목 (전 유형 공통)
        1. 실격 사유 부재: 아래 '실격 사유'에 해당하는 것이 하나도 없는가.
        2. 사용자 지시 반영: customInstruction이 appliedInstruction/문항에 정확히 반영됐는가(지시가 없으면 통과).

        # 검증 항목 및 엄격도 (운영자 설정)
        """
        + buildCriteria(quizType, criteria)
        + pass2
        + """

        # 실격 사유 (하나라도 해당하면 미달 — 선지·질문문의 문면만 보고 판정한다)
        """
        + DISQUALIFIERS.get(quizType);
  }

  /**
   * 유형별 실격 사유. 생성 GuideLine을 그대로 붙이던 자리를 대체한다.
   *
   * <p>검증관에게 "무엇을 만들어라"(생성 지침)를 주면 검증이 생성 지시 준수 검사로 수렴해 독립 판정이 되지 않는다. 여기에는 **관찰 가능한 실격 사유만** 둔다 —
   * 3인 평가에서 확정된 감점 패턴은 전부 문면을 세면 판정되는 형태였다.
   */
  private static final Map<QuizType, String> DISQUALIFIERS =
      new EnumMap<>(
          Map.of(
              QuizType.MULTIPLE,
                  """
              1. 정답이 오답들보다 눈에 띄게 길다.
              2. 절대어(항상·모든·절대로·반드시·오직)가 오답에만 쓰이고 정답만 조건부로 서술된다.
                 — 정답과 오답 양쪽에 고르게 나타나면 정답 단서가 아니므로 해당하지 않는다.
              3. 정답 선지가 강의노트 문장을 그대로 옮겼다(축자 복사). 문자열 조회만으로 정답이 결정된다.
              4. 질문문이 거짓 전제를 단언한다(예: "오류가 하나 있다"고 못박았는데 정답은 "오류가 없다").
              5. 아무도 그렇게 믿지 않을 오답이 있다 — 그 선지를 고른 사람의 잘못된 지식 상태를
                 한 문장으로 적을 수 없으면 해당한다.
              6. "X 하는 대신 Y를 감수한다" 형식에서 오답의 Y만 실질 손해이고 정답의 Y는 손해가 아니거나 이득이다.
              7. 질문문의 특징적 단어가 정답 선지에만 반복된다.
              """,
              QuizType.OX,
                  """
              1. 절대어(항상·모든·절대로·언제나)가 있고 정답이 거짓이거나, 완화어(대개·보통·때때로)가 있고 정답이 참이다
                 — 절대어의 존재 자체가 아니라 **정답 방향과의 상관**이 실격 사유다.
              2. 한 진술에 독립적으로 참·거짓을 판정할 명제가 둘 이상 들어 있다.
              3. 진술이 강의노트 문장을 그대로 옮겼다(축자 복사).
              4. 조건에 따라 참일 수도 거짓일 수도 있어 진리값이 하나로 결정되지 않는다.
              5. 지엽적 수치·고유명사 하나만 확인하면 끝나는 사소한 진술이다.
              6. 가치·해석 주장인데 누구의 견해인지 밝히지 않았다.
              7. 정답이 거짓인데 무엇이 왜 틀렸는지 해설에 없다.
              """,
              QuizType.BLANK,
                  """
              1. 빈칸 주변 문맥만으로 다른 답이 들어가도 참인 문장이 된다(정답이 하나로 결정되지 않는다).
              2. 해당 지식을 몰라도 문장 다른 부분의 반복·정의문 구조로 빈칸이 채워진다.
              3. 관사·단복수·시제나 빈칸 길이가 정답 후보를 좁혀 준다.
              4. 지워진 단어가 그 문장의 중심 개념이 아니라 부수적 수식어다.
              5. 한 문장에 빈칸이 셋 이상이거나 정답이 여러 어절이라 문장이 무너졌다.
              6. 빈칸이 문장 첫머리에 있어 무엇을 묻는지 읽기 전에 답을 요구한다.
              7. 문장이 강의노트 문장을 그대로 옮겼다(축자 복사).
              """,
              QuizType.ESSAY,
                  """
              1. 채점기준표가 질문문에 없는 것을 요구한다(예: "예시 N개 이상", 특정 서술 형식).
                 지시하지 않은 것으로 감점하게 만드는 기준은 그 자체로 실격이다.
              2. 질문문이 서술 범위를 한정하지 않아 학습자마다 다른 것을 쓰게 된다.
              3. 모범답안·채점기준표·질문문 셋 중 어느 한 쌍이 어긋난다
                 (질문↔답 / 답↔기준표 / 질문↔기준표 중 어디인지 밝힐 수 있어야 한다).
              4. 채점기준의 충족 조건이 관찰 불가능하다("논리적으로 서술", "체계적으로 분석").
              5. 선택형으로 충분히 측정되는 것을 서술형으로 냈다(단순 정의·나열로 답이 끝난다).
              6. 모범답안이 강의노트 문장을 그대로 옮겼다(축자 복사).
              """,
              QuizType.REAL_BLANK,
                  """
              1. 빈칸 주변 문맥만으로 다른 답이 들어가도 참인 문장이 된다(정답이 하나로 결정되지 않는다).
              2. 해당 지식을 몰라도 문장 다른 부분의 반복·정의문 구조로 빈칸이 채워진다.
              3. 관사·단복수·시제가 정답 후보를 좁혀 준다.
              4. 지워진 단어가 그 문장의 중심 개념이 아니라 부수적 수식어다.
              5. 허용 정답 목록에 정답과 뜻이 다른 표현(인접·상위·하위 개념, 오탈자)이 들어 있다.
              6. 문장이 강의노트 문장을 그대로 옮겼다(축자 복사).
              """));

  /**
   * 실격 사유가 없는 유형이 있으면 기동을 막는다. 유형을 추가하고 여기를 빠뜨리면 그 유형만 다른 유형의 실격 사유로 검증되는데, 프롬프트가 그럴듯해 보여 로그로는 드러나지
   * 않는다.
   */
  public static void assertAllTypesCovered() {
    EnumSet<QuizType> missing = EnumSet.allOf(QuizType.class);
    missing.removeAll(DISQUALIFIERS.keySet());
    if (!missing.isEmpty()) {
      throw new IllegalStateException("실격 사유가 정의되지 않은 퀴즈 유형: " + missing);
    }
  }

  private static String buildCriteria(
      QuizType quizType, Map<QualityCriterion, Strictness> criteria) {
    if (criteria == null || criteria.isEmpty()) {
      return "- (설정된 항목 없음 — 필수 항목만 적용)\n";
    }
    return criteria.entrySet().stream()
        .filter(e -> e.getValue() != null && e.getValue() != Strictness.OFF)
        .filter(e -> e.getKey().appliesTo(quizType))
        .map(
            e ->
                "- %s (엄격도: %s): %s\n"
                    .formatted(e.getKey().key(), e.getValue().label(), e.getKey().description()))
        .collect(Collectors.joining());
  }
}
