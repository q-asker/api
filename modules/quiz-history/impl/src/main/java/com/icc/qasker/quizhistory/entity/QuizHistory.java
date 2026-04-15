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

  @Column(length = 100)
  private String title;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(columnDefinition = "JSON")
  private List<AnswerSnapshot> answers;

  @Column private Integer score;

  private String totalTime;

  @Enumerated(EnumType.STRING)
  @Builder.Default
  @Column(nullable = false)
  private QuizHistoryStatus status = QuizHistoryStatus.INCOMPLETE;

  public void updateTitle(String title) {
    this.title = title;
  }

  public void completeQuiz(List<AnswerSnapshot> answers, Integer score, String totalTime) {
    this.answers = answers;
    this.score = score;
    this.totalTime = totalTime;
    this.status = QuizHistoryStatus.COMPLETED;
  }

  public enum QuizHistoryStatus {
    INCOMPLETE,
    COMPLETED
  }
}
