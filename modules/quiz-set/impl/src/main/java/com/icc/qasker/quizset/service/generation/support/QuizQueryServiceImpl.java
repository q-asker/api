package com.icc.qasker.quizset.service.generation.support;

import com.icc.qasker.global.error.CustomException;
import com.icc.qasker.global.error.ExceptionMessage;
import com.icc.qasker.quizset.GenerationStatus;
import com.icc.qasker.quizset.QuizQueryService;
import com.icc.qasker.quizset.dto.feresponse.ProblemSetResponse;
import com.icc.qasker.quizset.entity.Problem;
import com.icc.qasker.quizset.entity.ProblemId;
import com.icc.qasker.quizset.entity.ProblemSet;
import com.icc.qasker.quizset.mapper.ProblemSetResponseMapper;
import com.icc.qasker.quizset.mapper.ProblemToQuizViewMapper;
import com.icc.qasker.quizset.repository.ProblemRepository;
import com.icc.qasker.quizset.repository.ProblemSetRepository;
import com.icc.qasker.quizset.view.QuizView;
import java.util.List;
import java.util.Optional;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

@Service
@AllArgsConstructor
@Transactional(readOnly = true)
public class QuizQueryServiceImpl implements QuizQueryService {

  private final ProblemSetRepository problemSetRepository;
  private final ProblemRepository problemRepository;
  private final ProblemSetResponseMapper problemSetResponseMapper;

  @Override
  public List<QuizView> getQuizViews(long problemSetId, List<Integer> numbers) {
    List<ProblemId> problemIds =
        numbers.stream()
            .map(
                number -> {
                  return ProblemId.builder().problemSetId(problemSetId).number(number).build();
                })
            .toList();

    List<Problem> problems = problemRepository.findByIdInOrderByIdNumberAsc(problemIds);

    return problems.stream().map(ProblemToQuizViewMapper::toQuizView).toList();
  }

  @Override
  public Optional<GenerationStatus> getGenerationStatusBySessionId(String sessionId) {
    return problemSetRepository.findGenerationStatusBySessionId(sessionId);
  }

  @Override
  public ProblemSetResponse getMissedProblems(String sessionId, int lastQuizNumber) {
    Assert.hasText(sessionId, "sessionId must not be null");
    ProblemSet problemSet =
        problemSetRepository
            .findFirstBySessionIdOrderByCreatedAtDesc(sessionId)
            .orElseThrow(() -> new CustomException(ExceptionMessage.PROBLEM_SET_NOT_FOUND));

    List<Problem> remaining =
        problemRepository.findRemainingProblems(problemSet.getId(), lastQuizNumber);

    return problemSetResponseMapper.toResponse(problemSet, remaining);
  }
}
