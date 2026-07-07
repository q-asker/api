package com.icc.qasker.quizset.entity;

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
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 문항 품질 로그 — 문항 1:1(problem_set_id + number). problem이 순수 서빙만 책임지도록 생성 근거(rationale)·품질 판정
 * (qualityStatus/feedback)과 재생성 원본(v1)을 이 테이블로 분리 보관한다. 미달 subset만 마킹하는 dirty tracking·부분 컬럼
 * UPDATE를 위해 {@link DynamicUpdate}를 적용한다.
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

  // 생성 근거(검증·재생성 입력). Pass 2 재검토가 이 값을 읽는다.
  @JdbcTypeCode(SqlTypes.JSON)
  @Column(columnDefinition = "JSON")
  private Rationale rationale;

  @Enumerated(EnumType.STRING)
  @Column(length = 20)
  private QualityStatus qualityStatus;

  @Column(columnDefinition = "TEXT")
  private String feedback;

  // 재생성된 문항인 경우 원본 미달본(v1)과 그 미달 사유. 아니면 null. 현재(v2)는 problem + 이 로그의 quality가 곧 결과다.
  @Column(columnDefinition = "JSON")
  private String v1Json;

  @Column(columnDefinition = "TEXT")
  private String v1Feedback;

  /** 생성 근거 갱신(게이트 저장 시). */
  public void bindRationale(Rationale rationale) {
    this.rationale = rationale;
  }

  /** 품질 판정 반영(게이트·Pass 2 재검토). 통과 시 status=OK·feedback=null. */
  public void applyVerdict(QualityStatus qualityStatus, String feedback) {
    this.qualityStatus = qualityStatus;
    this.feedback = feedback;
  }

  /** 재생성 원본(v1) 스냅샷 기록. */
  public void bindV1(String v1Json, String v1Feedback) {
    this.v1Json = v1Json;
    this.v1Feedback = v1Feedback;
  }
}
