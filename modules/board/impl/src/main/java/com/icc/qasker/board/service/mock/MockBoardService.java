package com.icc.qasker.board.service.mock;

import com.icc.qasker.board.dto.BoardCategory;
import com.icc.qasker.board.dto.request.PostRequest;
import com.icc.qasker.board.dto.response.PostDetailResponse;
import com.icc.qasker.board.dto.response.PostPageResponse;
import com.icc.qasker.board.entity.Board;
import com.icc.qasker.board.repository.BoardRepository;
import com.icc.qasker.board.service.BoardService;
import com.icc.qasker.board.service.BoardServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 부하 트레이스용 board mock(@Profile("mock")). 읽기는 실 서비스에 위임하고, 쓰기는 자기정리(save→delete)로 순증 0을 유지한다. 실
 * write 엔드포인트(POST/PUT/DELETE /boards)를 그대로 태우되 행을 남기지 않아, trace_snapshot이 실 URI로 write SQL을 잡으면서
 * DB 상태는 불변이다.
 */
@Service
@Primary
@Profile("mock")
@RequiredArgsConstructor
public class MockBoardService implements BoardService {

  private final BoardServiceImpl real;
  private final BoardRepository boardRepository;

  @Override
  public PostPageResponse getPosts(BoardCategory category, Pageable pageable) {
    return real.getPosts(category, pageable);
  }

  @Override
  public PostDetailResponse getPost(Long boardId, String requestUserId) {
    return real.getPost(boardId, requestUserId);
  }

  @Override
  @Transactional
  public void createPost(PostRequest request, String userId) {
    selfCleanWrite(userId);
  }

  @Override
  @Transactional
  public void updatePost(Long boardId, PostRequest request, String userId) {
    selfCleanWrite(userId);
  }

  @Override
  @Transactional
  public void deletePost(Long boardId, String userId) {
    selfCleanWrite(userId);
  }

  /** save→delete로 board write SQL을 실 URI에 태그하되 순증 0을 유지한다. */
  private void selfCleanWrite(String userId) {
    Board board = Board.builder().title("mock").content("mock").userId(userId).build();
    boardRepository.save(board);
    boardRepository.delete(board);
  }
}
