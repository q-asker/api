package com.icc.qasker.quiz.dto.aiRequest;

import com.icc.qasker.quiz.dto.aiResponse.QuizEvent.QuizGeneratedFromAI.SelectionsOfAi;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class SpecificExplanationRequestToAI {

    private String title;
    private List<SelectionsOfAi> selections;
}
