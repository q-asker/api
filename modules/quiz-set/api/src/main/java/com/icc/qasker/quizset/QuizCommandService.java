package com.icc.qasker.quizset;

import com.icc.qasker.quizset.dto.airesponse.ProblemSetGeneratedEvent.QuizGeneratedFromAI;
import com.icc.qasker.quizset.dto.ferequest.enums.QuizType;
import java.util.List;

public interface QuizCommandService {

  Long initProblemSet(
      String userId,
      String sessionId,
      String title,
      Integer totalQuizCount,
      QuizType quizType,
      String uploadUrl);

  void updateStatus(Long problemSetId, GenerationStatus status);

  List<Integer> saveBatch(List<QuizGeneratedFromAI> generatedProblems, Long problemSetId);
}
