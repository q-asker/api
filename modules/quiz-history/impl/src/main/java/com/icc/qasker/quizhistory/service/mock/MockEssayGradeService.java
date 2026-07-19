package com.icc.qasker.quizhistory.service.mock;

import com.icc.qasker.ai.EssayGradingService;
import com.icc.qasker.ai.dto.EssayGradingResult;
import com.icc.qasker.global.component.HashUtil;
import com.icc.qasker.quizhistory.entity.EssayGradeLog;
import com.icc.qasker.quizhistory.repository.EssayGradeLogRepository;
import com.icc.qasker.quizhistory.service.EssayGradeService;
import com.icc.qasker.quizset.ProblemSetReadService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 부하 트레이스용 essay 채점 mock(@Profile("mock")). 문제 조회(읽기)는 실제로 태우고, AI 채점은 mock 채점기가 처리하며,
 * essay_grade_log write는 자기정리(save→delete)로 순증 0을 유지한다.
 */
@Service
@Primary
@Profile("mock")
@RequiredArgsConstructor
public class MockEssayGradeService implements EssayGradeService {

  private final ProblemSetReadService problemSetReadService;
  private final EssayGradingService essayGradingService;
  private final EssayGradeLogRepository essayGradeLogRepository;
  private final HashUtil hashUtil;

  @Override
  @Transactional
  public EssayGradingResult grade(
      String userId, String problemSetId, int problemNumber, String textAnswer, int attemptCount) {
    Long decodedProblemSetId = hashUtil.decode(problemSetId);

    // 읽기 트레이스: 실 서비스와 동일하게 문제 세트를 조회한다.
    problemSetReadService.findProblemsByProblemSetId(decodedProblemSetId);

    // AI 채점(mock 프로파일이면 MockEssayGradingService가 고정 결과 반환).
    EssayGradingResult result =
        essayGradingService.grade("mock", "mock", "mock", textAnswer, attemptCount);

    // 채점 로그 write 자기정리(save→delete).
    EssayGradeLog log =
        EssayGradeLog.builder()
            .userId(userId)
            .problemSetId(decodedProblemSetId)
            .problemNumber(problemNumber)
            .question("mock")
            .studentAnswer(textAnswer)
            .attemptCount(attemptCount)
            .totalScore(result.totalScore())
            .maxScore(result.maxScore())
            .elementScores(result.elementScores())
            .overallFeedback(result.overallFeedback())
            .evidenceJson(result.evidenceJson())
            .build();
    essayGradeLogRepository.save(log);
    essayGradeLogRepository.delete(log);

    return result;
  }
}
