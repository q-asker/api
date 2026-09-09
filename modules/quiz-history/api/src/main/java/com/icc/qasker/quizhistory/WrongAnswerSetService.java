package com.icc.qasker.quizhistory;

import com.icc.qasker.quizhistory.dto.ferequest.CreateWrongAnswerSetRequest;
import com.icc.qasker.quizhistory.dto.feresponse.WrongAnswerSetResponse;

/** 한 폴더에서 내가 틀린 문항을 모아 유형마다 새 문제집을 만든다. */
public interface WrongAnswerSetService {

  /**
   * @param idempotencyKey 한 번의 사용자 조작을 가리키는 값. 같은 값으로 다시 들어오면 새로 만들지 않고 먼저 만들어진 것을 돌려준다.
   */
  WrongAnswerSetResponse createFromFolder(
      String userId, CreateWrongAnswerSetRequest request, String idempotencyKey);
}
