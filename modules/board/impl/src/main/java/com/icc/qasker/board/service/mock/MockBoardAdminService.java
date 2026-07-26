package com.icc.qasker.board.service.mock;

import com.icc.qasker.board.BoardAdminService;
import com.icc.qasker.board.dto.BoardCategory;
import com.icc.qasker.board.dto.request.PostRequest;
import com.icc.qasker.board.entity.Board;
import com.icc.qasker.board.entity.Reply;
import com.icc.qasker.board.repository.BoardRepository;
import com.icc.qasker.board.repository.ReplyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 부하 트레이스용 board-admin mock(@Profile("mock")). 실 write(업데이트 로그 작성/수정, 관리자 답변)을 throwaway 엔티티
 * save→(update)→delete 로 태워 순증 0 을 유지한다. 소유·존재 검증(findById)에 묶이지 않아 임의 boardId 로도 종단 write SQL 이
 * 결정적으로 발화하고, BoardRepository·ReplyRepository 의 admin write 경로가 trace_snapshot 에 실 URI 로 남되 DB 상태는
 * 불변이다.
 */
@Service
@Primary
@Profile("mock")
@RequiredArgsConstructor
public class MockBoardAdminService implements BoardAdminService {

  private final BoardRepository boardRepository;
  private final ReplyRepository replyRepository;

  @Override
  @Transactional
  public void createUpdateLog(PostRequest request, String adminUserId) {
    Board throwaway = updateLog(adminUserId);
    boardRepository.save(throwaway); // INSERT
    boardRepository.delete(throwaway); // 자기정리(순증 0)
  }

  @Override
  @Transactional
  public void updateUpdateLog(Long boardId, PostRequest request, String adminUserId) {
    Board throwaway = updateLog(adminUserId);
    boardRepository.save(throwaway); // INSERT
    throwaway.update("mock2", "mock2"); // UPDATE 대상 변경
    boardRepository.saveAndFlush(throwaway); // UPDATE flush → 트레이스
    boardRepository.delete(throwaway);
  }

  @Override
  @Transactional
  public void reply(Long boardId, String adminUserId, String content) {
    // throwaway 게시글 → 그 위에 답변 INSERT(ReplyRepository) → 둘 다 삭제(순증 0).
    Board board = updateLog(adminUserId);
    boardRepository.save(board);
    Reply reply = Reply.builder().board(board).adminId(adminUserId).content("mock").build();
    replyRepository.save(reply); // INSERT (ReplyRepository)
    replyRepository.delete(reply);
    boardRepository.delete(board);
  }

  private static Board updateLog(String adminUserId) {
    return Board.builder()
        .title("mock")
        .content("mock")
        .userId(adminUserId)
        .category(BoardCategory.UPDATE_LOG)
        .build();
  }
}
