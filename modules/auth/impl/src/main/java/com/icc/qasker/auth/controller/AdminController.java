package com.icc.qasker.auth.controller;

import com.icc.qasker.auth.dto.request.ReplyRequest;
import com.icc.qasker.auth.entity.User;
import com.icc.qasker.auth.service.AdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Admin", description = "관리자 관련 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/admin")
public class AdminController {

    private final AdminService adminService;

    @Operation(summary = "댓글을 단다")
    @PostMapping("/{boardId}")
    public ResponseEntity<?> reply(@RequestBody ReplyRequest replyRequest,
        @PathVariable Long boardId, @AuthenticationPrincipal User user) {
        adminService.reply(boardId, user, replyRequest.content());
        return ResponseEntity.ok().build();
    }
}
