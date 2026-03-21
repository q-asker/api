package com.icc.qasker.quiz.controller;

import com.icc.qasker.global.annotation.UserId;
import com.icc.qasker.quiz.QuizHistoryCommandService;
import com.icc.qasker.quiz.QuizHistoryQueryService;
import com.icc.qasker.quiz.dto.ferequest.ChangeHistoryTitleRequest;
import com.icc.qasker.quiz.dto.ferequest.InitHistoryRequest;
import com.icc.qasker.quiz.dto.ferequest.SaveHistoryRequest;
import com.icc.qasker.quiz.dto.feresponse.HistoryDetailResponse;
import com.icc.qasker.quiz.dto.feresponse.HistorySummaryResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
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
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "QuizHistory", description = "퀴즈 히스토리 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/history")
public class QuizHistoryController {

  private final QuizHistoryCommandService quizHistoryCommandService;
  private final QuizHistoryQueryService quizHistoryQueryService;

  @Operation(summary = "내 퀴즈 히스토리 목록을 조회한다 (완료/미완료")
  @GetMapping
  public ResponseEntity<List<HistorySummaryResponse>> getHistoryList(@UserId String userId) {
    return ResponseEntity.ok(quizHistoryQueryService.getHistoryList(userId));
  }

  @Operation(summary = "퀴즈 히스토리 상세를 조회한다 (문제 + 답안 + 정답)")
  @GetMapping("/{problemSetId}")
  public ResponseEntity<HistoryDetailResponse> getHistoryDetail(
      @UserId String userId, @PathVariable String problemSetId) {
    return ResponseEntity.ok(quizHistoryQueryService.getHistoryDetail(userId, problemSetId));
  }

  @Operation(summary = "퀴즈 생성 시 히스토리를 초기화한다")
  @PostMapping("/init")
  public ResponseEntity<Void> initHistory(
      @UserId String userId, @Valid @RequestBody InitHistoryRequest request) {
    quizHistoryCommandService.initHistory(userId, request);
    return ResponseEntity.status(HttpStatus.CREATED).build();
  }

  @Operation(summary = "퀴즈 완료 후 답안 및 점수를 저장한다")
  @PostMapping
  public ResponseEntity<Map<String, String>> saveHistory(
      @UserId String userId, @Valid @RequestBody SaveHistoryRequest request) {
    String historyId = quizHistoryCommandService.saveHistory(userId, request);
    return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("historyId", historyId));
  }

  @Operation(summary = "퀴즈 히스토리 제목을 변경한다")
  @PatchMapping("/{historyId}/title")
  public ResponseEntity<Void> updateHistoryTitle(
      @UserId String userId,
      @PathVariable String historyId,
      @Valid @RequestBody ChangeHistoryTitleRequest request) {
    quizHistoryCommandService.updateHistoryTitle(userId, historyId, request.title());
    return ResponseEntity.noContent().build();
  }

  @Operation(summary = "모든 퀴즈 기록을 삭제한다")
  @DeleteMapping
  public ResponseEntity<Void> deleteAllHistory(@UserId String userId) {
    quizHistoryCommandService.deleteAllHistory(userId);
    return ResponseEntity.noContent().build();
  }
}
