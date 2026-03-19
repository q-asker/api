package com.icc.qasker.quiz.service.query;

import com.icc.qasker.global.component.HashUtil;
import com.icc.qasker.global.error.CustomException;
import com.icc.qasker.global.error.ExceptionMessage;
import com.icc.qasker.quiz.ProblemSetService;
import com.icc.qasker.quiz.dto.ferequest.ChangeTitleRequest;
import com.icc.qasker.quiz.dto.feresponse.ProblemSetResponse;
import com.icc.qasker.quiz.entity.ProblemSet;
import com.icc.qasker.quiz.mapper.ProblemSetResponseMapper;
import com.icc.qasker.quiz.repository.ProblemSetRepository;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

@Service
@AllArgsConstructor
@Transactional(readOnly = true)
public class ProblemSetServiceImpl implements ProblemSetService {

  private final ProblemSetResponseMapper problemSetResponseMapper;
  private final ProblemSetRepository problemSetRepository;
  private final HashUtil hashUtil;

  @Override
  public ProblemSetResponse getProblemSet(String problemSetId) {
    Assert.hasText(problemSetId, "problemSetId must not be blank");
    ProblemSet problemSet = getProblemSetEntityByEncoded(problemSetId);
    return problemSetResponseMapper.fromEntity(problemSet);
  }

  @Override
  @Transactional
  public void changeProblemSetTitle(
      String userId, String problemSetId, ChangeTitleRequest request) {
    ProblemSet ps = getProblemSetEntityByEncoded(problemSetId);
    if (!ps.getUserId().equals(userId)) {
      throw new CustomException(ExceptionMessage.UNAUTHORIZED);
    }
    ps.updateTitle(request.title());
  }

  private ProblemSet getProblemSetEntityByEncoded(String encodedId) {
    long id = hashUtil.decode(encodedId);
    return problemSetRepository
        .findById(id)
        .orElseThrow(() -> new CustomException(ExceptionMessage.PROBLEM_SET_NOT_FOUND));
  }
}
