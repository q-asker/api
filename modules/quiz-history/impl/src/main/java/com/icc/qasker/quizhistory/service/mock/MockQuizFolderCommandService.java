package com.icc.qasker.quizhistory.service.mock;

import com.icc.qasker.global.component.HashUtil;
import com.icc.qasker.quizhistory.QuizFolderCommandService;
import com.icc.qasker.quizhistory.dto.feresponse.FolderResponse;
import com.icc.qasker.quizhistory.entity.QuizFolder;
import com.icc.qasker.quizhistory.repository.QuizFolderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 부하 트레이스용 quiz-folder 커맨드 mock(@Profile("mock")). 다른 도메인 mock(MockQuizHistoryCommandService 등)과 동일
 * 패턴 — findByIdAndUserId·소유검증 없이 모든 write를 자기정리(save→delete) throwaway로 순증 0으로 태운다. folder만 이 mock이
 * 없어 실 서비스(조건부 delete)를 타 스케일마다 종단 write가 들쭉날쭉했다.
 */
@Service
@Primary
@Profile("mock")
@RequiredArgsConstructor
public class MockQuizFolderCommandService implements QuizFolderCommandService {

  private final QuizFolderRepository quizFolderRepository;
  private final HashUtil hashUtil;

  @Override
  @Transactional
  public FolderResponse createFolder(String userId, String name) {
    QuizFolder folder = QuizFolder.builder().userId(userId).name(name).build();
    quizFolderRepository.save(folder);
    String encodedId = hashUtil.encode(folder.getId());
    quizFolderRepository.delete(folder);
    return new FolderResponse(encodedId, name);
  }

  @Override
  public void renameFolder(String userId, String folderId, String name) {
    selfCleanWrite(userId, name);
  }

  @Override
  public void deleteFolder(String userId, String folderId) {
    selfCleanWrite(userId, "mock");
  }

  /** throwaway QuizFolder를 save→delete로 태워 종단 write SQL을 실 URI에 태그하되 데이터는 남기지 않는다. */
  @Transactional
  public void selfCleanWrite(String userId, String name) {
    QuizFolder folder = QuizFolder.builder().userId(userId).name(name).build();
    quizFolderRepository.save(folder);
    quizFolderRepository.delete(folder);
  }
}
