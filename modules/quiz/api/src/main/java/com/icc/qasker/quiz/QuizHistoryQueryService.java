package com.icc.qasker.quiz;

import com.icc.qasker.quiz.dto.feresponse.HistoryDetailResponse;
import com.icc.qasker.quiz.dto.feresponse.HistorySummaryResponse;
import java.util.List;

public interface QuizHistoryQueryService {

  List<HistorySummaryResponse> getHistoryList(String userId);

  HistoryDetailResponse getHistoryDetail(String userId, String historyId);
}
