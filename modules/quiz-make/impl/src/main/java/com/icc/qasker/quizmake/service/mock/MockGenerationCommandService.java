package com.icc.qasker.quizmake.service.mock;

import com.icc.qasker.quizmake.GenerationCommandService;
import com.icc.qasker.quizmake.dto.ferequest.GenerationRequest;
import com.icc.qasker.quizset.QuizCommandService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;

/**
 * 부하 트레이스용 생성 mock(@Profile("mock")). AI 스트리밍·가상스레드·SSE는 전부 건너뛰고 동기로 {@code initProblemSet}만 태운다.
 * problem_set 삭제 메서드가 다른 모듈에 없어(cross-module) 트랜잭션 롤백으로 순증 0을 달성한다 — INSERT는 performance_schema에 남아
 * 실 URI로 트레이스되고, 커밋은 되지 않아 DB 상태는 불변이다.
 */
@Service
@Primary
@Profile("mock")
@RequiredArgsConstructor
public class MockGenerationCommandService implements GenerationCommandService {

  private final QuizCommandService quizCommandService;

  @Override
  @Transactional
  public void triggerGeneration(String userId, GenerationRequest request) {
    quizCommandService.initProblemSet(
        userId,
        request.sessionId(),
        request.title(),
        request.quizCount(),
        request.quizType(),
        request.uploadedUrl(),
        request.customInstruction());
    // 자기정리: 롤백으로 problem_set INSERT를 되돌린다(트레이스엔 남고 DB엔 안 남음).
    TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
  }
}
