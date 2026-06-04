package com.icc.qasker.cost.entity;

/** Outbox 행의 발행 상태. PENDING(미발행) → SENT(발행 완료). */
public enum OutboxStatus {
  PENDING,
  SENT
}
