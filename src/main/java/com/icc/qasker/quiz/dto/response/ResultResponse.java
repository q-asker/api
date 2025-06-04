package com.icc.qasker.quiz.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
public class ResultResponse {
    private int number;
    private String explanation;
    private List<Integer> referencedPages;
}
