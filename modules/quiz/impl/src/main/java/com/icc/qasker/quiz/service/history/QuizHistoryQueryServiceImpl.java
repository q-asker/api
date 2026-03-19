package com.icc.qasker.quiz.service.history;

import com.icc.qasker.global.component.HashUtil;
import com.icc.qasker.global.error.CustomException;
import com.icc.qasker.global.error.ExceptionMessage;
import com.icc.qasker.quiz.QuizHistoryQueryService;
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
    List<ProblemSet> problemSets = problemSetRepository.findAllByUserIdOrderByCreatedAtDesc(userId);

    List<Long> problemSetIds = problemSets.stream().map(ProblemSet::getId).toList();

    // IN 쿼리 1번으로 모든 히스토리 일괄 조회
    Map<Long, QuizHistory> historyMap =
        quizHistoryRepository.findAllByProblemSetIdInAndUserId(problemSetIds, userId).stream()
            .collect(Collectors.toMap(QuizHistory::getProblemSetId, h -> h));

    return problemSets.stream()
        .filter(
            ps -> {
              QuizHistory h = historyMap.get(ps.getId());
              // 소프트 딜리트된 항목은 목록에서 제외
              return h == null || !h.isDeleted();
            })
        .map(
            ps -> {
              QuizHistory history = historyMap.get(ps.getId());
              boolean completed = history != null;
              return new HistorySummaryResponse(
                  hashUtil.encode(ps.getId()),
                  ps.getTitle(),
                  completed ? hashUtil.encode(history.getId()) : null,
                  ps.getQuizType(),
                  ps.getTotalQuizCount(),
                  completed,
                  completed ? history.getScore() : null,
                  completed ? history.getCreatedAt() : null);
            })
        .toList();
  }

  @Override
  public HistoryDetailResponse getHistoryDetail(String userId, String problemSetId) {
    long id = hashUtil.decode(problemSetId);
    QuizHistory history =
        quizHistoryRepository
            .findFirstByProblemSetIdAndUserIdAndDeletedFalseOrderByCreatedAtDesc(id, userId)
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

                  // indexOf 대신 IntStream으로 index 직접 추적 (O(n²) 방지)
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
