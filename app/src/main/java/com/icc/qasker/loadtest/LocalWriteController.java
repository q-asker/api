package com.icc.qasker.loadtest;

import com.icc.qasker.auth.entity.RefreshToken;
import com.icc.qasker.auth.repository.RefreshTokenRepository;
import com.icc.qasker.board.dto.BoardCategory;
import com.icc.qasker.board.entity.Board;
import com.icc.qasker.board.entity.FeedbackBoard;
import com.icc.qasker.board.entity.Reply;
import com.icc.qasker.board.repository.BoardRepository;
import com.icc.qasker.board.repository.FeedbackBoardRepository;
import com.icc.qasker.board.repository.ReplyRepository;
import com.icc.qasker.global.annotation.RateLimit;
import com.icc.qasker.global.ratelimit.RateLimitTier;
import com.icc.qasker.quizhistory.entity.EssayGradeLog;
import com.icc.qasker.quizhistory.entity.QuizHistory;
import com.icc.qasker.quizhistory.repository.EssayGradeLogRepository;
import com.icc.qasker.quizhistory.repository.QuizHistoryRepository;
import com.icc.qasker.quizset.dto.ferequest.enums.QuizType;
import com.icc.qasker.quizset.entity.Problem;
import com.icc.qasker.quizset.entity.ProblemId;
import com.icc.qasker.quizset.entity.ProblemSet;
import com.icc.qasker.quizset.repository.ProblemRepository;
import com.icc.qasker.quizset.repository.ProblemSetRepository;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 부하 테스트용 쓰기 드라이버. Gemini·OCI 등 비싼 외부 호출을 거치지 않고 조작 데이터로 각 레포의 실제 save/delete만 태운다(= mock 쓰기). 이로써
 * 쓰기 레포 메서드가 계측(spring.data.repository.invocations)에 뜬다. 각 레포는 독립 try로 감싸 한 제약 실패가 나머지를 막지 않는다. 생성한
 * 행은 같은 호출에서 delete로 되돌린다(자기 정리). @Profile("loadtest").
 */
@RestController
@Profile("loadtest")
@RequiredArgsConstructor
@RequestMapping("/local/write")
public class LocalWriteController {

  private final BoardRepository boardRepository;
  private final ReplyRepository replyRepository;
  private final FeedbackBoardRepository feedbackBoardRepository;
  private final ProblemSetRepository problemSetRepository;
  private final ProblemRepository problemRepository;
  private final QuizHistoryRepository quizHistoryRepository;
  private final EssayGradeLogRepository essayGradeLogRepository;
  private final RefreshTokenRepository refreshTokenRepository;

  @RateLimit(RateLimitTier.NONE)
  @PostMapping
  public ResponseEntity<String> write() {
    String uid = "loadtest-" + System.nanoTime();
    StringBuilder log = new StringBuilder();

    // Board + Reply
    try {
      Board board =
          boardRepository.save(
              Board.builder()
                  .userId(uid)
                  .title("lt")
                  .content("lt")
                  .category(BoardCategory.INQUIRY)
                  .build());
      Reply reply =
          replyRepository.save(Reply.builder().board(board).adminId(uid).content("lt").build());
      replyRepository.delete(reply);
      boardRepository.delete(board);
      log.append("board+reply ok; ");
    } catch (Exception e) {
      log.append("board FAIL:").append(e.getClass().getSimpleName()).append("; ");
    }

    // FeedbackBoard
    try {
      FeedbackBoard fb =
          feedbackBoardRepository.save(FeedbackBoard.builder().userId(uid).content("lt").build());
      feedbackBoardRepository.delete(fb);
      log.append("feedback ok; ");
    } catch (Exception e) {
      log.append("feedback FAIL:").append(e.getClass().getSimpleName()).append("; ");
    }

    // ProblemSet + Problem + QuizHistory + EssayGradeLog (하나의 FK 정합 그래프)
    try {
      ProblemSet ps =
          problemSetRepository.save(
              ProblemSet.builder()
                  .userId(uid)
                  .title("lt")
                  .sessionId("lt-" + System.nanoTime())
                  .quizType(QuizType.MULTIPLE)
                  .totalQuizCount(1)
                  .fileUrl("lt")
                  .build());
      Long psId = ps.getId();

      Problem problem =
          Problem.builder()
              .id(ProblemId.builder().problemSetId(psId).number(1).build())
              .problemSet(ps)
              .title("lt")
              .build();
      problemRepository.saveAll(List.of(problem));

      QuizHistory qh =
          quizHistoryRepository.save(
              QuizHistory.builder()
                  .userId(uid)
                  .problemSetId(psId)
                  .title("lt")
                  .score(0)
                  .totalTime("0")
                  .build());

      EssayGradeLog eg =
          essayGradeLogRepository.save(
              EssayGradeLog.builder()
                  .userId(uid)
                  .problemSetId(psId)
                  .problemNumber(1)
                  .question("lt")
                  .studentAnswer("lt")
                  .attemptCount(1)
                  .totalScore(0)
                  .maxScore(1)
                  .elementScores(List.of())
                  .overallFeedback("lt")
                  .evidenceJson("{}")
                  .build());

      // 자기 정리 (FK 역순)
      quizHistoryRepository.delete(qh);
      essayGradeLogRepository.delete(eg);
      problemRepository.delete(problem);
      problemSetRepository.delete(ps);
      log.append("problemset+problem+history+essay ok; ");
    } catch (Exception e) {
      log.append("quizset-chain FAIL:")
          .append(e.getClass().getSimpleName())
          .append(":")
          .append(e.getMessage())
          .append("; ");
    }

    // RefreshToken (userId @Id — 가짜 userId로 INSERT)
    try {
      RefreshToken rt =
          refreshTokenRepository.save(
              new RefreshToken(uid, "lt-hash", Instant.now().plusSeconds(3600)));
      refreshTokenRepository.delete(rt);
      log.append("refreshtoken ok; ");
    } catch (Exception e) {
      log.append("refreshtoken FAIL:").append(e.getClass().getSimpleName()).append("; ");
    }

    return ResponseEntity.ok(log.toString());
  }
}
