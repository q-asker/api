package com.icc.qasker.quizhistory.service;

import com.icc.qasker.global.component.HashUtil;
import com.icc.qasker.global.error.CustomException;
import com.icc.qasker.global.error.ExceptionMessage;
import com.icc.qasker.quiz.ProblemSetReadService;
import com.icc.qasker.quiz.dto.feresponse.Selection;
import com.icc.qasker.quiz.dto.readonly.ProblemDetail;
import com.icc.qasker.quiz.dto.readonly.ProblemSetSummary;
import com.icc.qasker.quiz.dto.readonly.SelectionDetail;
import com.icc.qasker.quizhistory.QuizHistoryQueryService;
import com.icc.qasker.quizhistory.dto.feresponse.HistoryCheckResponse;
import com.icc.qasker.quizhistory.dto.feresponse.HistoryDetailResponse;
import com.icc.qasker.quizhistory.dto.feresponse.HistorySummaryResponse;
import com.icc.qasker.quizhistory.dto.feresponse.ProblemWithAnswer;
import com.icc.qasker.quizhistory.entity.QuizHistory;
import com.icc.qasker.quizhistory.mapper.QuizHistoryMapper;
import com.icc.qasker.quizhistory.repository.QuizHistoryRepository;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class QuizHistoryQueryServiceImpl implements QuizHistoryQueryService {

  private final QuizHistoryRepository quizHistoryRepository;
  private final ProblemSetReadService problemSetReadService;
  private final HashUtil hashUtil;
  private final QuizHistoryMapper quizHistoryMapper;

  @Override
  public List<HistorySummaryResponse> getHistoryList(String userId) {

    // IN 쿼리 1번으로 모든 히스토리 일괄 조회
    List<QuizHistory> histories = quizHistoryRepository.findAllByUserId(userId);
    if (histories.isEmpty()) {
      return List.of();
    }

    // ProblemSet IN 쿼리 1번으로 일괄 조회 후 Map으로 변환
    List<Long> problemSetIds =
        histories.stream().map(QuizHistory::getProblemSetId).distinct().toList();
    Map<Long, ProblemSetSummary> problemSetMap =
        problemSetReadService.findProblemSetsByIds(problemSetIds).stream()
            .collect(Collectors.toMap(ProblemSetSummary::id, Function.identity()));

    return histories.stream()
        .filter(history -> problemSetMap.containsKey(history.getProblemSetId()))
        .map(
            history ->
                quizHistoryMapper.toSummary(history, problemSetMap.get(history.getProblemSetId())))
        .toList();
  }

  @Override
  public HistoryDetailResponse getHistoryDetail(String userId, String historyId) {
    long id = hashUtil.decode(historyId);
    QuizHistory history =
        quizHistoryRepository
            .findById(id)
            .filter(h -> h.getUserId().equals(userId))
            .orElseThrow(() -> new CustomException(ExceptionMessage.QUIZ_HISTORY_NOT_FOUND));

    Long problemSetId = history.getProblemSetId();
    ProblemSetSummary problemSet =
        problemSetReadService
            .findProblemSetById(problemSetId)
            .orElseThrow(() -> new CustomException(ExceptionMessage.PROBLEM_SET_NOT_FOUND));

    List<ProblemDetail> problems = problemSetReadService.findProblemsByProblemSetId(problemSetId);

    Map<Integer, Integer> answerMap =
        history.getAnswers().stream()
            .collect(Collectors.toMap(a -> a.number(), a -> a.userAnswer()));

    List<ProblemWithAnswer> problemWithAnswers =
        problems.stream()
            .map(
                p -> {
                  int userAnswer = answerMap.getOrDefault(p.number(), 0);
                  List<SelectionDetail> rawSelections = p.selections();
                  int correctIndex = findCorrectIndex(rawSelections);
                  boolean correct = userAnswer == correctIndex;

                  List<Selection> selections =
                      IntStream.range(0, rawSelections.size())
                          .mapToObj(
                              i ->
                                  new Selection(
                                      i + 1,
                                      rawSelections.get(i).content(),
                                      rawSelections.get(i).correct()))
                          .toList();

                  return new ProblemWithAnswer(
                      p.number(), p.title(), userAnswer, correct, selections);
                })
            .toList();

    return new HistoryDetailResponse(
        hashUtil.encode(history.getId()),
        hashUtil.encode(problemSetId),
        problemSet.quizType(),
        problemSet.totalQuizCount(),
        history.getScore(),
        history.getTotalTime(),
        history.getCreatedAt(),
        problemWithAnswers);
  }

  @Override
  public HistoryCheckResponse checkHistory(String userId, String problemSetId) {
    long id = hashUtil.decode(problemSetId);
    return quizHistoryRepository
        .findLatestByProblemSetAndUser(id, userId)
        .map(h -> new HistoryCheckResponse(true, hashUtil.encode(h.getId()), h.getTitle()))
        .orElse(new HistoryCheckResponse(false, null, null));
  }

  private int findCorrectIndex(List<SelectionDetail> selections) {
    for (int i = 0; i < selections.size(); i++) {
      if (selections.get(i).correct()) {
        return i + 1;
      }
    }
    return -1;
  }
}
