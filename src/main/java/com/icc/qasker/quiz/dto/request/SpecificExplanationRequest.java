package com.icc.qasker.quiz.dto.request;

import com.icc.qasker.quiz.dto.response.QuizGeneratedByAI.SelectionsOfAi;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Setter
@AllArgsConstructor
@NoArgsConstructor
@Getter
public class SpecificExplanationRequest {

    private String title;
    private List<SelectionsOfAi> selections;
}
