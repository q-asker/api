package com.icc.qasker.ai.dto;

import java.util.List;

public record AIProblem(
    int number,
    String content,
    String explanation,
    List<AISelection> selections,
    List<Integer> referencedPages) {}
