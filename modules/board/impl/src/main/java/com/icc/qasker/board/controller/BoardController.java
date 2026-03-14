package com.icc.qasker.board.controller;

import com.icc.qasker.board.dto.request.PostRequest;
import com.icc.qasker.board.dto.request.ReplyRequest;
import com.icc.qasker.board.dto.response.PostDetailResponse;
import com.icc.qasker.board.dto.response.PostPageResponse;
import com.icc.qasker.board.dto.response.PostResponse;
import com.icc.qasker.board.service.BoardService;
import com.icc.qasker.board.service.ReplyService;
import com.icc.qasker.global.annotation.UserId;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Board", description = "게시글 관련 API")
@RestController
@RequestMapping("/boards")
@RequiredArgsConstructor
public class BoardController {

  private final BoardService boardService;
  private final ReplyService replyService;

  @Operation(summary = "게시글 명단을 가져온다")
  @GetMapping
  public ResponseEntity<PostPageResponse> getPosts(@PageableDefault Pageable pageable) {
    Page<PostResponse> page = boardService.getPosts(pageable);
    return ResponseEntity.ok(PostPageResponse.from(page));
  }

  @Operation(summary = "단일 게시글 내용을 가져온다")
  @GetMapping("/{boardId}")
  public ResponseEntity<PostDetailResponse> getPost(
      @PathVariable Long boardId, @UserId String userId) {
    return ResponseEntity.ok(boardService.getPost(boardId, userId));
  }

  @Operation(summary = "게시글 작성을 요청한다")
  @PostMapping()
  public ResponseEntity<?> createPost(@RequestBody PostRequest request, @UserId String userId) {
    boardService.createPost(request, userId);
    return ResponseEntity.ok().build();
  }

  @Operation(summary = "게시글을 수정한다")
  @PutMapping("/{boardId}")
  public ResponseEntity<?> updatePost(
      @PathVariable Long boardId, @RequestBody PostRequest request, @UserId String userId) {
    boardService.updatePost(boardId, request, userId);
    return ResponseEntity.ok().build();
  }

  @Operation(summary = "게시글을 삭제한다")
  @DeleteMapping("/{boardId}")
  public ResponseEntity<?> deletePost(@PathVariable Long boardId, @UserId String userId) {
    boardService.deletePost(boardId, userId);
    return ResponseEntity.ok().build();
  }

  @Operation(summary = "댓글을 단다")
  @PostMapping("/{boardId}/replies")
  public ResponseEntity<?> reply(
      @RequestBody ReplyRequest replyRequest, @PathVariable Long boardId, @UserId String userId) {
    replyService.reply(boardId, userId, replyRequest.content());
    return ResponseEntity.ok().build();
  }
}
