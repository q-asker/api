package com.icc.qasker.quiz.entity;

import jakarta.persistence.Embeddable;
import lombok.*;

import java.io.Serializable;

@Embeddable
@Getter
@RequiredArgsConstructor
@EqualsAndHashCode

public class ProblemId implements Serializable {
    private final Long problemSetId;
    private final int number;

}
