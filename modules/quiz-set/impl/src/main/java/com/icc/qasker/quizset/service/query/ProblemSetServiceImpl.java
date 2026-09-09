package com.icc.qasker.quizset.service.query;

import com.icc.qasker.global.component.HashUtil;
import com.icc.qasker.global.error.CustomException;
import com.icc.qasker.global.error.ExceptionMessage;
import com.icc.qasker.quizset.ProblemSetOrigin;
import com.icc.qasker.quizset.ProblemSetService;
import com.icc.qasker.quizset.dto.ferequest.ChangeTitleRequest;
import com.icc.qasker.quizset.dto.feresponse.ChangeTitleResponse;
import com.icc.qasker.quizset.dto.feresponse.ProblemSetResponse;
import com.icc.qasker.quizset.dto.feresponse.RegenerationConditionResponse;
import com.icc.qasker.quizset.entity.ProblemSet;
import com.icc.qasker.quizset.mapper.ProblemSetResponseMapper;
import com.icc.qasker.quizset.repository.ProblemSetRepository;
import java.util.List;
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
  public RegenerationConditionResponse getRegenerationCondition(String problemSetId) {
    Assert.hasText(problemSetId, "problemSetId must not be blank");
    ProblemSet ps = getProblemSetEntityByEncoded(problemSetId);
    // 자료 능동 만료검사는 이번 스코프 미도입 → 자료 기반 세트는 documentAvailable 항상 true(후속에 실 판정으로 대체).
    // 오답 모아풀기로 만든 세트는 근거 자료 자체가 없으므로 false. 404로 막지 않는 것은 의도다 — 프론트가 이 응답을 받아
    // 폴백 경로로 degrade 하고, 진입점을 실제로 감추는 것은 응답의 origin 을 보는 프론트 쪽 판단이다.
    boolean documentAvailable = ps.getOrigin() != ProblemSetOrigin.WRONG_ANSWER;
    return new RegenerationConditionResponse(
        ps.getQuizType(),
        ps.getTotalQuizCount(),
        emptyToNull(ps.getPageNumbers()),
        ps.getLanguage(),
        ps.getCustomInstruction(),
        ps.getFileUrl(),
        ps.getTitle(),
        documentAvailable);
  }

  // legacy 세트는 컬럼 NULL이 IntegerListConverter를 거쳐 빈 리스트로 읽힌다. 계약대로 null로 정규화해 프론트 폴백 판정을 명확히 한다.
  private static List<Integer> emptyToNull(List<Integer> pageNumbers) {
    return (pageNumbers == null || pageNumbers.isEmpty()) ? null : pageNumbers;
  }

  @Override
  @Transactional
  public ChangeTitleResponse changeProblemSetTitle(
      String userId, String problemSetId, ChangeTitleRequest request) {
    ProblemSet ps = getProblemSetEntityByEncoded(problemSetId);
    if (ps.getUserId() == null || !ps.getUserId().equals(userId)) {
      throw new CustomException(ExceptionMessage.NOT_ENOUGH_ACCESS);
    }
    ps.updateTitle(request.title());
    return new ChangeTitleResponse(ps.getTitle());
  }

  private ProblemSet getProblemSetEntityByEncoded(String encodedId) {
    long id = hashUtil.decode(encodedId);
    return problemSetRepository
        .findById(id)
        .orElseThrow(() -> new CustomException(ExceptionMessage.PROBLEM_SET_NOT_FOUND));
  }
}
