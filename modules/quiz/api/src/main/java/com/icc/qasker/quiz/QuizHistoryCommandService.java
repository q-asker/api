package com.icc.qasker.quiz;

import com.icc.qasker.quiz.dto.ferequest.SaveHistoryRequest;

public interface QuizHistoryCommandService {

  void initHistory(String userId, Long problemSetId);

  String saveHistory(String userId, SaveHistoryRequest request);

  void deleteAllHistory(String userId);

  void deleteSpecificHistory(String userId, String problemSetId);

  void updateHistoryTitle(String userId, String historyId, String title);
}
