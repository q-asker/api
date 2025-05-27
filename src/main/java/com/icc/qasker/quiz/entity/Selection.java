package com.icc.qasker.quiz.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Getter @RequiredArgsConstructor @Setter
public class Selection {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private final String content;
    @Column(nullable = false)
    private final boolean isCorrect;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumns({
            @JoinColumn(name = "problem_set_id", referencedColumnName = "problem_set_id"),
            @JoinColumn(name = "number", referencedColumnName = "number")
    })
    private final Problem problem;
}
