package com.icc.qasker.quizset.entity;

import com.icc.qasker.global.entity.CreatedAt;
import com.icc.qasker.quizset.GenerationStatus;
import com.icc.qasker.quizset.dto.ferequest.enums.QuizType;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.validation.constraints.PositiveOrZero;
import java.util.ArrayList;
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
public class ProblemSet extends CreatedAt {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(length = 100)
  private String title;

  private String userId;

  @OneToMany(mappedBy = "problemSet", cascade = CascadeType.ALL, orphanRemoval = true)
  @Builder.Default
  private List<Problem> problems = new ArrayList<>();

  @Enumerated(EnumType.STRING)
  @Builder.Default
  @Column(nullable = false)
  private GenerationStatus generationStatus = GenerationStatus.GENERATING;

  @Enumerated(EnumType.STRING)
  private QuizType quizType;

  @PositiveOrZero
  @Column(nullable = false)
  private Integer totalQuizCount;

  @Column(unique = true, nullable = false)
  private String sessionId;

  @Column(nullable = false)
  private String fileUrl;

  // 이하 헬퍼 함수
  public void updateStatus(GenerationStatus status) {
    if (status == null) {
      throw new IllegalArgumentException("ProblemSet status must not be null");
    }
    this.generationStatus = status;
  }

  public void updateTitle(String title) {
    this.title = title;
  }
}
