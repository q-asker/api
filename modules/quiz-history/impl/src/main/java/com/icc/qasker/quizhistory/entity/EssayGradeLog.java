package com.icc.qasker.quizhistory.entity;

import com.icc.qasker.global.entity.CreatedAt;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.List;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** ESSAY 채점 API 호출 로그. 비동기로 저장된다. */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
@Table(name = "essay_grade_log")
public class EssayGradeLog extends CreatedAt {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  private String userId;

  @Column(nullable = false)
  private Long problemSetId;

  @Column(nullable = false)
  private int problemNumber;

  @Column(nullable = false, length = 500)
  private String question;

  @Column(nullable = false, columnDefinition = "TEXT")
  private String studentAnswer;

  @Column(nullable = false)
  private int attemptCount;

  @Column(nullable = false)
  private int totalScore;

  @Column(nullable = false)
  private int maxScore;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(nullable = false, columnDefinition = "JSON")
  private List<ElementScoreSnapshot> elementScores;

  @Column(columnDefinition = "TEXT")
  private String overallFeedback;

  @Column(columnDefinition = "JSON")
  private String evidenceJson;

  public record ElementScoreSnapshot(
      String element, int maxPoints, int earnedPoints, String level, String feedback) {}
}
