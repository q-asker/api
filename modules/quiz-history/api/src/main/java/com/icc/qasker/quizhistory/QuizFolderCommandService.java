package com.icc.qasker.quizhistory;

import com.icc.qasker.quizhistory.dto.feresponse.FolderResponse;

public interface QuizFolderCommandService {

  FolderResponse createFolder(String userId, String name);

  void renameFolder(String userId, String folderId, String name);

  void deleteFolder(String userId, String folderId);
}
