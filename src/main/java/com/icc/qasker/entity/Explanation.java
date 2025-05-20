package com.icc.qasker.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Getter @NoArgsConstructor @AllArgsConstructor
public class Explanation {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String content;

    @OneToOne
    @JoinColumn(name = "problem_id")
    private Problem problem;
}
