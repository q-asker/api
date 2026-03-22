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
    // 히스토리 기준 조회 — 히스토리가 없는 ProblemSet은 포함하지 않고,
    // 삭제된 히스토리는 자동으로 목록에서 제외됨
    List<QuizHistory> histories = quizHistoryRepository.findAllByUserIdOrderByCreatedAtDesc(userId);

    List<Long> problemSetIds = histories.stream().map(QuizHistory::getProblemSetId).toList();

    Map<Long, ProblemSet> problemSetMap =
        problemSetRepository.findAllById(problemSetIds).stream()
            .collect(Collectors.toMap(ProblemSet::getId, ps -> ps));

    return histories.stream()
        .map(
            h -> {
              ProblemSet ps = problemSetMap.get(h.getProblemSetId());
              // ProblemSet이 삭제된 경우 히스토리도 노출하지 않음
              if (ps == null) return null;
              return new HistorySummaryResponse(
                  hashUtil.encode(ps.getId()),
                  ps.getTitle(),
                  ps.getCreatedAt(),
                  hashUtil.encode(h.getId()),
                  ps.getQuizType(),
                  ps.getTotalQuizCount(),
                  true,
                  h.getScore(),
                  h.getCreatedAt());
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
