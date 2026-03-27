package com.icc.qasker.quizhistory.service;

import com.icc.qasker.global.component.HashUtil;
import com.icc.qasker.global.error.CustomException;
import com.icc.qasker.global.error.ExceptionMessage;
import com.icc.qasker.quizhistory.QuizHistoryCommandService;
import com.icc.qasker.quizhistory.converter.AnswerSnapshotConverter;
import com.icc.qasker.quizhistory.dto.ferequest.InitHistoryRequest;
import com.icc.qasker.quizhistory.dto.ferequest.SaveHistoryRequest;
import com.icc.qasker.quizhistory.entity.AnswerSnapshot;
import com.icc.qasker.quizhistory.entity.QuizHistory;
import com.icc.qasker.quizhistory.repository.QuizHistoryRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class QuizHistoryCommandServiceImpl implements QuizHistoryCommandService {

  private static final AnswerSnapshotConverter ANSWER_SNAPSHOT_CONVERTER =
      new AnswerSnapshotConverter();

  private final QuizHistoryRepository quizHistoryRepository;
  private final HashUtil hashUtil;

  @Override
  public String initHistory(String userId, InitHistoryRequest request) {
    long problemSetId = hashUtil.decode(request.problemSetId());

    // upsert: 있으면 초기화, 없으면 새로 생성 (atomic)
    quizHistoryRepository.upsertInitHistory(userId, problemSetId, request.title());

    QuizHistory history =
        quizHistoryRepository
            .findLatestByProblemSetAndUser(problemSetId, userId)
            .orElseThrow(() -> new CustomException(ExceptionMessage.QUIZ_HISTORY_NOT_FOUND));
    return hashUtil.encode(history.getId());
  }

  @Override
  public String saveHistory(String userId, SaveHistoryRequest request) {
    long id = hashUtil.decode(request.problemSetId());
    List<AnswerSnapshot> snapshots =
        request.userAnswers().stream()
            .map(a -> new AnswerSnapshot(a.number(), a.userAnswer()))
            .toList();
    String answersJson = ANSWER_SNAPSHOT_CONVERTER.convertToDatabaseColumn(snapshots);
    quizHistoryRepository.upsertSaveHistory(userId, id, answersJson, request.score());
    return quizHistoryRepository
        .fi
        .map(hashUtil::encode)
        .orElseThrow(() -> new CustomException(ExceptionMessage.QUIZ_HISTORY_NOT_FOUND));
    return "";
  }

  @Override
  public void updateHistoryTitle(String userId, String historyId, String title) {
    long decodedHistoryId = hashUtil.decode(historyId);
    QuizHistory history =
        quizHistoryRepository
            .findById(decodedHistoryId)
            .filter(h -> h.getUserId().equals(userId))
            .orElseThrow(() -> new CustomException(ExceptionMessage.QUIZ_HISTORY_NOT_FOUND));
    history.updateTitle(title);
  }

  @Override
  public void deleteAllHistory(String userId) {
    quizHistoryRepository.deleteAllByUserId(userId);
  }

  @Override
  public void deleteHistory(String userId, String historyId) {
    long id = hashUtil.decode(historyId);
    quizHistoryRepository
        .findById(id)
        .filter(h -> h.getUserId().equals(userId))
        .orElseThrow(() -> new CustomException(ExceptionMessage.QUIZ_HISTORY_NOT_FOUND));
    quizHistoryRepository.deleteById(id);
  }
}
