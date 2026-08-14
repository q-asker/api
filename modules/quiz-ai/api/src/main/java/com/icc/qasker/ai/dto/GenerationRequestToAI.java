package com.icc.qasker.ai.dto;

import com.icc.qasker.ai.QuizBatchSink;
import java.util.List;
import lombok.Builder;

@Builder
public record GenerationRequestToAI(
    String fileUrl,
    String quizType,
    String language,
    int quizCount,
    List<Integer> referencePages,
    QuizBatchSink sink,
    String customInstruction) {}
