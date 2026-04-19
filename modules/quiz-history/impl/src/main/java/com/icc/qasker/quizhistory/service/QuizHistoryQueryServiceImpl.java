package com.icc.qasker.quizhistory.service;

import com.icc.qasker.global.component.HashUtil;
import com.icc.qasker.global.error.CustomException;
import com.icc.qasker.global.error.ExceptionMessage;
import com.icc.qasker.quizhistory.QuizHistoryQueryService;
import com.icc.qasker.quizhistory.dto.feresponse.HistoryCheckResponse;
import com.icc.qasker.quizhistory.dto.feresponse.HistoryDetailResponse;
import com.icc.qasker.quizhistory.dto.feresponse.HistoryPageResponse;
import com.icc.qasker.quizhistory.dto.feresponse.HistorySummaryResponse;
import com.icc.qasker.quizhistory.dto.feresponse.ProblemWithAnswer;
import com.icc.qasker.quizhistory.entity.AnswerSnapshot;
import com.icc.qasker.quizhistory.entity.QuizHistory;
import com.icc.qasker.quizhistory.mapper.QuizHistoryMapper;
import com.icc.qasker.quizhistory.repository.QuizHistoryRepository;
import com.icc.qasker.quizset.ProblemSetReadService;
import com.icc.qasker.quizset.dto.feresponse.Selection;
import com.icc.qasker.quizset.dto.readonly.ProblemDetail;
import com.icc.qasker.quizset.dto.readonly.ProblemSetSummary;
import com.icc.qasker.quizset.dto.readonly.SelectionDetail;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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
  public HistoryPageResponse getHistoryList(String userId, int page, int size) {

    // 페이지 단위로 히스토리 조회
    Pageable pageable = Pageable.ofSize(size).withPage(page);
    Page<QuizHistory> historyPage =
        quizHistoryRepository.findAllByUserIdOrderByCreatedAtDesc(userId, pageable);

    List<QuizHistory> histories = historyPage.getContent();
    if (histories.isEmpty()) {
      return new HistoryPageResponse(
          List.of(),
          historyPage.getTotalElements(),
          historyPage.getTotalPages(),
          historyPage.getNumber(),
          historyPage.getSize());
    }

    // ProblemSet IN 쿼리 1번으로 일괄 조회 후 Map으로 변환
    List<Long> problemSetIds =
        histories.stream().map(QuizHistory::getProblemSetId).distinct().toList();
    Map<Long, ProblemSetSummary> problemSetMap =
        problemSetReadService.findProblemSetsByIds(problemSetIds).stream()
            .collect(Collectors.toMap(ProblemSetSummary::id, Function.identity()));

    List<HistorySummaryResponse> content =
        histories.stream()
            .map(
                history ->
                    quizHistoryMapper.toSummary(
                        history, problemSetMap.get(history.getProblemSetId())))
            .toList();

    return new HistoryPageResponse(
        content,
        historyPage.getTotalElements(),
        historyPage.getTotalPages(),
        historyPage.getNumber(),
        historyPage.getSize());
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
            .collect(Collectors.toMap(AnswerSnapshot::number, AnswerSnapshot::userAnswer));

    Map<Integer, Boolean> inReviewMap =
        history.getAnswers().stream()
            .collect(Collectors.toMap(AnswerSnapshot::number, AnswerSnapshot::inReview));

    List<ProblemWithAnswer> problemWithAnswers =
        problems.stream()
            .map(
                p -> {
                  boolean inReview = inReviewMap.getOrDefault(p.number(), false);
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
                      p.number(), p.title(), userAnswer, correct, inReview, selections);
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
        .findByUserIdAndProblemSetId(userId, hashUtil.decode(problemSetId))
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
