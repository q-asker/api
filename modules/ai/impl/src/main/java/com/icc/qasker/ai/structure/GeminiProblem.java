package com.icc.qasker.ai.structure;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import java.util.List;

public record GeminiProblem(
    @JsonPropertyDescription("문제 번호 (1부터 시작)")
    int number,

    @JsonPropertyDescription("문제 본문")
    String title,

    @JsonPropertyDescription("선택지 목록")
    List<GeminiSelection> selections,

    @JsonPropertyDescription("정답 해설")
    String explanation
) {

}
