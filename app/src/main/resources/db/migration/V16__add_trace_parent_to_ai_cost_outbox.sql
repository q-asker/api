-- ai_cost_outbox에 분산 추적(W3C traceparent) 컬럼 추가.
-- 발행(MessageRelay)이 1초 뒤 별도 스레드에서 일어나 trace가 끊기므로,
-- 적재 시점(요청 trace)의 컨텍스트를 저장했다가 발행 시 복원해 end-to-end 추적을 잇기 위함이다.
-- 기존 행은 NULL(추적 없이 발행) — 하위호환.
ALTER TABLE ai_cost_outbox ADD COLUMN trace_parent VARCHAR(64) NULL;
