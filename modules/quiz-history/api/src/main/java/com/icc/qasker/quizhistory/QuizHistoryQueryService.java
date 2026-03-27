package com.icc.qasker.quizhistory;

import com.icc.qasker.quizhistory.dto.feresponse.HistoryCheckResponse;
import com.icc.qasker.quizhistory.dto.feresponse.HistoryDetailResponse;
import com.icc.qasker.quizhistory.dto.feresponse.HistorySummaryResponse;
import java.util.List;

public interface QuizHistoryQueryService {

  List<HistorySummaryResponse> getHistoryList(String userId);

  HistoryDetailResponse getHistoryDetail(String userId, String historyId);

  HistoryCheckResponse checkHistory(String userId, String problemSetId);
}
