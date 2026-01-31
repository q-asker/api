package com.icc.qasker.quiz.dto.aiResponse;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class QuizEvent implements StreamEvent {

    @NotEmpty(message = "quiz가 null입니다.")
    @Valid
    private List<QuizGeneratedFromAI> quiz;

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class QuizGeneratedFromAI {

        @NotNull(message = "number가 null입니다.")
        private Integer number;

        @NotBlank(message = "title이 존재하지 않습니다.")
        private String title;

        @NotNull(message = "selections가 null입니다.")
        private List<SelectionsOfAi> selections;

        @NotBlank(message = "explanation이 null입니다.")
        private String explanation;

        @NotNull(message = "referencedPages가 null입니다.")
        private List<Integer> referencedPages;

        @Getter
        @Setter
        @NoArgsConstructor
        @AllArgsConstructor
        public static class SelectionsOfAi {

            @NotBlank(message = "selection의 content가 존재하지 않습니다.")
            private String content;
            private boolean correct;
        }
    }
}
