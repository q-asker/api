package com.icc.qasker.cost.dto;

/** AI 호출 결과 상태. 성공/실패 모두 비용 이벤트로 적재되며, 실패는 errorCode와 함께 기록된다. */
public enum InvocationStatus {
  SUCCESS,
  ERROR
}
