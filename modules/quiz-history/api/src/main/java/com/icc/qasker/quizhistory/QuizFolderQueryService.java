package com.icc.qasker.quizhistory;

import com.icc.qasker.quizhistory.dto.feresponse.FolderListResponse;

public interface QuizFolderQueryService {

  FolderListResponse getFolders(String userId);
}
