package com.icc.qasker.quiz.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Getter @NoArgsConstructor @Setter @AllArgsConstructor
public class Selection {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String content;
    @Column(nullable = false)
    private boolean correct;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumns({
            @JoinColumn(name = "problem_set_id", referencedColumnName = "problem_set_id"),
            @JoinColumn(name = "number", referencedColumnName = "number")
    })
    private Problem problem;
}
