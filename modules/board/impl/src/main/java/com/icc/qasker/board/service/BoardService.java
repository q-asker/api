package com.icc.qasker.board.service;

import com.icc.qasker.board.dto.BoardCategory;
import com.icc.qasker.board.dto.request.PostRequest;
import com.icc.qasker.board.dto.response.PostDetailResponse;
import com.icc.qasker.board.dto.response.PostPageResponse;
import org.springframework.data.domain.Pageable;

/**
 * 게시판 사용자 기능. mock 프로파일에서 자기정리(save→delete) write 구현({@code MockBoardService})으로 교체돼, 실 write
 * 엔드포인트를 순증 0으로 트레이스한다.
 */
public interface BoardService {

  PostPageResponse getPosts(BoardCategory category, Pageable pageable);

  PostDetailResponse getPost(Long boardId, String requestUserId);

  void createPost(PostRequest request, String userId);

  void updatePost(Long boardId, PostRequest request, String userId);

  void deletePost(Long boardId, String userId);
}
