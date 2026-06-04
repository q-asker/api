package com.icc.qasker.cost.entity;

import com.icc.qasker.cost.dto.InvocationStatus;
import com.icc.qasker.global.entity.CreatedAt;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * AI 호출 원장. Gemini 호출 1건 = 1행으로, 사용자별 토큰 사용량 추적의 원천 데이터다. request_id UNIQUE 제약으로 멱등을 보장한다(동일 호출 중복
 * 적재 차단). prod 프로파일은 ddl-auto=validate이므로 컬럼 타입을 V14 마이그레이션과 정확히 일치시킨다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
@Table(name = "ai_invocation")
public class AiInvocation extends CreatedAt {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  /** 호출 단위 멱등 키 (UUID) */
  @Column(nullable = false, columnDefinition = "CHAR(36)")
  private String requestId;

  /** 사용자 식별자 (User PK = provider_providerId 문자열) */
  @Column(nullable = false)
  private String userId;

  /** 생성된 문제세트 id (실패 시 null) */
  private Long quizSetId;

  @Column(nullable = false)
  private String model;

  private String modelVersion;

  @Column(nullable = false)
  private long inputTokens;

  @Column(nullable = false)
  private long cachedTokens;

  @Column(nullable = false)
  private long thinkingTokens;

  @Column(nullable = false)
  private long outputTokens;

  @Column(nullable = false)
  private long latencyMs;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private InvocationStatus status;

  /** 실패 시 오류 코드 (성공 시 null) */
  private String errorCode;

  /** 호출 발생 시각 */
  @Column(nullable = false)
  private Instant occurredAt;
}
