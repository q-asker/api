package com.icc.qasker.quizset.entity;

import com.icc.qasker.global.entity.CreatedAt;
import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.LazyGroup;

/**
 * 문항 품질 로그 — 문항 1:1(problem_set_id + number). problem이 순수 서빙만 책임지도록 생성 근거를 이 테이블로 분리 보관한다. 첫
 * 생성본(v1: 질문·해설·미달 사유)과 재생성된 개선본(v2: 질문·해설)을 함께 담고, 사후 재검토(Pass 2) 결과(v2Feedback·review)는 필요 시
 * 마킹한다. 소수만 마킹하는 dirty tracking·부분 컬럼 UPDATE를 위해 {@link DynamicUpdate}를 적용한다.
 */
@Entity
@Getter
@DynamicUpdate
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
@Table(
    name = "problem_quality_log",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uk_pql_set_number",
            columnNames = {"problem_set_id", "number"}))
public class ProblemQualityLog extends CreatedAt {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false)
  private Long problemSetId;

  @Column(name = "number", nullable = false)
  private int number;

  @LazyGroup("pass2")
  @Basic(fetch = FetchType.LAZY)
  @Column(columnDefinition = "TEXT")
  private String v1QuestionJson;

  @Column(columnDefinition = "TEXT")
  private String v1Explanation;

  @LazyGroup("pass2")
  @Basic(fetch = FetchType.LAZY)
  @Column(columnDefinition = "TEXT")
  private String v1Feedback;

  @LazyGroup("pass2")
  @Basic(fetch = FetchType.LAZY)
  @Column(columnDefinition = "TEXT")
  private String v2QuestionJson;

  @Column(columnDefinition = "TEXT")
  private String v2Explanation;

  @LazyGroup("pass2")
  @Basic(fetch = FetchType.LAZY)
  @Column(columnDefinition = "TEXT")
  private String v2Feedback;

  @Column(columnDefinition = "TEXT")
  private String review;

  /** 재생성된 개선본(v2)의 질문·해설을 부착한다. */
  public void bindV2(String v2QuestionJson, String v2Explanation) {
    this.v2QuestionJson = v2QuestionJson;
    this.v2Explanation = v2Explanation;
  }

  /** 질문 재검증(Pass 2) 결과를 v2Feedback에 반영한다. */
  public void markQuestionVerdict(String v2Feedback) {
    this.v2Feedback = v2Feedback;
  }

  /** 해설 형식 검증(정규식) 결과를 review에 반영한다. */
  public void markExplanationReview(String review) {
    this.review = review;
  }
}
