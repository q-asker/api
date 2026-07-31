package com.icc.qasker.quizhistory.service.mock;

import com.icc.qasker.global.component.HashUtil;
import com.icc.qasker.quizhistory.QuizFolderCommandService;
import com.icc.qasker.quizhistory.dto.feresponse.FolderResponse;
import com.icc.qasker.quizhistory.entity.QuizFolder;
import com.icc.qasker.quizhistory.repository.QuizFolderRepository;
import com.icc.qasker.quizhistory.repository.QuizHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 부하 트레이스용 quiz-folder 커맨드 mock(@Profile("mock & !mockai")). 다른 도메인
 * mock(MockQuizHistoryCommandService 등)과 동일 패턴 — findByIdAndUserId·소유검증 없이 모든 write를
 * 자기정리(save→delete) throwaway로 순증 0으로 태운다. folder만 이 mock이 없어 실 서비스(조건부 delete)를 타 스케일마다 종단 write가
 * 들쭉날쭉했다.
 */
@Service
@Primary
@Profile("mock & !mockai")
@RequiredArgsConstructor
public class MockQuizFolderCommandService implements QuizFolderCommandService {

  private final QuizFolderRepository quizFolderRepository;
  private final QuizHistoryRepository quizHistoryRepository;
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
  @Transactional
  public void deleteFolder(String userId, String folderId) {
    // 실 deleteFolder 경로를 그대로 태운다: throwaway 폴더 생성 → 히스토리 folder 해제 UPDATE
    //  (throwaway 폴더엔 배정 히스토리가 없어 매칭 0행 → 순증 0) → 폴더 삭제.
    //  clearFolderByFolderIdAndUserId(quiz_history 를 folder_id 로 UPDATE)까지 스케일별로 계측된다.
    QuizFolder folder = QuizFolder.builder().userId(userId).name("mock").build();
    quizFolderRepository.save(folder);
    quizHistoryRepository.clearFolderByFolderIdAndUserId(folder.getId(), userId);
    quizFolderRepository.delete(folder);
  }

  /** throwaway QuizFolder를 save→delete로 태워 종단 write SQL을 실 URI에 태그하되 데이터는 남기지 않는다. */
  @Transactional
  public void selfCleanWrite(String userId, String name) {
    QuizFolder folder = QuizFolder.builder().userId(userId).name(name).build();
    quizFolderRepository.save(folder);
    quizFolderRepository.delete(folder);
  }
}
