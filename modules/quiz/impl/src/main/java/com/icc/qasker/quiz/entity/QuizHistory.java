package com.icc.qasker.quiz.entity;

import com.icc.qasker.global.entity.CreatedAt;
import com.icc.qasker.quiz.QuizHistoryStatus;
import com.icc.qasker.quiz.converter.UserAnswerConverter;
import com.icc.qasker.quiz.dto.ferequest.UserAnswer;
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
import jakarta.persistence.UniqueConstraint;
import java.util.List;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SoftDelete;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
@Table(
    name = "quiz_history",
    indexes = {@Index(name = "idx_quiz_history_user_id", columnList = "userId")})
@SoftDelete(columnName = "deleted")
public class QuizHistory extends CreatedAt {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false)
  private String userId;

  @Column(nullable = false)
  private Long problemSetId;

  @Convert(converter = UserAnswerConverter.class)
  @Column(columnDefinition = "TEXT")
  private List<UserAnswer> answers;

  @Column private Integer score;

  private String totalTime;
}
