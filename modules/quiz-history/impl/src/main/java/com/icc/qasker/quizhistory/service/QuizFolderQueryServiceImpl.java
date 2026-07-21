package com.icc.qasker.quizhistory.service;

import com.icc.qasker.global.component.HashUtil;
import com.icc.qasker.quizhistory.QuizFolderQueryService;
import com.icc.qasker.quizhistory.dto.feresponse.FolderListResponse;
import com.icc.qasker.quizhistory.dto.feresponse.FolderSummaryResponse;
import com.icc.qasker.quizhistory.entity.QuizFolder;
import com.icc.qasker.quizhistory.repository.FolderCount;
import com.icc.qasker.quizhistory.repository.QuizFolderRepository;
import com.icc.qasker.quizhistory.repository.QuizHistoryRepository;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class QuizFolderQueryServiceImpl implements QuizFolderQueryService {

  private final QuizFolderRepository quizFolderRepository;
  private final QuizHistoryRepository quizHistoryRepository;
  private final HashUtil hashUtil;

  @Override
  public FolderListResponse getFolders(String userId) {
    List<QuizFolder> folders = quizFolderRepository.findAllByUserIdOrderByCreatedAtDesc(userId);
    Map<Long, Long> countMap =
        quizHistoryRepository.countGroupedByFolder(userId).stream()
            .collect(Collectors.toMap(FolderCount::getFolderId, FolderCount::getCount));

    List<FolderSummaryResponse> summaries =
        folders.stream()
            .map(
                f ->
                    new FolderSummaryResponse(
                        hashUtil.encode(f.getId()),
                        f.getName(),
                        countMap.getOrDefault(f.getId(), 0L)))
            .toList();

    long unclassifiedCount = quizHistoryRepository.countByUserIdAndFolderIdIsNull(userId);
    return new FolderListResponse(summaries, unclassifiedCount);
  }
}
