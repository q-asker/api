package com.icc.qasker.quizhistory.controller;

import com.icc.qasker.global.annotation.UserId;
import com.icc.qasker.quizhistory.QuizHistoryQueryService;
import com.icc.qasker.quizhistory.dto.feresponse.EssayHistoryDetailResponse;
import com.icc.qasker.quizhistory.dto.feresponse.HistoryCheckResponse;
import com.icc.qasker.quizhistory.dto.feresponse.HistoryDetailResponse;
import com.icc.qasker.quizhistory.dto.feresponse.HistoryPageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 퀴즈 히스토리 조회 API (Shadow Copy 패턴)
 *
 * <p>원본 ProblemSet과 독립적으로 사용자가 독립적인 히스토리(제목, 답안, 점수)를 갖는다.
 */
@Tag(name = "QuizHistory", description = "퀴즈 히스토리 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/history")
public class QuizHistoryQueryController {

  private final QuizHistoryQueryService quizHistoryQueryService;

  @Operation(summary = "해당 문제세트에 대한 히스토리 존재 여부를 확인한다")
  @GetMapping("/check/{problemSetId}")
  public ResponseEntity<HistoryCheckResponse> checkHistory(
      @UserId String userId, @PathVariable String problemSetId) {
    return ResponseEntity.ok(quizHistoryQueryService.checkHistory(userId, problemSetId));
  }

  @Operation(summary = "내 퀴즈 히스토리 목록을 페이지 단위로 조회한다")
  @GetMapping
  public ResponseEntity<HistoryPageResponse> getHistoryList(
      @UserId String userId,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return ResponseEntity.ok(quizHistoryQueryService.getHistoryList(userId, page, size));
  }

  @Operation(summary = "퀴즈 히스토리 상세를 조회한다 (문제 + 답안 + 정답)")
  @GetMapping("/{historyId}")
  public ResponseEntity<HistoryDetailResponse> getHistoryDetail(
      @UserId String userId, @PathVariable String historyId) {
    return ResponseEntity.ok(quizHistoryQueryService.getHistoryDetail(userId, historyId));
  }

  @Operation(summary = "ESSAY 히스토리 상세를 조회한다 (문제 + 답안 + 최신 채점 결과)")
  @GetMapping("/{historyId}/essay")
  public ResponseEntity<EssayHistoryDetailResponse> getEssayHistoryDetail(
      @UserId String userId, @PathVariable String historyId) {
    return ResponseEntity.ok(quizHistoryQueryService.getEssayHistoryDetail(userId, historyId));
  }
}
