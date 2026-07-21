package com.icc.qasker.quizhistory.service;

import com.icc.qasker.global.component.HashUtil;
import com.icc.qasker.global.error.CustomException;
import com.icc.qasker.global.error.ExceptionMessage;
import com.icc.qasker.quizhistory.QuizHistoryQueryService;
import com.icc.qasker.quizhistory.dto.ferequest.HistoryScope;
import com.icc.qasker.quizhistory.dto.feresponse.EssayHistoryDetailResponse;
import com.icc.qasker.quizhistory.dto.feresponse.EssayHistoryDetailResponse.EssayProblemWithGrade;
import com.icc.qasker.quizhistory.dto.feresponse.HistoryCheckResponse;
import com.icc.qasker.quizhistory.dto.feresponse.HistoryDetailResponse;
import com.icc.qasker.quizhistory.dto.feresponse.HistoryPageResponse;
import com.icc.qasker.quizhistory.dto.feresponse.HistorySummaryResponse;
import com.icc.qasker.quizhistory.dto.feresponse.ProblemWithAnswer;
import com.icc.qasker.quizhistory.entity.AnswerSnapshotView;
import com.icc.qasker.quizhistory.entity.EssayGradeLog;
import com.icc.qasker.quizhistory.entity.QuizFolder;
import com.icc.qasker.quizhistory.entity.QuizHistory;
import com.icc.qasker.quizhistory.mapper.QuizHistoryMapper;
import com.icc.qasker.quizhistory.repository.EssayGradeLogRepository;
import com.icc.qasker.quizhistory.repository.QuizFolderRepository;
import com.icc.qasker.quizhistory.repository.QuizHistoryRepository;
import com.icc.qasker.quizset.ProblemSetReadService;
import com.icc.qasker.quizset.dto.readonly.ProblemDetail;
import com.icc.qasker.quizset.dto.readonly.ProblemSetSummary;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class QuizHistoryQueryServiceImpl implements QuizHistoryQueryService {

  private final QuizHistoryRepository quizHistoryRepository;
  private final QuizFolderRepository quizFolderRepository;
  private final EssayGradeLogRepository essayGradeLogRepository;
  private final ProblemSetReadService problemSetReadService;
  private final HashUtil hashUtil;
  private final QuizHistoryMapper quizHistoryMapper;

  @Override
  public HistoryPageResponse getHistoryList(
      String userId, HistoryScope scope, String folderId, int page, int size) {

    // 페이지 단위로 히스토리 조회 (탐색 범위 3종: 전체/미분류/특정 폴더)
    Pageable pageable = Pageable.ofSize(size).withPage(page);
    Page<QuizHistory> historyPage = loadHistoryPage(userId, scope, folderId, pageable);

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

    // 소속 폴더명 IN 쿼리 1번으로 일괄 조회 후 Map으로 변환 (미분류 기록은 조회 대상 아님)
    List<Long> folderIds =
        histories.stream()
            .map(QuizHistory::getFolderId)
            .filter(Objects::nonNull)
            .distinct()
            .toList();
    Map<Long, String> folderNameMap =
        folderIds.isEmpty()
            ? Map.of()
            : quizFolderRepository.findAllById(folderIds).stream()
                .collect(Collectors.toMap(QuizFolder::getId, QuizFolder::getName));

    List<HistorySummaryResponse> content =
        histories.stream()
            .map(
                history -> {
                  ProblemSetSummary problemSet = problemSetMap.get(history.getProblemSetId());
                  if (problemSet == null) {
                    return null;
                  }
                  String folderName =
                      history.getFolderId() == null
                          ? null
                          : folderNameMap.get(history.getFolderId());
                  return quizHistoryMapper.toSummary(history, problemSet, folderName);
                })
            .filter(Objects::nonNull)
            .toList();

    return new HistoryPageResponse(
        content,
        historyPage.getTotalElements(),
        historyPage.getTotalPages(),
        historyPage.getNumber(),
        historyPage.getSize());
  }

  private Page<QuizHistory> loadHistoryPage(
      String userId, HistoryScope scope, String folderId, Pageable pageable) {
    return switch (scope) {
      case ALL -> quizHistoryRepository.findAllByUserIdOrderByCreatedAtDesc(userId, pageable);
      case UNCLASSIFIED ->
          quizHistoryRepository.findAllByUserIdAndFolderIdIsNullOrderByCreatedAtDesc(
              userId, pageable);
      case FOLDER -> {
        if (folderId == null || folderId.isBlank()) {
          throw new CustomException(HttpStatus.BAD_REQUEST, "folderId가 필요합니다.");
        }
        yield quizHistoryRepository.findAllByUserIdAndFolderIdOrderByCreatedAtDesc(
            userId, hashUtil.decode(folderId), pageable);
      }
    };
  }

  @Override
  public HistoryDetailResponse getHistoryDetail(String userId, String historyId) {
    QuizHistory history = loadOwnedHistory(userId, historyId);
    Long problemSetId = history.getProblemSetId();
    ProblemSetSummary problemSet = loadProblemSet(problemSetId);
    List<ProblemDetail> problems = problemSetReadService.findProblemsByProblemSetId(problemSetId);

    AnswerSnapshotView answers = AnswerSnapshotView.from(history.getAnswers());
    List<ProblemWithAnswer> problemWithAnswers =
        problems.stream().map(p -> quizHistoryMapper.toProblemWithAnswer(p, answers)).toList();

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
  public EssayHistoryDetailResponse getEssayHistoryDetail(String userId, String historyId) {
    QuizHistory history = loadOwnedHistory(userId, historyId);
    Long problemSetId = history.getProblemSetId();
    ProblemSetSummary problemSet = loadProblemSet(problemSetId);
    List<ProblemDetail> problems = problemSetReadService.findProblemsByProblemSetId(problemSetId);

    AnswerSnapshotView answers = AnswerSnapshotView.from(history.getAnswers());

    // 문제별 최신 채점 결과 조회
    Map<Integer, EssayGradeLog> gradeLogMap =
        essayGradeLogRepository.findLatestByUserIdAndProblemSetId(userId, problemSetId).stream()
            .collect(Collectors.toMap(EssayGradeLog::getProblemNumber, Function.identity()));

    List<EssayProblemWithGrade> essayProblems =
        problems.stream()
            .map(
                p ->
                    quizHistoryMapper.toEssayProblemWithGrade(
                        p, answers, gradeLogMap.get(p.number())))
            .toList();

    return new EssayHistoryDetailResponse(
        hashUtil.encode(history.getId()),
        hashUtil.encode(problemSetId),
        problemSet.quizType(),
        problemSet.totalQuizCount(),
        history.getTotalTime(),
        history.getCreatedAt(),
        essayProblems);
  }

  @Override
  public HistoryCheckResponse checkHistory(String userId, String problemSetId) {
    long id = hashUtil.decode(problemSetId);
    return quizHistoryRepository
        .findByUserIdAndProblemSetId(userId, hashUtil.decode(problemSetId))
        .map(h -> new HistoryCheckResponse(true, hashUtil.encode(h.getId()), h.getTitle()))
        .orElse(new HistoryCheckResponse(false, null, null));
  }

  private QuizHistory loadOwnedHistory(String userId, String historyId) {
    long id = hashUtil.decode(historyId);
    return quizHistoryRepository
        .findById(id)
        .filter(h -> h.getUserId().equals(userId))
        .orElseThrow(() -> new CustomException(ExceptionMessage.QUIZ_HISTORY_NOT_FOUND));
  }

  private ProblemSetSummary loadProblemSet(Long problemSetId) {
    return problemSetReadService
        .findProblemSetById(problemSetId)
        .orElseThrow(() -> new CustomException(ExceptionMessage.PROBLEM_SET_NOT_FOUND));
  }
}
