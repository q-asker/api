package com.icc.qasker.quizset.service.mock;

import com.icc.qasker.quizset.ProblemSetService;
import com.icc.qasker.quizset.dto.ferequest.ChangeTitleRequest;
import com.icc.qasker.quizset.dto.feresponse.ChangeTitleResponse;
import com.icc.qasker.quizset.dto.feresponse.ProblemSetResponse;
import com.icc.qasker.quizset.service.query.ProblemSetServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

/**
 * 부하 트레이스용 problem-set mock(@Profile("mock")). 조회는 실 서비스에 위임하고, 제목 변경(기존 행 UPDATE)은 변경→원복으로 순증 0을
 * 유지한다(별개 트랜잭션 2건이라 UPDATE 두 번이 실 URI로 태깅되고 데이터는 불변).
 */
@Service
@Primary
@Profile("mock")
@RequiredArgsConstructor
public class MockProblemSetService implements ProblemSetService {

  private final ProblemSetServiceImpl real;

  @Override
  public ProblemSetResponse getProblemSet(String problemSetId) {
    return real.getProblemSet(problemSetId);
  }

  @Override
  public ChangeTitleResponse changeProblemSetTitle(
      String userId, String problemSetId, ChangeTitleRequest request) {
    String original = real.getProblemSet(problemSetId).title();
    ChangeTitleResponse response = real.changeProblemSetTitle(userId, problemSetId, request);
    real.changeProblemSetTitle(userId, problemSetId, new ChangeTitleRequest(original));
    return response;
  }
}
