package com.icc.qasker.quiz.dto.feResponse;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class ResultResponse {

    private int number;
    private String explanation;
    private List<Integer> referencedPages;
}
