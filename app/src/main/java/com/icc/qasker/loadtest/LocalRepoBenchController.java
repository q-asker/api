package com.icc.qasker.loadtest;

import com.icc.qasker.auth.repository.RefreshTokenRepository;
import com.icc.qasker.auth.repository.UserRepository;
import com.icc.qasker.board.dto.BoardCategory;
import com.icc.qasker.board.repository.BoardRepository;
import com.icc.qasker.board.repository.FeedbackBoardRepository;
import com.icc.qasker.board.repository.ReplyRepository;
import com.icc.qasker.global.annotation.RateLimit;
import com.icc.qasker.global.ratelimit.RateLimitTier;
import com.icc.qasker.quizhistory.repository.EssayGradeLogRepository;
import com.icc.qasker.quizhistory.repository.QuizHistoryRepository;
import com.icc.qasker.quizset.GenerationStatus;
import com.icc.qasker.quizset.repository.ProblemRepository;
import com.icc.qasker.quizset.repository.ProblemSetRepository;
import java.time.Instant;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 부하 테스트용 read 레포 벤치. 대시보드는 레포지토리 메서드 타이밍만 보므로, API 경로로 간접 호출하는 대신 각 read 메서드를 직접 N번 호출해 균일하게 표본을
 * 쌓는다(warmup 희석, 스케줄러 전용 쿼리도 직접 구동). 조회만 하므로 부작용 없음. 9개 레포 전체 커버.
 * findByRtHash는 @Lock(PESSIMISTIC_WRITE)라 호출마다 짧은 트랜잭션으로 감싸 락을 즉시 해제한다. @Profile("loadtest").
 */
@RestController
@Profile("loadtest")
@RequestMapping("/local/repo-bench")
public class LocalRepoBenchController {

  private static final String SEED_USER = "h_e9887d1d5b31f89c3101b5732df92f4c";

  private final RefreshTokenRepository refreshTokenRepository;
  private final ProblemSetRepository problemSetRepository;
  private final ProblemRepository problemRepository;
  private final QuizHistoryRepository quizHistoryRepository;
  private final EssayGradeLogRepository essayGradeLogRepository;
  private final UserRepository userRepository;
  private final BoardRepository boardRepository;
  private final ReplyRepository replyRepository;
  private final FeedbackBoardRepository feedbackBoardRepository;
  private final TransactionTemplate txTemplate;

  public LocalRepoBenchController(
      RefreshTokenRepository refreshTokenRepository,
      ProblemSetRepository problemSetRepository,
      ProblemRepository problemRepository,
      QuizHistoryRepository quizHistoryRepository,
      EssayGradeLogRepository essayGradeLogRepository,
      UserRepository userRepository,
      BoardRepository boardRepository,
      ReplyRepository replyRepository,
      FeedbackBoardRepository feedbackBoardRepository,
      PlatformTransactionManager txManager) {
    this.refreshTokenRepository = refreshTokenRepository;
    this.problemSetRepository = problemSetRepository;
    this.problemRepository = problemRepository;
    this.quizHistoryRepository = quizHistoryRepository;
    this.essayGradeLogRepository = essayGradeLogRepository;
    this.userRepository = userRepository;
    this.boardRepository = boardRepository;
    this.replyRepository = replyRepository;
    this.feedbackBoardRepository = feedbackBoardRepository;
    this.txTemplate = new TransactionTemplate(txManager);
  }

  @RateLimit(RateLimitTier.NONE)
  @PostMapping
  public ResponseEntity<String> bench(@RequestParam(defaultValue = "100") int n) {
    Instant now = Instant.now();
    List<GenerationStatus> statuses = List.of(GenerationStatus.GENERATING);
    for (int i = 0; i < n; i++) {
      long id = i;
      // auth
      txTemplate.executeWithoutResult(
          s -> refreshTokenRepository.findByRtHash("bench-" + id)); // 무인덱스 풀스캔 + PESSIMISTIC_WRITE
      userRepository.findById(SEED_USER); // PK(String)
      // quiz-set
      problemSetRepository.findByGenerationStatusInAndCreatedAtBefore(statuses, now); // 스케줄러 쿼리(스캔)
      problemSetRepository.findById(id); // PK
      problemSetRepository.findAllById(List.of(1L, 2L, 3L)); // PK in
      problemRepository.findByIdProblemSetId(id); // problem_set_id
      // quiz-history
      quizHistoryRepository.findById(id); // PK
      quizHistoryRepository.findAllByUserIdOrderByCreatedAtDesc(SEED_USER); // user 인덱스
      quizHistoryRepository.findByUserIdAndProblemSetId(SEED_USER, id); // user+ps
      // 마스킹된 essay_grade_log.element_scores JSON이 역직렬화 안 되므로 miss 인자로(행 materialize 회피, 쿼리 비용만 측정)
      essayGradeLogRepository.findById(-1L - id); // PK (miss)
      essayGradeLogRepository.findLatestByUserIdAndProblemSetId(
          "bench-nobody", id); // user+ps (miss)
      // board
      boardRepository.findById(id); // PK
      boardRepository.findByIdWithReplies(id); // fetch join
      boardRepository.findByCategory(BoardCategory.INQUIRY, PageRequest.of(0, 20)); // 카테고리 페이지
      replyRepository.findById(id); // PK
      feedbackBoardRepository.findById(id); // PK
    }
    return ResponseEntity.ok("repo-bench done: n=" + n + " per method (9 repos)");
  }
}
