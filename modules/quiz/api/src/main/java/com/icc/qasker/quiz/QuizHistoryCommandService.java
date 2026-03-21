package com.icc.qasker.quiz;

import com.icc.qasker.quiz.dto.ferequest.InitHistoryRequest;
import com.icc.qasker.quiz.dto.ferequest.SaveHistoryRequest;

public interface QuizHistoryCommandService {

  String initHistory(String userId, InitHistoryRequest request);

  String saveHistory(String userId, SaveHistoryRequest request);

  void deleteAllHistory(String userId);

  void deleteHistory(String userId, String historyId);

  void updateHistoryTitle(String userId, String historyId, String title);
}
