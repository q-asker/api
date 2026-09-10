package com.icc.qasker.quizset;

import com.icc.qasker.global.quiz.QuizType;
import com.icc.qasker.quizset.dto.airesponse.ProblemSetGeneratedEvent.QuizGeneratedFromAI;
import java.util.List;

public interface QuizCommandService {

  Long initProblemSet(
      String userId,
      String sessionId,
      String title,
      Integer totalQuizCount,
      QuizType quizType,
      String uploadUrl,
      String customInstruction,
      List<Integer> pageNumbers,
      String language);

  void updateStatus(Long problemSetId, GenerationStatus status);

  List<Integer> saveBatch(List<QuizGeneratedFromAI> generatedProblems, Long problemSetId);
}
