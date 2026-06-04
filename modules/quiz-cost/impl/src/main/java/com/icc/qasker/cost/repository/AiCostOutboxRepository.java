package com.icc.qasker.cost.repository;

import com.icc.qasker.cost.entity.AiCostOutbox;
import com.icc.qasker.cost.entity.OutboxStatus;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/** AI 비용 Outbox Repository. MessageRelay가 PENDING 행을 생성 순서대로 배치 폴링한다. */
public interface AiCostOutboxRepository extends JpaRepository<AiCostOutbox, Long> {

  /**
   * 지정 상태의 행을 생성 순서(오래된 것 먼저)로 조회한다. idx_ai_cost_outbox_poll(status, created_at) 인덱스를 활용한다.
   *
   * @param status 조회 대상 상태 (PENDING)
   * @param pageable 배치 크기 제한
   */
  List<AiCostOutbox> findByStatusOrderByCreatedAtAsc(OutboxStatus status, Pageable pageable);
}
