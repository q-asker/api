package com.icc.qasker.quizhistory;

import com.icc.qasker.quizhistory.dto.ferequest.HistoryScope;
import com.icc.qasker.quizhistory.dto.feresponse.EssayHistoryDetailResponse;
import com.icc.qasker.quizhistory.dto.feresponse.HistoryCheckResponse;
import com.icc.qasker.quizhistory.dto.feresponse.HistoryDetailResponse;
import com.icc.qasker.quizhistory.dto.feresponse.HistoryPageResponse;

public interface QuizHistoryQueryService {

  HistoryPageResponse getHistoryList(
      String userId, HistoryScope scope, String folderId, int page, int size);

  HistoryDetailResponse getHistoryDetail(String userId, String historyId);

  EssayHistoryDetailResponse getEssayHistoryDetail(String userId, String historyId);

  HistoryCheckResponse checkHistory(String userId, String problemSetId);
}
