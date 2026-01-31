package com.icc.qasker.quiz.dto.aiRequest;

import com.icc.qasker.quiz.dto.aiResponse.ProblemSetGeneratedEvent.QuizGeneratedFromAI.SelectionsOfAI;
import java.util.List;

public record SpecificExplanationRequestToAI(
    String title,
    List<SelectionsOfAI> selections
) {


};
