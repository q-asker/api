package com.icc.qasker.quiz.entity;

import jakarta.persistence.Embeddable;
import lombok.*;

import java.io.Serializable;

@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode

public class ProblemId implements Serializable {
    // 복합키
    private Long problemSetId;
    private Long number;
    // cf)
    // number: 문제의 번호 (1번 문제, 2번 문제)
    // problemId: 현재 user가 풀고 있는 문제집 id와 해당 문제의 문제 번호를 합친 복합키

}
