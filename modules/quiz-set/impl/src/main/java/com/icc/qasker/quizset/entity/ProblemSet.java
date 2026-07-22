package com.icc.qasker.quizset.entity;

import com.icc.qasker.global.entity.CreatedAt;
import com.icc.qasker.quizset.GenerationStatus;
import com.icc.qasker.quizset.converter.IntegerListConverter;
import com.icc.qasker.quizset.dto.ferequest.enums.QuizType;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
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

  // 읽기 전용 연관관계 — Problem의 생성/수정/삭제는 ProblemRepository로 직접 관리(독립 쓰기 단위)
  @OneToMany(mappedBy = "problemSet")
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

  @Column(columnDefinition = "TEXT")
  private String customInstruction;

  // 동일 재현용 생성 조건 — 생성 시점에만 알 수 있어 세트에 함께 저장한다(조회 응답엔 없어 복원 불가).
  // V19 이전 legacy 세트는 null(컬럼 NULL). IntegerListConverter는 NULL을 빈 리스트로 읽는다.
  @Convert(converter = IntegerListConverter.class)
  @Column(columnDefinition = "TEXT")
  private List<Integer> pageNumbers;

  // 요청 Language enum(quiz-make 모듈)을 이 모듈이 의존하지 않아 문자열("KO"/"EN")로 저장한다.
  @Column(length = 8)
  private String language;

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
