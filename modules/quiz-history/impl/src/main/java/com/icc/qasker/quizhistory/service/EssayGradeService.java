package com.icc.qasker.quizhistory.service;

import com.icc.qasker.ai.dto.EssayGradingResult;

/**
 * ESSAY 채점. mock 프로파일에서 자기정리(save→delete) 구현({@code MockEssayGradeService})으로 교체돼, 문제 조회는 실제로 태우고
 * 채점 로그는 순증 0으로 남긴다.
 */
public interface EssayGradeService {

  EssayGradingResult grade(
      String userId, String problemSetId, int problemNumber, String textAnswer, int attemptCount);
}
