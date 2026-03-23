package com.icc.qasker.quiz.service.history;

import com.icc.qasker.global.component.HashUtil;
import com.icc.qasker.global.error.CustomException;
import com.icc.qasker.global.error.ExceptionMessage;
import com.icc.qasker.quiz.QuizHistoryQueryService;
import com.icc.qasker.quiz.QuizHistoryStatus;
import com.icc.qasker.quiz.dto.feresponse.HistoryDetailResponse;
import com.icc.qasker.quiz.dto.feresponse.HistorySummaryResponse;
import com.icc.qasker.quiz.dto.feresponse.ProblemWithAnswer;
import com.icc.qasker.quiz.dto.feresponse.Selection;
import com.icc.qasker.quiz.entity.Problem;
import com.icc.qasker.quiz.entity.ProblemSet;
import com.icc.qasker.quiz.entity.QuizHistory;
import com.icc.qasker.quiz.repository.ProblemRepository;
import com.icc.qasker.quiz.repository.ProblemSetRepository;
import com.icc.qasker.quiz.repository.QuizHistoryRepository;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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

  @Override
  public List<HistorySummaryResponse> getHistoryList(String userId) {
    List<QuizHistory> histories = quizHistoryRepository.findAllByUserIdOrderByCreatedAtDesc(userId);
    List<Long> problemSetIds = histories.stream().map(QuizHistory::getProblemSetId).toList();

    Map<Long, ProblemSet> problemSetMap =
        problemSetRepository.findAllById(problemSetIds).stream()
            .collect(Collectors.toMap(ProblemSet::getId, ps -> ps));

    return histories.stream()
        .map(
            h -> {
              ProblemSet ps = problemSetMap.get(h.getProblemSetId());
              if (ps == null) {
                return null;
              }
              boolean completed = h.getStatus() == QuizHistoryStatus.COMPLETED;
              return new HistorySummaryResponse(
                  hashUtil.encode(ps.getId()),
                  ps.getTitle(),
                  ps.getCreatedAt(),
                  completed ? hashUtil.encode(h.getId()) : null,
                  ps.getQuizType(),
                  ps.getTotalQuizCount(),
                  completed,
                  completed ? h.getScore() : null,
                  completed ? h.getCreatedAt() : null);
            })
        .filter(Objects::nonNull)
        .toList();
  }

  @Override
  public HistoryDetailResponse getHistoryDetail(String userId, String problemSetId) {
    long id = hashUtil.decode(problemSetId);
    QuizHistory history =
        quizHistoryRepository
            .findByProblemSetIdAndUserId(id, userId)
            .filter(h -> h.getStatus() == QuizHistoryStatus.COMPLETED)
            .orElseThrow(() -> new CustomException(ExceptionMessage.QUIZ_HISTORY_NOT_FOUND));

    ProblemSet problemSet =
        problemSetRepository
            .findById(id)
            .orElseThrow(() -> new CustomException(ExceptionMessage.PROBLEM_SET_NOT_FOUND));

    List<Problem> problems = problemRepository.findByIdProblemSetId(id);

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
        problemSet.getQuizType(),
        problemSet.getTotalQuizCount(),
        history.getScore(),
        history.getCreatedAt(),
        problemWithAnswers);
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
