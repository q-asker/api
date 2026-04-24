package com.icc.qasker.quizmake.dto.airequest;

import com.icc.qasker.quizset.dto.ferequest.enums.QuizType;
import java.util.List;
import lombok.Builder;

@Builder
public record GenerationRequestToAI(
    String uploadedUrl, int quizCount, QuizType quizType, List<Integer> pageNumbers) {}
;
