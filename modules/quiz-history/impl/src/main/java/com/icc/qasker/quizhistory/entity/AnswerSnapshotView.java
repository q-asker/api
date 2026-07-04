package com.icc.qasker.quizhistory.entity;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** AnswerSnapshot 목록을 문항 번호 기준 조회용으로 감싼 값 객체. 상세/서술형 상세 매핑에서 반복되던 스냅샷 → Map 변환을 한 곳으로 모은다. */
public final class AnswerSnapshotView {

  private final Map<Integer, Integer> userAnswers;
  private final Map<Integer, Boolean> inReviews;
  private final Map<Integer, String> textAnswers;

  private AnswerSnapshotView(
      Map<Integer, Integer> userAnswers,
      Map<Integer, Boolean> inReviews,
      Map<Integer, String> textAnswers) {
    this.userAnswers = userAnswers;
    this.inReviews = inReviews;
    this.textAnswers = textAnswers;
  }

  public static AnswerSnapshotView from(List<AnswerSnapshot> answers) {
    Map<Integer, Integer> userAnswers =
        answers.stream()
            .collect(Collectors.toMap(AnswerSnapshot::number, AnswerSnapshot::userAnswer));
    Map<Integer, Boolean> inReviews =
        answers.stream()
            .collect(Collectors.toMap(AnswerSnapshot::number, AnswerSnapshot::inReview));
    Map<Integer, String> textAnswers =
        answers.stream()
            .filter(a -> a.textAnswer() != null)
            .collect(Collectors.toMap(AnswerSnapshot::number, AnswerSnapshot::textAnswer));
    return new AnswerSnapshotView(userAnswers, inReviews, textAnswers);
  }

  public int userAnswer(int number) {
    return userAnswers.getOrDefault(number, 0);
  }

  public boolean inReview(int number) {
    return inReviews.getOrDefault(number, false);
  }

  public String textAnswer(int number) {
    return textAnswers.get(number);
  }
}
