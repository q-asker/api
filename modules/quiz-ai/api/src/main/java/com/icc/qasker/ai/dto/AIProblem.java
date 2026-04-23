package com.icc.qasker.ai.dto;

import java.util.List;

public record AIProblem(
    String content,
    String bloomsLevel,
    List<AISelection> selections,
    List<Integer> referencedPages,
    String appliedInstruction) {}
