package com.icc.qasker.ai.dto;

import java.util.List;
import lombok.Builder;

@Builder
public record GenerationRequestToAI(
    String fileUrl,
    String strategyValue,
    int quizCount,
    List<Integer> referencePages
) {

}
