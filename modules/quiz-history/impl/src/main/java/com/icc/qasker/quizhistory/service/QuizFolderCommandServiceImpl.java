package com.icc.qasker.quizhistory.service;

import com.icc.qasker.global.component.HashUtil;
import com.icc.qasker.global.error.CustomException;
import com.icc.qasker.global.error.ExceptionMessage;
import com.icc.qasker.quizhistory.QuizFolderCommandService;
import com.icc.qasker.quizhistory.dto.feresponse.FolderResponse;
import com.icc.qasker.quizhistory.entity.QuizFolder;
import com.icc.qasker.quizhistory.repository.QuizFolderRepository;
import com.icc.qasker.quizhistory.repository.QuizHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class QuizFolderCommandServiceImpl implements QuizFolderCommandService {

  private static final int MAX_FOLDERS = 100;
  private static final int MAX_NAME_LENGTH = 50;

  private final QuizFolderRepository quizFolderRepository;
  private final QuizHistoryRepository quizHistoryRepository;
  private final HashUtil hashUtil;

  @Override
  @Transactional
  public FolderResponse createFolder(String userId, String name) {
    String trimmed = validateName(name);
    if (quizFolderRepository.countByUserId(userId) >= MAX_FOLDERS) {
      throw new CustomException(ExceptionMessage.FOLDER_LIMIT_EXCEEDED);
    }
    QuizFolder folder = QuizFolder.builder().userId(userId).name(trimmed).build();
    QuizFolder saved = quizFolderRepository.save(folder);
    return new FolderResponse(hashUtil.encode(saved.getId()), saved.getName());
  }

  @Override
  @Transactional
  public void renameFolder(String userId, String folderId, String name) {
    String trimmed = validateName(name);
    QuizFolder folder = loadOwnedFolder(userId, folderId);
    folder.rename(trimmed);
  }

  @Override
  @Transactional
  public void deleteFolder(String userId, String folderId) {
    QuizFolder folder = loadOwnedFolder(userId, folderId);
    quizHistoryRepository.clearFolderByFolderIdAndUserId(folder.getId(), userId);
    quizFolderRepository.delete(folder);
  }

  private QuizFolder loadOwnedFolder(String userId, String folderId) {
    return quizFolderRepository
        .findByIdAndUserId(hashUtil.decode(folderId), userId)
        .orElseThrow(() -> new CustomException(ExceptionMessage.FOLDER_NOT_FOUND));
  }

  private String validateName(String name) {
    if (name == null) {
      throw new CustomException(ExceptionMessage.FOLDER_NAME_INVALID);
    }
    String trimmed = name.trim();
    if (trimmed.isEmpty() || trimmed.length() > MAX_NAME_LENGTH) {
      throw new CustomException(ExceptionMessage.FOLDER_NAME_INVALID);
    }
    return trimmed;
  }
}
