package com.icc.qasker.loadtest;

import com.icc.qasker.quizset.scheduler.StaleGenerationRecoveryService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 부하 테스트용 스케줄러 드라이버. 타이머(@Profile("!loadtest"))가 꺼진 loadtest에서 스케줄러 로직을 온디맨드로 1회 태운다. 그
 * SELECT(findByGenerationStatusInAndCreatedAtBefore 스캔)가 계측 파이프라인에 잡혀, 비-controller 백그라운드 쿼리도 실
 * uri로 트레이스된다. @Profile("loadtest").
 */
@RestController
@Profile("loadtest")
@RequiredArgsConstructor
@RequestMapping("/local/scheduler")
public class LocalSchedulerController {

  private final StaleGenerationRecoveryService staleGenerationRecoveryService;

  @PostMapping("/stale-generation")
  public ResponseEntity<Integer> purgeStale() {
    return ResponseEntity.ok(staleGenerationRecoveryService.purgeStaleProblemSets());
  }
}
