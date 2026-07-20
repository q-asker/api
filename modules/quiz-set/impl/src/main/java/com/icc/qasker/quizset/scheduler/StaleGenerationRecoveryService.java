package com.icc.qasker.quizset.scheduler;

import com.icc.qasker.quizset.GenerationStatus;
import com.icc.qasker.quizset.entity.ProblemSet;
import com.icc.qasker.quizset.repository.ProblemSetRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 방치된 ProblemSet 정리 로직. 스케줄러(주기 발화)와 loadtest 온디맨드 트리거가 공유한다. 항상 등록되는 서비스라 loadtest에서도 호출할 수 있고, 그
 * SELECT(findByGenerationStatusInAndCreatedAtBefore)가 계측 파이프라인에 태깅된다.
 */
@Service
@RequiredArgsConstructor
public class StaleGenerationRecoveryService {

  private static final long STALE_THRESHOLD_MINUTES = 10;
  private static final List<GenerationStatus> TARGET_STATUSES =
      List.of(GenerationStatus.FAILED, GenerationStatus.GENERATING);

  private final ProblemSetRepository problemSetRepository;

  /** FAILED 또는 10분 이상 GENERATING 상태로 방치된 ProblemSet을 삭제하고 삭제 건수를 반환한다. */
  @Transactional
  public int purgeStaleProblemSets() {
    Instant threshold = Instant.now().minus(STALE_THRESHOLD_MINUTES, ChronoUnit.MINUTES);
    List<ProblemSet> stale =
        problemSetRepository.findByGenerationStatusInAndCreatedAtBefore(TARGET_STATUSES, threshold);
    if (!stale.isEmpty()) {
      problemSetRepository.deleteAll(stale);
    }
    return stale.size();
  }
}
