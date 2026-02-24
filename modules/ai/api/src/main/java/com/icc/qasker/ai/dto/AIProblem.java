package com.icc.qasker.ai.dto;

import java.util.List;

public record AIProblem(
    int number,
    String title,
    List<AISelection> selections,
    String explanation,
    List<Integer> referencedPages
) {

}
