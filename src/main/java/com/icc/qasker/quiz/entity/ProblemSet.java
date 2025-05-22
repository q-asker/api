package com.icc.qasker.quiz.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Entity
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class ProblemSet {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id; // 기본키
    private String title; // 문제집 이름

    @OneToMany(mappedBy = "problemSet", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Problem> problems;
}
