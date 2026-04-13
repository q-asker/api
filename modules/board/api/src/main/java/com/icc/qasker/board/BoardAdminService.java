package com.icc.qasker.board;

import com.icc.qasker.board.dto.request.PostRequest;

/** 게시판 관리자 전용 기능 인터페이스. */
public interface BoardAdminService {

  /** 업데이트 로그 게시글을 작성한다. */
  void createUpdateLog(PostRequest request, String adminUserId);

  /** 게시글에 관리자 답변(댓글)을 단다. */
  void reply(Long boardId, String adminUserId, String content);
}
