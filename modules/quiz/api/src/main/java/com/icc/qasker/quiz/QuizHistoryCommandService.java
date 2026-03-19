package com.icc.qasker.quiz;

import com.icc.qasker.quiz.dto.ferequest.SaveHistoryRequest;

public interface QuizHistoryCommandService {

  String saveHistory(String userId, SaveHistoryRequest request);

  void deleteHistory(String userId, String problemSetId);
}
