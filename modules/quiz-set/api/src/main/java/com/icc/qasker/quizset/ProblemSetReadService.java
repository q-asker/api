package com.icc.qasker.quizset;

import com.icc.qasker.quizset.dto.readonly.ProblemDetail;
import com.icc.qasker.quizset.dto.readonly.ProblemSetSummary;
import java.util.List;
import java.util.Optional;

/** ProblemSet/Problem 데이터를 read-only DTO로 제공하는 인터페이스. quiz-history 모듈에서 사용. */
public interface ProblemSetReadService {

  Optional<ProblemSetSummary> findProblemSetById(Long id);

  List<ProblemSetSummary> findProblemSetsByIds(List<Long> ids);

  List<ProblemDetail> findProblemsByProblemSetId(Long problemSetId);
}
