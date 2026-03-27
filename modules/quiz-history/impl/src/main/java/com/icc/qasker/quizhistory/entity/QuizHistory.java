package com.icc.qasker.quizhistory.entity;

import com.icc.qasker.global.entity.CreatedAt;
import com.icc.qasker.quizhistory.converter.AnswerSnapshotConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.util.List;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
@Table(
    name = "quiz_history",
    indexes = {@Index(name = "idx_quiz_history_user_id", columnList = "userId")})
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

  @Convert(converter = AnswerSnapshotConverter.class)
  @Column(columnDefinition = "TEXT")
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

  public enum QuizHistoryStatus {
    INCOMPLETE,
    COMPLETED
  }
}
