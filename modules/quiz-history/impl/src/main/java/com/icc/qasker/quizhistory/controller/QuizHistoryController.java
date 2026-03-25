package com.icc.qasker.quizhistory.controller;

import com.icc.qasker.global.annotation.UserId;
import com.icc.qasker.quizhistory.QuizHistoryCommandService;
import com.icc.qasker.quizhistory.QuizHistoryQueryService;
import com.icc.qasker.quizhistory.dto.ferequest.ChangeHistoryTitleRequest;
import com.icc.qasker.quizhistory.dto.ferequest.InitHistoryRequest;
import com.icc.qasker.quizhistory.dto.ferequest.SaveHistoryRequest;
import com.icc.qasker.quizhistory.dto.feresponse.HistoryCheckResponse;
import com.icc.qasker.quizhistory.dto.feresponse.HistoryDetailResponse;
import com.icc.qasker.quizhistory.dto.feresponse.HistoryIdResponse;
import com.icc.qasker.quizhistory.dto.feresponse.HistorySummaryResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 퀴즈 히스토리 API (Shadow Copy 패턴)
 *
 * <p>원본 ProblemSet과 독립적으로 사용자가 독립적인 히스토리(제목, 답안, 점수)를 갖는다.
 */
@Tag(name = "QuizHistory", description = "퀴즈 히스토리 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/history")
public class QuizHistoryController {

  private final QuizHistoryCommandService quizHistoryCommandService;
  private final QuizHistoryQueryService quizHistoryQueryService;

  // ── Check ──────────────────────────────────────────────────────────────────

  @Operation(summary = "해당 문제세트에 대한 히스토리 존재 여부를 확인한다")
  @GetMapping("/check/{problemSetId}")
  public ResponseEntity<HistoryCheckResponse> checkHistory(
      @UserId String userId, @PathVariable String problemSetId) {
    return ResponseEntity.ok(quizHistoryQueryService.checkHistory(userId, problemSetId));
  }

  // ── Read ───────────────────────────────────────────────────────────────────

  @Operation(summary = "내 퀴즈 히스토리 목록을 조회한다 (완료/미완료)")
  @GetMapping
  public ResponseEntity<List<HistorySummaryResponse>> getHistoryList(@UserId String userId) {
    return ResponseEntity.ok(quizHistoryQueryService.getHistoryList(userId));
  }

  @Operation(summary = "퀴즈 히스토리 상세를 조회한다 (문제 + 답안 + 정답)")
  @GetMapping("/{historyId}")
  public ResponseEntity<HistoryDetailResponse> getHistoryDetail(
      @UserId String userId, @PathVariable String historyId) {
    return ResponseEntity.ok(quizHistoryQueryService.getHistoryDetail(userId, historyId));
  }

  // ── Create ─────────────────────────────────────────────────────────────────

  @Operation(summary = "퀴즈 생성 시 히스토리를 초기화한다")
  @PostMapping("/init")
  public ResponseEntity<HistoryIdResponse> initHistory(
      @UserId String userId, @Valid @RequestBody InitHistoryRequest request) {
    String historyId = quizHistoryCommandService.initHistory(userId, request);
    return ResponseEntity.status(HttpStatus.CREATED).body(new HistoryIdResponse(historyId));
  }

  // ── Update: Data ───────────────────────────────────────────────────────────

  @Operation(summary = "퀴즈 완료 후 답안 및 점수를 저장한다")
  @PostMapping
  public ResponseEntity<HistoryIdResponse> saveHistory(
      @UserId String userId, @Valid @RequestBody SaveHistoryRequest request) {
    String historyId = quizHistoryCommandService.saveHistory(userId, request);
    return ResponseEntity.status(HttpStatus.CREATED).body(new HistoryIdResponse(historyId));
  }

  // ── Update: Metadata ───────────────────────────────────────────────────────

  @Operation(summary = "퀴즈 히스토리 제목을 변경한다")
  @PatchMapping("/{historyId}/title")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void updateHistoryTitle(
      @UserId String userId,
      @PathVariable String historyId,
      @Valid @RequestBody ChangeHistoryTitleRequest request) {
    quizHistoryCommandService.updateHistoryTitle(userId, historyId, request.title());
  }

  // ── Delete ─────────────────────────────────────────────────────────────────

  @Operation(summary = "특정 퀴즈 기록을 삭제한다")
  @DeleteMapping("/{historyId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void deleteHistory(@UserId String userId, @PathVariable String historyId) {
    quizHistoryCommandService.deleteHistory(userId, historyId);
  }

  @Operation(summary = "모든 퀴즈 기록을 삭제한다")
  @DeleteMapping("/all")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void deleteAllHistory(@UserId String userId) {
    quizHistoryCommandService.deleteAllHistory(userId);
  }
}
