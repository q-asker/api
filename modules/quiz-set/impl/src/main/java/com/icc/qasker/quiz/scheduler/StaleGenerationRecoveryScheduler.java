package com.icc.qasker.quiz.scheduler;

import com.icc.qasker.quiz.GenerationStatus;
import com.icc.qasker.quiz.entity.ProblemSet;
import com.icc.qasker.quiz.repository.ProblemSetRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** FAILED 또는 10분 이상 GENERATING 상태로 방치된 ProblemSet을 삭제한다. */
@Slf4j
@Component
@RequiredArgsConstructor
public class StaleGenerationRecoveryScheduler {

  private static final long STALE_THRESHOLD_MINUTES = 10;
  private static final List<GenerationStatus> TARGET_STATUSES =
      List.of(GenerationStatus.FAILED, GenerationStatus.GENERATING);

  private final ProblemSetRepository problemSetRepository;

  @Scheduled(fixedRate = 60_000)
  @Transactional
  public void deleteStaleProblemSets() {
    Instant threshold = Instant.now().minus(STALE_THRESHOLD_MINUTES, ChronoUnit.MINUTES);

    List<ProblemSet> staleList =
        problemSetRepository.findByGenerationStatusInAndCreatedAtBefore(TARGET_STATUSES, threshold);

    if (!staleList.isEmpty()) {
      problemSetRepository.deleteAll(staleList);
      log.warn("방치된 ProblemSet {}건 삭제 (FAILED + GENERATING 10분 초과)", staleList.size());
    }
  }
}
