package com.icc.qasker.quizmake.service.generation;

import static com.icc.qasker.quizset.GenerationStatus.COMPLETED;

import com.icc.qasker.global.error.ExceptionMessage;
import com.icc.qasker.quizmake.GenerationQueryService;
import com.icc.qasker.quizmake.SseNotificationService;
import com.icc.qasker.quizset.GenerationStatus;
import com.icc.qasker.quizset.QuizQueryService;
import com.icc.qasker.quizset.dto.feresponse.ProblemSetResponse;
import java.util.Optional;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.math.NumberUtils;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Slf4j
@Service
@AllArgsConstructor
public class GenerationQueryServiceImpl implements GenerationQueryService {

  // 핵심
  private final SseNotificationService notificationService;
  private final QuizQueryService quizQueryService;

  @Override
  public SseEmitter subscribe(String sessionId, String lastEventId) {
    // DB 통신 실패할 수 있으므로 먼저
    Optional<GenerationStatus> statusOptional =
        quizQueryService.getGenerationStatusBySessionId(sessionId);

    SseEmitter emitter = notificationService.createSseEmitter(sessionId);

    statusOptional.ifPresent(
        status -> {
          switch (status) {
            case FAILED ->
                notificationService.sendFinishWithError(
                    sessionId, ExceptionMessage.AI_GENERATION_FAILED.getMessage());

            case GENERATING, COMPLETED -> {
              int lastEventNumber = NumberUtils.toInt(lastEventId, 0);
              ProblemSetResponse ps =
                  quizQueryService.getMissedProblems(sessionId, lastEventNumber);

              notificationService.sendCreatedMessageWithId(
                  sessionId, String.valueOf(lastEventNumber + ps.quiz().size()), ps);

              // COMPLETE 상태일 경우 완료 메시지 전송
              if (status == COMPLETED) {
                notificationService.sendComplete(sessionId);
              }
            }
          }
        });

    return emitter;
  }
}
