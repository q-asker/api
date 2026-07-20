package com.icc.qasker.quizset.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 방치된 ProblemSet을 주기적으로 정리한다. 실제 정리 로직은 {@link StaleGenerationRecoveryService}에 있고 여기서는 주기 발화만
 * 담당한다. loadtest 프로파일에서는 타이머를 비활성화한다 — 부하 트레이스가 순증 0을 유지하도록 백그라운드 삭제를 막는다(로직은 온디맨드 트리거가 호출).
 */
@Slf4j
@Component
@Profile("!loadtest")
@RequiredArgsConstructor
public class StaleGenerationRecoveryScheduler {

  private final StaleGenerationRecoveryService staleGenerationRecoveryService;

  @Scheduled(fixedRate = 60_000)
  public void deleteStaleProblemSets() {
    int purged = staleGenerationRecoveryService.purgeStaleProblemSets();
    if (purged > 0) {
      log.warn("[방치 ProblemSet 정리] FAILED/GENERATING 10분 초과 삭제 count={}", purged);
    }
  }
}
