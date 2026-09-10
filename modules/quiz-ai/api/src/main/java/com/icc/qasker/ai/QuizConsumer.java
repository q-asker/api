package com.icc.qasker.ai;

import com.icc.qasker.ai.dto.AIProblem;

public interface QuizConsumer {

  int saveProblem(AIProblem problem);

  default void recordV1(int number, AIProblem v1, String v1Feedback) {}

  default void recordV2(int number, AIProblem v2) {}
}
