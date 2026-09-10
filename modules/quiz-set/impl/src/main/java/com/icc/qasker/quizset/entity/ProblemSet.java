package com.icc.qasker.quizset.entity;

import com.icc.qasker.global.entity.CreatedAt;
import com.icc.qasker.global.quiz.QuizType;
import com.icc.qasker.quizset.GenerationStatus;
import com.icc.qasker.quizset.ProblemSetOrigin;
import com.icc.qasker.quizset.converter.IntegerListConverter;
import jakarta.persistence.CascadeType;
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

  @OneToMany(mappedBy = "problemSet", cascade = CascadeType.ALL)
  @Builder.Default
  private List<Problem> problems = new ArrayList<>();

  @Enumerated(EnumType.STRING)
  @Builder.Default
  @Column(nullable = false)
  private GenerationStatus generationStatus = GenerationStatus.GENERATING;

  @Enumerated(EnumType.STRING)
  private QuizType quizType;

  // 세트 출처. 오답 모아풀기로 만들어진 세트는 원본 자료가 없어, 자료를 전제로 하는 후속 동작의 노출 여부를 이 값으로 가른다.
  @Enumerated(EnumType.STRING)
  @Builder.Default
  @Column(nullable = false, length = 20)
  private ProblemSetOrigin origin = ProblemSetOrigin.DOCUMENT;

  // 오답 모아풀기로 만든 세트가 어느 폴더에서 모였는지. 자료 기반 세트는 null.
  @Column private Long sourceFolderId;

  @PositiveOrZero
  @Column(nullable = false)
  private Integer totalQuizCount;

  @Column(unique = true, nullable = false)
  private String sessionId;

  @Column(nullable = false)
  private String fileUrl;

  @Column(columnDefinition = "TEXT")
  private String customInstruction;

  // 동일 재현(이어풀기)용 생성 조건 — 생성 시점에만 알 수 있어 세트에 함께 저장한다(일반 조회 응답엔 없어 복원 불가).
  // V18 이전 legacy 세트는 컬럼 NULL. IntegerListConverter는 NULL을 빈 리스트로 읽으므로 조회 매핑에서 빈 리스트를 null로 정규화한다.
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
