package com.icc.qasker.ai.dto;

import com.icc.qasker.ai.QuizConsumer;
import com.icc.qasker.global.quiz.QuizType;
import java.util.List;
import lombok.Builder;

@Builder
public record GenerationRequestToAI(
    String fileUrl,
    QuizType quizType,
    String language,
    int quizCount,
    List<Integer> referencePages,
    QuizConsumer consumer,
    String customInstruction) {}
