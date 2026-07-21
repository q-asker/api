package com.icc.qasker.quizhistory.controller;

import com.icc.qasker.global.annotation.UserId;
import com.icc.qasker.quizhistory.QuizFolderQueryService;
import com.icc.qasker.quizhistory.dto.feresponse.FolderListResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 퀴즈 기록 폴더 조회 API (내 폴더 목록 + 폴더별/미분류 기록 수). */
@Tag(name = "QuizFolder", description = "퀴즈 기록 폴더 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/folders")
public class QuizFolderQueryController {

  private final QuizFolderQueryService quizFolderQueryService;

  @Operation(summary = "내 폴더 목록과 폴더별/미분류 기록 수를 조회한다")
  @GetMapping
  public ResponseEntity<FolderListResponse> getFolders(@UserId String userId) {
    return ResponseEntity.ok(quizFolderQueryService.getFolders(userId));
  }
}
