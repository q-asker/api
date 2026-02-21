package com.icc.qasker.auth.controller;

import com.icc.qasker.auth.dto.request.PostRequest;
import com.icc.qasker.auth.dto.response.PostPageResponse;
import com.icc.qasker.auth.dto.response.PostResponse;
import com.icc.qasker.auth.dto.response.SinglePostResponse;
import com.icc.qasker.auth.entity.User;
import com.icc.qasker.auth.service.BoardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Board", description = "게시글 관련 API")
@RestController
@RequestMapping("/board")
@RequiredArgsConstructor
public class BoardController {

    private final BoardService boardService;

    @Operation(summary = "게시글 명단을 가져온다")
    @GetMapping
    public ResponseEntity<PostPageResponse> getPosts(@PageableDefault Pageable pageable) {
        Page<PostResponse> page = boardService.getPosts(pageable);
        return ResponseEntity.ok(PostPageResponse.from(page));
    }

    @Operation(summary = "단일 게시글 내용을 가져온다")
    @GetMapping("/{boardId}")
    public ResponseEntity<SinglePostResponse> getPost(@PathVariable Long boardId,
        @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(boardService.getPost(boardId, user));
    }

    @Operation(summary = "게시글 작성을 요청한다")
    @PostMapping("/create")
    public ResponseEntity<?> createPost(
        @RequestBody PostRequest request,
        @AuthenticationPrincipal User user) {
        boardService.createPost(request, user);
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "게시글을 수정한다")
    @PostMapping("/update/{boardId}")
    public ResponseEntity<?> updatePost(
        @PathVariable Long boardId,
        @RequestBody PostRequest request,
        @AuthenticationPrincipal User user) {
        boardService.updatePost(boardId, request, user);
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "게시글을 삭제한다")
    @DeleteMapping("/delete/{boardId}")
    public ResponseEntity<?> deletePost(
        @PathVariable Long boardId,
        @AuthenticationPrincipal User user) {
        boardService.deletePost(boardId, user);
        return ResponseEntity.ok().build();
    }
}
