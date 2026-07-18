package com.icc.qasker.quizhistory.service;

import com.icc.qasker.ai.EssayGradingService;
import com.icc.qasker.ai.dto.EssayGradingResult;
import com.icc.qasker.global.component.HashUtil;
import com.icc.qasker.global.error.CustomException;
import com.icc.qasker.global.error.ExceptionMessage;
import com.icc.qasker.quizhistory.entity.EssayGradeLog;
import com.icc.qasker.quizhistory.repository.EssayGradeLogRepository;
import com.icc.qasker.quizset.ProblemSetReadService;
import com.icc.qasker.quizset.dto.readonly.ProblemDetail;
import com.icc.qasker.quizset.dto.readonly.SelectionDetail;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/** ESSAY 채점 서비스. Problem 조회 → AI 채점 → 응답 반환. */
@Slf4j
@Service
@RequiredArgsConstructor
public class EssayGradeService {

  private final ProblemSetReadService problemSetReadService;
  private final EssayGradingService essayGradingService;
  private final EssayGradeLogRepository essayGradeLogRepository;
  private final HashUtil hashUtil;

  public EssayGradingResult grade(
      String userId, String problemSetId, int problemNumber, String textAnswer, int attemptCount) {
    Long decodedProblemSetId = hashUtil.decode(problemSetId);

    // 문제 조회
    List<ProblemDetail> problems =
        problemSetReadService.findProblemsByProblemSetId(decodedProblemSetId);
    ProblemDetail problem =
        problems.stream()
            .filter(p -> p.number() == problemNumber)
            .findFirst()
            .orElseThrow(() -> new CustomException(ExceptionMessage.PROBLEM_SET_NOT_FOUND));

    // 모범답안 추출 (selections[0].content)
    String modelAnswer =
        problem.selections().stream()
            .filter(SelectionDetail::correct)
            .map(SelectionDetail::content)
            .findFirst()
            .orElseThrow(() -> new CustomException(ExceptionMessage.PROBLEM_SET_NOT_FOUND));

    // 루브릭 추출 (explanationContent)
    String rubric = problem.explanationContent();
    if (rubric == null || rubric.isBlank()) {
      throw new CustomException(ExceptionMessage.PROBLEM_SET_NOT_FOUND);
    }

    // AI 채점 (시도 횟수에 따라 피드백 구체성 차등 적용)
    EssayGradingResult result =
        essayGradingService.grade(problem.title(), modelAnswer, rubric, textAnswer, attemptCount);

    // 로그 저장 (동기 — 채점 결과 내구성 보장: 저장 실패 시 요청도 실패)
    saveLog(
        userId,
        decodedProblemSetId,
        problemNumber,
        problem.title(),
        textAnswer,
        attemptCount,
        result);

    return result;
  }

  private void saveLog(
      String userId,
      Long problemSetId,
      int problemNumber,
      String question,
      String studentAnswer,
      int attemptCount,
      EssayGradingResult result) {
    EssayGradeLog essayGradeLog =
        EssayGradeLog.builder()
            .userId(userId)
            .problemSetId(problemSetId)
            .problemNumber(problemNumber)
            .question(question)
            .studentAnswer(studentAnswer)
            .attemptCount(attemptCount)
            .totalScore(result.totalScore())
            .maxScore(result.maxScore())
            .elementScores(result.elementScores())
            .overallFeedback(result.overallFeedback())
            .evidenceJson(result.evidenceJson())
            .build();

    essayGradeLogRepository.save(essayGradeLog);
  }
}
