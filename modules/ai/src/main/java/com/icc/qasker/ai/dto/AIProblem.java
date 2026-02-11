package com.icc.qasker.ai.dto;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import java.util.List;

public record AIProblem(
    @JsonPropertyDescription("문제 번호입니다 (1부터 시작).")
    int number,

    @JsonPropertyDescription("문제의 지문(질문) 내용입니다. 명확하고 구체적이어야 합니다.")
    String title,

    @JsonPropertyDescription("객관식 선택지 목록입니다. (객관식인 경우 필수, 그 외 빈 리스트)")
    List<AISelection> selections,

    @JsonPropertyDescription("정답에 대한 상세한 해설입니다.")
    String explanation
) {

}