package com.icc.qasker.quizset.service.mock;

import com.icc.qasker.quizset.ProblemSetService;
import com.icc.qasker.quizset.dto.ferequest.ChangeTitleRequest;
import com.icc.qasker.quizset.dto.feresponse.ChangeTitleResponse;
import com.icc.qasker.quizset.dto.feresponse.ProblemSetResponse;
import com.icc.qasker.quizset.entity.ProblemSet;
import com.icc.qasker.quizset.repository.ProblemSetRepository;
import com.icc.qasker.quizset.service.query.ProblemSetServiceImpl;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;

/**
 * 부하 트레이스용 problem-set mock(@Profile("mock")). 조회는 실 서비스에 위임한다. 제목 변경은 다른 도메인 mock 처럼 throwaway
 * problem_set 을 만들어 INSERT→UPDATE 를 태우고 트랜잭션 롤백으로 순증 0 을 달성한다 — 소유검증에 묶이지 않아 임의 problemSetId(수확
 * id)로도 종단 UPDATE 가 결정적으로 발화한다. INSERT·UPDATE 는 실 URI 로 트레이스에 남고 커밋되지 않아 DB 상태는 불변이다.
 */
@Service
@Primary
@Profile("mock")
@RequiredArgsConstructor
public class MockProblemSetService implements ProblemSetService {

  private final ProblemSetServiceImpl real;
  private final ProblemSetRepository problemSetRepository;

  @Override
  public ProblemSetResponse getProblemSet(String problemSetId) {
    return real.getProblemSet(problemSetId);
  }

  @Override
  @Transactional
  public ChangeTitleResponse changeProblemSetTitle(
      String userId, String problemSetId, ChangeTitleRequest request) {
    ProblemSet throwaway =
        ProblemSet.builder()
            .userId(userId)
            .title("mock")
            .totalQuizCount(0)
            .sessionId("mock-" + UUID.randomUUID())
            .fileUrl("mock")
            .build();
    problemSetRepository.save(throwaway); // INSERT (IDENTITY → 즉시 발화)
    throwaway.updateTitle(request.title());
    problemSetRepository.saveAndFlush(throwaway); // UPDATE flush → 트레이스
    // 자기정리: 롤백으로 INSERT·UPDATE 를 되돌린다(트레이스엔 남고 DB엔 안 남음).
    TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
    return new ChangeTitleResponse(request.title());
  }
}
