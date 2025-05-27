package com.icc.qasker.quiz.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Getter @RequiredArgsConstructor @Setter
public class Problem {
    @EmbeddedId
    private final ProblemId id;
    private final String title;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("problemSetId")
    @JoinColumn(name = "problem_set_id")
    private final ProblemSet problemSet;

    @OneToMany(mappedBy = "problem", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Selection> selections;

    @OneToOne(mappedBy = "problem", cascade = CascadeType.ALL, orphanRemoval = true)
    private Explanation explanation;
}