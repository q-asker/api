package com.icc.qasker.quiz.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Getter @RequiredArgsConstructor @Setter
public class Explanation {
    @EmbeddedId
    private final ProblemId id;
    private final String content;

    @OneToOne
    @MapsId
    @JoinColumns({
            @JoinColumn(name = "problem_set_id", referencedColumnName = "problem_set_id"),
            @JoinColumn(name = "number", referencedColumnName = "number")
    })
    private final Problem problem;
}
