package com.icc.qasker.ai.dto;

import com.icc.qasker.ai.QuizBatchSink;
import java.util.List;
import lombok.Builder;

@Builder
public record GenerationRequestToAI(
    String fileUrl,
    String strategyValue,
    String language,
    int quizCount,
    List<Integer> referencePages,
    QuizBatchSink sink,
    String customInstruction,
    // 원본 quizType이 REAL_BLANK인지 — AI 전략명은 BLANK로 붕괴되므로(toAiStrategyName) 인정 답 생성 분기용으로 별도 전달.
    boolean realBlank) {}
