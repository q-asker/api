package com.slb.qasker.entity;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
// 추후 entity 추가
public class Problem {
    private Long problemId;
    private String correctAnswer;
    private String explanation;
}
