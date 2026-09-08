package com.icc.qasker.quizset.scheduler;

import com.icc.qasker.quizset.GenerationStatus;
import com.icc.qasker.quizset.repository.ProblemSetRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 방치된 ProblemSet 정리 로직. 스케줄러(주기 발화)와 loadtest 온디맨드 트리거가 공유한다. 항상 등록되는 서비스라 loadtest에서도 호출할 수 있고, 그
 * SELECT(findStaleIds)가 계측 파이프라인에 태깅된다.
 */
@Service
@RequiredArgsConstructor
public class StaleGenerationRecoveryService implements StaleGenerationRecovery {

  /**
   * 세션 시한(10분)보다 길게 잡는다. 같은 값이면 진행 중인 생성이 저장 직전에 삭제된다 — 스케줄러는 세트 createdAt 기준이고 세션 시계는 PDF 업로드 뒤에
   * 출발해 항상 늦으며, 주기가 1분이라 판정도 최대 1분 늦게 온다.
   */
  private static final long STALE_THRESHOLD_MINUTES = 15;

  private static final List<GenerationStatus> TARGET_STATUSES =
      List.of(GenerationStatus.FAILED, GenerationStatus.GENERATING);

  private final ProblemSetRepository problemSetRepository;

  /** FAILED 또는 15분 이상 GENERATING 상태로 방치된 ProblemSet을 삭제하고 삭제 건수를 반환한다. */
  @Override
  @Transactional
  public int purgeStaleProblemSets() {
    Instant threshold = Instant.now().minus(STALE_THRESHOLD_MINUTES, ChronoUnit.MINUTES);
    List<Long> staleIds = problemSetRepository.findStaleIds(TARGET_STATUSES, threshold);
    if (!staleIds.isEmpty()) {
      problemSetRepository.deleteBulkByProblemSetIds(staleIds);
    }
    return staleIds.size();
  }
}
