package com.icc.qasker.quiz.dto.response;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
public class QuizGeneratedByAI {

    @NotNull(message = "number가 null입니다.")
    private int number;

    @NotBlank(message = "title이 존재하지 않습니다.")
    private String title;

    @NotNull(message = "selections가 null입니다.")
    @Size(min = 4, max = 4, message = "selections가 4개가 아닙니다.")
    private List<SelectionsOfAi> selections;

    @NotBlank(message = "explanation이 null입니다.")
    private String explanation;

    @NotNull(message = "referencedPages가 null입니다.")
    private List<Integer> referencedPages;

    @Getter
    @Setter
    public static class SelectionsOfAi {

        @NotBlank(message = "selection의 content가 존재하지 않습니다.")
        private String content;
        private boolean correct;
    }
}
