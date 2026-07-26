package com.icc.qasker.quizset.service.mock;

import com.icc.qasker.quizset.QualityReviewService;
import com.icc.qasker.quizset.dto.QualityReviewResult;
import com.icc.qasker.quizset.repository.ProblemQualityLogRepository;
import com.icc.qasker.quizset.repository.ProblemSetRepository;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 부하 트레이스용 quality-review mock(@Profile("mock")). 실 서비스가 in-memory 맵만 읽는 latestResult 를 포함해, admin
 * quality-review 경로가 {@link ProblemQualityLogRepository} 를 §①(seed 축)에 편입하도록 실 finder 를 읽기 전용으로 태운다
 * — Gemini(QualityVerifier) 호출·마킹 UPDATE 없이 순증 0. 실 QualityReviewServiceImpl 의 TX1 로드 쿼리(pass2
 * eager·explanation 계열·세트 조회)를 그대로 발화시켜, 데이터 크기 따라 O(n) 여부를 seed 축에서 관측 가능하게 한다. 실 서비스는
 * hibernate-enhancement 하네스가 쓰지 않으므로(그건 explanation-review 만 태움) mock 으로 덮어도 A/B 측정에 영향이 없다.
 */
@Service
@Primary
@Profile("mock")
@RequiredArgsConstructor
public class MockQualityReviewService implements QualityReviewService {

  private final ProblemQualityLogRepository qualityLogRepository;
  private final ProblemSetRepository problemSetRepository;

  @Override
  @Transactional(readOnly = true)
  public void review(List<Long> problemSetIds) {
    touchReads(problemSetIds);
  }

  /** 실은 @Async — mock 은 동기 읽기로 요청(seed) 스레드에서 계측되게 한다(순증 0). */
  @Override
  @Transactional(readOnly = true)
  public void submitReviewBulk(List<Long> problemSetIds) {
    touchReads(problemSetIds);
  }

  /** 실은 in-memory 맵 조회(레포 무접촉) — mock 은 레포 읽기를 태워 GET 경로도 §①에 남긴다. */
  @Override
  @Transactional(readOnly = true)
  public Optional<QualityReviewResult> latestResult(Long problemSetId) {
    qualityLogRepository.findByProblemSetIdAndNumber(problemSetId, 1);
    return Optional.empty();
  }

  /** ProblemQualityLog 의 두 finder(explanation 계열·quality pass2 계열) + 세트 조회를 읽기 전용으로 태운다. */
  private void touchReads(List<Long> problemSetIds) {
    if (problemSetIds == null || problemSetIds.isEmpty()) {
      return;
    }
    qualityLogRepository.findByProblemSetIdIn(problemSetIds);
    qualityLogRepository.findWithPass2ByProblemSetIdIn(problemSetIds);
    problemSetRepository.findAllById(problemSetIds);
  }
}
