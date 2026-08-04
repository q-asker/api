package com.icc.qasker.quizset.scheduler;

import com.icc.qasker.quizset.GenerationStatus;
import com.icc.qasker.quizset.repository.ProblemSetRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;

/**
 * 부하 트레이스용 스케줄러 mock(@Profile("mock")). 실 서비스와 동일하게 스테일 SELECT + 부모 벌크 delete(단일 JPQL) 를 실행하고(자식
 * problem 은 FK ON DELETE CASCADE 로 DB 가 자동 삭제), 트랜잭션을 롤백해 순증 0 을 유지한다 — 벌크 JPQL 은 즉시 DML 로 DB 에 바로
 * 나가 계측에 잡히므로(엔티티 deleteAll 과 달리 명시 flush 불필요) 롤백 전에 이미 트레이스에 남는다. 실제 행은 지워지지 않아 라운드 간 동일한 스테일 집합을
 * 반복 측정한다(파괴적 삭제로 시드가 갉히거나 첫 라운드 후 빈손이 되는 것을 방지). 자식 cascade 삭제는 엔진 내부라 별도 SQL 로는 트레이스에 나타나지 않는다.
 */
@Service
@Primary
@Profile("mock")
@RequiredArgsConstructor
public class MockStaleGenerationRecoveryService implements StaleGenerationRecovery {

  private static final long STALE_THRESHOLD_MINUTES = 10;
  private static final List<GenerationStatus> TARGET_STATUSES =
      List.of(GenerationStatus.FAILED, GenerationStatus.GENERATING);

  private final ProblemSetRepository problemSetRepository;

  @Override
  @Transactional
  public int purgeStaleProblemSets() {
    Instant threshold = Instant.now().minus(STALE_THRESHOLD_MINUTES, ChronoUnit.MINUTES);
    List<Long> problemSetIds = problemSetRepository.findStaleIds(TARGET_STATUSES, threshold);
    if (!problemSetIds.isEmpty()) {
      problemSetRepository.deleteBulkByProblemSetIds(problemSetIds);
    }
    TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
    return problemSetIds.size();
  }
}
