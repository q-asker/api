package com.icc.qasker.quizhistory.entity;

import com.icc.qasker.global.entity.CreatedAt;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.List;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
@Table(
    name = "quiz_history",
    uniqueConstraints = {
      @UniqueConstraint(
          name = "uk_quiz_history_user_problem",
          columnNames = {"user_id", "problem_set_id"})
    })
public class QuizHistory extends CreatedAt {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false)
  private String userId;

  @Column(nullable = false)
  private Long problemSetId;

  /** 소속 폴더 id. null이면 미분류(어느 폴더에도 속하지 않음). */
  @Column private Long folderId;

  @Column(length = 100)
  private String title;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(columnDefinition = "JSON")
  private List<AnswerSnapshot> answers;

  @Column private Integer score;

  /** 풀이를 마친 시각. createdAt 은 기록 행이 처음 만들어진 시각이라 재풀이해도 갱신되지 않아 "가장 최근에 푼 순"을 표현하지 못한다. 미완료면 null. */
  @Column private Instant completedAt;

  private String totalTime;

  @Enumerated(EnumType.STRING)
  @Builder.Default
  @Column(nullable = false)
  private QuizHistoryStatus status = QuizHistoryStatus.INCOMPLETE;

  public void updateTitle(String title) {
    this.title = title;
  }

  /** 폴더 배정/해제. folderId가 null이면 미분류로 되돌린다(단일 소속: 기존 소속을 덮어씀). */
  public void assignFolder(Long folderId) {
    this.folderId = folderId;
  }

  public void completeQuiz(List<AnswerSnapshot> answers, Integer score, String totalTime) {
    this.answers = answers;
    this.score = score;
    this.totalTime = totalTime;
    this.status = QuizHistoryStatus.COMPLETED;
    this.completedAt = Instant.now();
  }

  public enum QuizHistoryStatus {
    INCOMPLETE,
    COMPLETED
  }
}
