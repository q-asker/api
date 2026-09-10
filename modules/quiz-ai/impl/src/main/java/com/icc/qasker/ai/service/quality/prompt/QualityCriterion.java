package com.icc.qasker.ai.service.quality.prompt;

import com.icc.qasker.global.quiz.QuizType;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;

/**
 * 검증 항목(FR-002·FR-011). 항목명·설명·적용 유형을 한 곳에 모은 SSOT다.
 *
 * <p>yml(q-asker.ai.quality.criteria)의 케밥케이스 키가 스프링 relaxed binding으로 이 상수에 매핑된다 — 오타는 바인딩 실패, 즉 기동
 * 실패로 드러난다. 항목을 추가·삭제할 때 여기만 고치면 프롬프트 설명과 유형별 적용 범위가 함께 따라온다.
 */
public enum QualityCriterion {

  /** 전 유형 공통 필수(FR-002b). */
  CONSTRUCTION_STRATEGY(Scope.ALL, "위 '실격 사유'에 해당하는 것이 하나도 없는가."),

  /** 전 유형 공통 필수(FR-002b). */
  INSTRUCTION_APPLICATION(
      Scope.ALL, "사용자 지시(customInstruction)가 문항·appliedInstruction에 정확히 반영됐는가."),

  SINGLE_CORRECT_ANSWER(Scope.FIXED_ANSWER, "정답이 유일한가(복수 정답·정답 없음이 아님)."),

  ANSWER_GROUNDED_IN_SOURCE(Scope.ALL, "정답이 강의노트 원문에 근거하는가(첨부 PDF가 있으면 원문과 직접 대조)."),

  DISTRACTORS_PLAUSIBLE(Scope.DISTRACTORS, "오답이 진지하게 고민할 만큼 그럴듯한가(명백한 극단·환상 진술이 아님)."),

  NO_OUTSIDE_KNOWLEDGE(Scope.ALL, "강의노트 범위 밖 지식 없이 풀 수 있는가(환각·외부지식 보강 배제)."),

  SHORTCUT_PREVENTION(
      Scope.DISTRACTORS,
      "내용을 몰라도 선지의 형태만으로 정답을 고를 수 있으면 위반. 아래 셋을 각각 확인한다."
          + " ① 정답 길이: 정답이 오답들보다 눈에 띄게 길면 위반. 길이는 고르게 맞추거나, 길어야 한다면 정답이 아닌 선지가 길어야 한다."
          + " ② 절대어 편중: '항상·모든·절대로·반드시·오직'처럼 예외 없음을 단언하는 표현이 오답에만 쓰이고 정답만 조건부로 서술되면 위반."
          + " 절대어가 있다는 사실 자체는 위반이 아니다 — 정답과 오답 양쪽에 고르게 나타나면 정답 단서가 되지 않으므로 통과."
          + " ③ 어조·위험도 대칭: 정답만 유독 온건하거나 균형 잡힌 서술이면 위반. 트레이드오프형에서 오답이"
          + " '런타임 오류·기동 실패·데이터 전면 손실' 같은 파국을 스스로 선언해 소거되면 위반 — 선지들의 감수 위험도가 대칭이어야 한다."),

  COGNITIVE_DEPTH(
      Scope.ALL,
      "정답이 강의노트 문장에 1:1로 직접 대응하거나, 표/다이어그램의 단일 셀 기본값(예: length=255) 하나를 암기로 알면 즉시 풀리면"
          + " 위반. 정답 도출에 여러 항목의 교차 대조나 다단계 추론이 필요해야 한다."),

  MODEL_ANSWER_BASIS(Scope.ESSAY, "[ESSAY] 모범답안이 원문에 근거하는가."),

  RUBRIC_CONSISTENCY(Scope.ESSAY, "[ESSAY] 질문↔모범답안↔채점 루브릭 3자가 정합하는가.");

  private final Scope scope;
  private final String description;

  QualityCriterion(Scope scope, String description) {
    this.scope = scope;
    this.description = description;
  }

  /** 프롬프트에 찍히는 항목명. yml 키와 같은 케밥케이스다. */
  public String key() {
    return name().toLowerCase(Locale.ROOT).replace('_', '-');
  }

  /** 검증관이 이 항목으로 무엇을 점검해야 하는지. 경량 모델이 항목을 정확히 적용하도록 프롬프트에 함께 싣는다. */
  public String description() {
    return description;
  }

  /** 이 유형의 프롬프트에 실을 항목인가. 없는 산출물을 찾게 만드는 항목은 여기서 걸러진다. */
  public boolean appliesTo(QuizType quizType) {
    return scope.types.contains(quizType);
  }

  /** 항목이 판정 가능한 유형의 범위 — 문항이 무엇을 가지고 있느냐로 갈린다. */
  private enum Scope {
    /** 전 유형. */
    ALL(EnumSet.allOf(QuizType.class)),

    /** 정답이 하나로 확정되는 유형 — ESSAY는 모범답안이라 유일성을 따질 대상이 아니다. */
    FIXED_ANSWER(EnumSet.complementOf(EnumSet.of(QuizType.ESSAY))),

    /** 오답 선지가 있는 유형 — REAL_BLANK·ESSAY는 선지 자체가 없어 판정할 수 없다. */
    DISTRACTORS(EnumSet.of(QuizType.MULTIPLE, QuizType.OX, QuizType.BLANK)),

    /** ESSAY 전용. */
    ESSAY(EnumSet.of(QuizType.ESSAY));

    private final Set<QuizType> types;

    Scope(Set<QuizType> types) {
      this.types = types;
    }
  }
}
