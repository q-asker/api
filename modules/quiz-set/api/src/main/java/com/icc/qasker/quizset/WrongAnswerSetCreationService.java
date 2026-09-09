package com.icc.qasker.quizset;

import com.icc.qasker.quizset.dto.WrongAnswerSetCreation;

/** 이미 있는 문항을 복제해 오답 문제집을 만든다. 새 문제를 생성하지 않으므로 AI를 거치지 않는다. */
public interface WrongAnswerSetCreationService {

  /**
   * 세트와 문항을 만들고 새 세트 id를 돌려준다. 호출자의 트랜잭션에 참여하므로, 세트·문항·호출자가 함께 남기는 것이 한 번에 커밋되거나 한 번에 사라진다.
   *
   * @throws org.springframework.dao.DataIntegrityViolationException 같은 sessionId의 세트가 이미 있을 때
   */
  Long create(WrongAnswerSetCreation request);
}
