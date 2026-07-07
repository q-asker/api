package com.icc.qasker.quizset.controller;

import com.icc.qasker.global.annotation.RateLimit;
import com.icc.qasker.global.ratelimit.RateLimitTier;
import com.icc.qasker.quizset.QuizCommandService;
import com.icc.qasker.quizset.dto.airesponse.ExplanationGeneratedFromAI;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * [임시 · 성능 측정 전용] 쓰기 축(dirty tracking) 1000 TPS 부하용 dev 엔드포인트.
 *
 * <p>saveExplanation(=Problem 재로드 → updateExplanation → flush, dirty-tracking 경로)을 HTTP로 구동해 실제
 * MySQL에 1000 TPS 쓰기 부하를 걸고 앱/DB CPU-time·RAM을 측정한다(baseline §B, Q3/Q10). {@code local} 프로파일에서만
 * 등록되고 rate limit 제외({@link RateLimitTier#NONE}). **측정 후 제거 대상 — 운영 배포 금지.**
 */
@RestController
@Profile("local")
@RequiredArgsConstructor
public class DevWriteBenchController {

  private final QuizCommandService quizCommandService;

  // ~800자 ≈ 2.4KB UTF-8 (실측 해설 크기 재현)
  private static final String EXPLANATION = "가나다라마바사아자차 ".repeat(73);

  @RateLimit(RateLimitTier.NONE)
  @PostMapping("/dev/bench/explanation")
  public void saveExplanation(@RequestParam long setId, @RequestParam int number) {
    quizCommandService.saveExplanation(
        setId, new ExplanationGeneratedFromAI(number, EXPLANATION, List.of()));
  }

  /** 배치 모드: 해설 count건을 saveExplanations(단일 트랜잭션)로 저장 — 단건 대비 커밋·fsync N→1 비교용. */
  @RateLimit(RateLimitTier.NONE)
  @PostMapping("/dev/bench/explanations")
  public void saveExplanations(@RequestParam long setId, @RequestParam int count) {
    List<ExplanationGeneratedFromAI> batch = new java.util.ArrayList<>(count);
    for (int number = 1; number <= count; number++) {
      batch.add(new ExplanationGeneratedFromAI(number, EXPLANATION, List.of()));
    }
    quizCommandService.saveExplanations(setId, batch);
  }
}
