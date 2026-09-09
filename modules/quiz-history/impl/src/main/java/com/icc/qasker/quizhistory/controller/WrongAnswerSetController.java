package com.icc.qasker.quizhistory.controller;

import com.icc.qasker.global.annotation.RateLimit;
import com.icc.qasker.global.annotation.UserId;
import com.icc.qasker.global.ratelimit.RateLimitTier;
import com.icc.qasker.quizhistory.WrongAnswerSetService;
import com.icc.qasker.quizhistory.dto.ferequest.CreateWrongAnswerSetRequest;
import com.icc.qasker.quizhistory.dto.feresponse.WrongAnswerSetResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 오답 모아풀기 API.
 *
 * <p>모을 오답이 없거나, 상한에 걸려 일부만 담기거나, 일부 유형이 실패해도 200이다 — 사용자에게 알려야 할 상태이지 요청의 실패가 아니고, 오류로 내리면 프론트의 공통
 * 오류 처리가 문구를 가로채 상황에 맞는 안내를 할 수 없다.
 */
@Tag(name = "ProblemSet", description = "문제세트 관련 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/problem-set")
public class WrongAnswerSetController {

  private final WrongAnswerSetService wrongAnswerSetService;

  @Operation(summary = "현재 폴더에서 틀린 문항을 모아 유형별로 새 문제집을 만든다")
  @RateLimit(RateLimitTier.WRITE)
  @PostMapping("/wrong-answers")
  public ResponseEntity<WrongAnswerSetResponse> createWrongAnswerSets(
      @UserId String userId,
      @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
      @Valid @RequestBody CreateWrongAnswerSetRequest request) {
    // 키가 없으면 멱등 보장 없이 매번 새로 만든다. 연타·재시도를 흡수하려면 한 번의 조작에 같은 키를 보내야 한다.
    String key =
        (idempotencyKey == null || idempotencyKey.isBlank())
            ? UUID.randomUUID().toString()
            : idempotencyKey;
    return ResponseEntity.ok(wrongAnswerSetService.createFromFolder(userId, request, key));
  }
}
