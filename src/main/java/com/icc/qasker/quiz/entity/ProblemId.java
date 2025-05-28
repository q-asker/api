package com.icc.qasker.quiz.entity;

import jakarta.persistence.Embeddable;
import lombok.*;

import java.io.Serializable;

@Embeddable
@Getter
@EqualsAndHashCode
@NoArgsConstructor
@AllArgsConstructor
@Setter
public class ProblemId implements Serializable {
    private Long problemSetId;
    private  int number;

}
