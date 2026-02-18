package com.icc.qasker.auth.controller;

import com.icc.qasker.auth.dto.response.PostResponse;
import com.icc.qasker.auth.service.BoardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Board", description = "게시글 관련 API")
@RestController
@RequestMapping("/board")
@RequiredArgsConstructor
public class BoardController {

    private final BoardService boardService;

    @Operation(summary = "게시글을 가져온다")
    @GetMapping
    public ResponseEntity<Page<PostResponse>> getPosts(
        @PageableDefault(page = 0, size = 20) Pageable pageable
    ) {
        return ResponseEntity.ok(boardService.getPosts(pageable));
    }


}
