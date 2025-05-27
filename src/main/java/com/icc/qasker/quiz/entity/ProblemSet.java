package com.icc.qasker.quiz.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.List;

@Entity
@Getter
@RequiredArgsConstructor
@Setter
public class ProblemSet {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private final String title;

    @OneToMany(mappedBy = "problemSet", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Problem> problems;
}
