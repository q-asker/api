package com.icc.qasker.quiz.service.history;

import com.icc.qasker.global.component.HashUtil;
import com.icc.qasker.global.error.CustomException;
import com.icc.qasker.global.error.ExceptionMessage;
import com.icc.qasker.quiz.QuizHistoryQueryService;
import com.icc.qasker.quiz.dto.feresponse.HistoryCheckResponse;
import com.icc.qasker.quiz.dto.feresponse.HistoryDetailResponse;
import com.icc.qasker.quiz.dto.feresponse.HistorySummaryResponse;
import com.icc.qasker.quiz.dto.feresponse.ProblemWithAnswer;
import com.icc.qasker.quiz.dto.feresponse.Selection;
import com.icc.qasker.quiz.entity.Problem;
import com.icc.qasker.quiz.entity.ProblemSet;
import com.icc.qasker.quiz.entity.QuizHistory;
import com.icc.qasker.quiz.mapper.QuizHistoryMapper;
import com.icc.qasker.quiz.repository.ProblemRepository;
import com.icc.qasker.quiz.repository.ProblemSetRepository;
import com.icc.qasker.quiz.repository.QuizHistoryRepository;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@AllArgsConstructor
@Transactional(readOnly = true)
public class QuizHistoryQueryServiceImpl implements QuizHistoryQueryService {

  private final QuizHistoryRepository quizHistoryRepository;
  private final ProblemSetRepository problemSetRepository;
  private final ProblemRepository problemRepository;
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
    Map<Long, ProblemSet> problemSetMap =
        problemSetRepository.findAllById(problemSetIds).stream()
            .collect(Collectors.toMap(ProblemSet::getId, Function.identity()));

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
    ProblemSet problemSet =
        problemSetRepository
            .findById(problemSetId)
            .orElseThrow(() -> new CustomException(ExceptionMessage.PROBLEM_SET_NOT_FOUND));

    List<Problem> problems = problemRepository.findByIdProblemSetId(problemSetId);

    Map<Integer, Integer> answerMap =
        history.getAnswers().stream()
            .collect(Collectors.toMap(a -> a.number(), a -> a.userAnswer()));

    List<ProblemWithAnswer> problemWithAnswers =
        problems.stream()
            .map(
                p -> {
                  int userAnswer = answerMap.getOrDefault(p.getId().getNumber(), 0);
                  var rawSelections = p.getSelections();
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
                      p.getId().getNumber(), p.getTitle(), userAnswer, correct, selections);
                })
            .toList();

    return new HistoryDetailResponse(
        hashUtil.encode(history.getId()),
        hashUtil.encode(problemSetId),
        problemSet.getQuizType(),
        problemSet.getTotalQuizCount(),
        history.getScore(),
        history.getTotalTime(),
        history.getCreatedAt(),
        problemWithAnswers);
  }

  @Override
  public HistoryCheckResponse checkHistory(String userId, String problemSetId) {
    long id = hashUtil.decode(problemSetId);
    return quizHistoryRepository
        .findFirstByProblemSetIdAndUserIdOrderByCreatedAtDesc(id, userId)
        .map(h -> new HistoryCheckResponse(true, hashUtil.encode(h.getId()), h.getTitle()))
        .orElse(new HistoryCheckResponse(false, null, null));
  }

  private int findCorrectIndex(List<com.icc.qasker.quiz.entity.Selection> selections) {
    for (int i = 0; i < selections.size(); i++) {
      if (selections.get(i).correct()) {
        return i + 1;
      }
    }
    return -1;
  }
}
