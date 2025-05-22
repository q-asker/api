package com.icc.qasker.quiz.dto.request;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class AnswerRequest {
    private Long number; // 보기의 번호
    private Long userAnswer; // client의 답
}
