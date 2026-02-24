package com.icc.qasker.ai.dto;

import java.util.List;

public record ChunkInfo(
    List<Integer> referencedPages,
    int quizCount
) {

}
