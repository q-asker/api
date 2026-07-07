package com.icc.qasker.quizset.service.generation.support;

import com.icc.qasker.global.error.CustomException;
import com.icc.qasker.global.error.ExceptionMessage;
import com.icc.qasker.quizset.GenerationStatus;
import com.icc.qasker.quizset.QuizCommandService;
import com.icc.qasker.quizset.dto.airesponse.ExplanationGeneratedFromAI;
import com.icc.qasker.quizset.dto.airesponse.ProblemSetGeneratedEvent.QuizGeneratedFromAI;
import com.icc.qasker.quizset.dto.ferequest.enums.QuizType;
import com.icc.qasker.quizset.entity.Problem;
import com.icc.qasker.quizset.entity.ProblemId;
import com.icc.qasker.quizset.entity.ProblemSet;
import com.icc.qasker.quizset.entity.Selection;
import com.icc.qasker.quizset.mapper.ProblemMapper;
import com.icc.qasker.quizset.repository.ProblemRepository;
import com.icc.qasker.quizset.repository.ProblemSetRepository;
import java.util.List;
import java.util.stream.IntStream;
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
      String uploadUrl,
      String customInstruction) {
    ProblemSet problemSet =
        ProblemSet.builder()
            .sessionId(sessionId)
            .title(title)
            .userId(userId)
            .totalQuizCount(totalQuizCount)
            .quizType(quizType)
            .fileUrl(uploadUrl)
            .customInstruction(customInstruction)
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

  @Override
  public void saveExplanation(Long problemSetId, ExplanationGeneratedFromAI explanation) {
    applyExplanation(problemSetId, explanation);
  }

  @Override
  public void saveExplanations(Long problemSetId, List<ExplanationGeneratedFromAI> explanations) {
    // N건을 단일 트랜잭션에서 처리 → flush·커밋 1회 (hibernate.jdbc.batch_size로 UPDATE 묶임)
    for (ExplanationGeneratedFromAI explanation : explanations) {
      applyExplanation(problemSetId, explanation);
    }
  }

  private void applyExplanation(Long problemSetId, ExplanationGeneratedFromAI explanation) {
    // 단일 문항 read-modify-write: 대상 1건만 로드해 해설 컬럼만 갱신(enhancement lazy + inline dirty tracking).
    Problem problem =
        problemRepository
            .findById(
                ProblemId.builder().problemSetId(problemSetId).number(explanation.number()).build())
            .orElse(null);
    if (problem == null) {
      log.warn(
          "[해설 후속 저장] 존재하지 않는 문항 번호 건너뜀 problemSetId={} number={}",
          problemSetId,
          explanation.number());
      return;
    }
    problem.updateExplanation(explanation.explanation());
    applySelectionExplanations(problem, explanation.selectionExplanations());
  }

  private void applySelectionExplanations(Problem problem, List<String> selectionExplanations) {
    List<Selection> selections = problem.getSelections();
    if (selectionExplanations == null || selectionExplanations.size() != selections.size()) {
      if (selectionExplanations != null) {
        log.warn(
            "[해설 후속 저장] 선지 해설 개수 불일치로 선지 해설 미갱신 problemId={} expected={} actual={}",
            problem.getId(),
            selections.size(),
            selectionExplanations.size());
      }
      return;
    }
    List<Selection> updated =
        IntStream.range(0, selections.size())
            .mapToObj(
                i ->
                    new Selection(
                        selections.get(i).content(),
                        selectionExplanations.get(i),
                        selections.get(i).correct()))
            .toList();
    problem.bindQuizData(updated, problem.getReferencedPages());
  }
}
