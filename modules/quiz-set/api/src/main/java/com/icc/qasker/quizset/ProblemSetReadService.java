package com.icc.qasker.quizset;

import com.icc.qasker.quizset.dto.readonly.ProblemDetail;
import com.icc.qasker.quizset.dto.readonly.ProblemSetSummary;
import java.util.List;
import java.util.Optional;

/** ProblemSet/Problem 데이터를 read-only DTO로 제공하는 인터페이스. quiz-history 모듈에서 사용. */
public interface ProblemSetReadService {

  Optional<ProblemSetSummary> findProblemSetById(Long id);

  /** 세션 식별자로 세트를 찾는다. 같은 요청이 두 번 들어왔을 때 이미 만들어진 세트를 그대로 돌려주기 위해 쓴다. */
  Optional<ProblemSetSummary> findProblemSetBySessionId(String sessionId);

  List<ProblemSetSummary> findProblemSetsByIds(List<Long> ids);

  List<ProblemDetail> findProblemsByProblemSetId(Long problemSetId);
}
