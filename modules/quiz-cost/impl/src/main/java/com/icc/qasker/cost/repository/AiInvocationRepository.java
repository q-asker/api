package com.icc.qasker.cost.repository;

import com.icc.qasker.cost.entity.AiInvocation;
import org.springframework.data.jpa.repository.JpaRepository;

/** AI 호출 원장 Repository. */
public interface AiInvocationRepository extends JpaRepository<AiInvocation, Long> {

  /** 멱등 검사: 동일 requestId의 원장 행이 이미 존재하는지 확인한다. */
  boolean existsByRequestId(String requestId);
}
