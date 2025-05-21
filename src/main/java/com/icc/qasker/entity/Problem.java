package com.icc.qasker.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@AllArgsConstructor
@Entity
@NoArgsConstructor
@Builder   
public class Problem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String title;

    private String correctAnswer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "problem_set_id")
    private ProblemSet problemSet;

    @OneToMany(mappedBy = "problem", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Selection> selections;

    @OneToOne(mappedBy = "problem", cascade = CascadeType.ALL, orphanRemoval = true)
    private Explanation explanation;
}
