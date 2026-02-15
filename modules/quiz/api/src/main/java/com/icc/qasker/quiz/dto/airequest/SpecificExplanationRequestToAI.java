package com.icc.qasker.quiz.dto.airequest;

import com.icc.qasker.quiz.dto.airesponse.ProblemSetGeneratedEvent.QuizGeneratedFromAI.SelectionsOfAI;
import java.util.List;

public record SpecificExplanationRequestToAI(
    String title,
    List<SelectionsOfAI> selections
) {


};
