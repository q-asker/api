package com.icc.qasker.quizset.entity;

import static jakarta.persistence.FetchType.LAZY;

import com.icc.qasker.global.entity.CreatedAt;
import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

  // 첫 생성본(v1): 질문(stem+선지) JSON 문자열, 해설 마크다운, 게이트 미달 사유(통과 시 null).
  // 질문은 앱이 JSON을 직접 직렬화/역직렬화하는 불투명 스냅샷이므로 TEXT로 저장한다(String↔JSON 컬럼 이중 인코딩 회피).
  // 질문 JSON·피드백(v1/v2)은 Pass-2(품질 재검토) 전용이라 해설 검증 경로에선 미사용 → 한 그룹("pass2")으로 지연 로딩한다.
  // (계측 OFF 시 즉시 로딩 폴백, 무해. quality-review는 @EntityGraph로 이 그룹을 한 쿼리에 eager 조회 권장.)
  @Basic(fetch = LAZY)
  @LazyGroup("pass2")
  @Column(columnDefinition = "TEXT")
  private String v1QuestionJson;

  @Column(columnDefinition = "TEXT")
  private String v1Explanation;

  @Basic(fetch = LAZY)
  @LazyGroup("pass2")
  @Column(columnDefinition = "TEXT")
  private String v1Feedback;

  // 재생성된 개선본(v2): 질문 JSON 문자열·해설. 재생성되지 않은 문항은 null.
  @Basic(fetch = LAZY)
  @LazyGroup("pass2")
  @Column(columnDefinition = "TEXT")
  private String v2QuestionJson;

  @Column(columnDefinition = "TEXT")
  private String v2Explanation;

  // 사후 재검토(Pass 2) 산출물. 생성 시점엔 null이며, 재검토 요청 시 채운다.
  @Basic(fetch = LAZY)
  @LazyGroup("pass2")
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
