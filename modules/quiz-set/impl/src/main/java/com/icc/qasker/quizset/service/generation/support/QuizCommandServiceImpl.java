package com.icc.qasker.quizset.service.generation.support;

import com.icc.qasker.global.error.CustomException;
import com.icc.qasker.global.error.ExceptionMessage;
import com.icc.qasker.quizset.GenerationStatus;
import com.icc.qasker.quizset.QuizCommandService;
import com.icc.qasker.quizset.dto.airesponse.ProblemSetGeneratedEvent.QuizGeneratedFromAI;
import com.icc.qasker.quizset.dto.ferequest.enums.QuizType;
import com.icc.qasker.quizset.entity.Problem;
import com.icc.qasker.quizset.entity.ProblemSet;
import com.icc.qasker.quizset.mapper.ProblemMapper;
import com.icc.qasker.quizset.repository.ProblemRepository;
import com.icc.qasker.quizset.repository.ProblemSetRepository;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@AllArgsConstructor
@Transactional
public class QuizCommandServiceImpl implements QuizCommandService {

  private final ProblemSetRepository problemSetRepository;
  private final ProblemRepository problemRepository;
  private final ProblemMapper problemMapper;

  @Override
  public Long initProblemSet(
      String userId,
      String sessionId,
      String title,
      Integer totalQuizCount,
      QuizType quizType,
      String uploadUrl) {
    ProblemSet problemSet =
        ProblemSet.builder()
            .sessionId(sessionId)
            .title(title)
            .userId(userId)
            .totalQuizCount(totalQuizCount)
            .quizType(quizType)
            .fileUrl(uploadUrl)
            .build();
    ProblemSet saved = problemSetRepository.save(problemSet);
    return saved.getId();
  }

  @Override
  public void updateStatus(Long problemSetId, GenerationStatus status) {
    ProblemSet problemSet =
        problemSetRepository
            .findById(problemSetId)
            .orElseThrow(() -> new CustomException(ExceptionMessage.PROBLEM_SET_NOT_FOUND));
    problemSet.updateStatus(status);
  }

  @Override
  public List<Integer> saveBatch(List<QuizGeneratedFromAI> generatedProblems, Long problemSetId) {
    ProblemSet problemSet =
        problemSetRepository
            .findById(problemSetId)
            .orElseThrow(() -> new CustomException(ExceptionMessage.PROBLEM_SET_NOT_FOUND));

    List<Problem> problems =
        generatedProblems.stream()
            .map(quiz -> problemMapper.fromResponse(quiz, problemSet))
            .toList();

    List<Problem> savedProblems = problemRepository.saveAll(problems);
    return savedProblems.stream().map(problem -> problem.getId().getNumber()).toList();
  }
}
