package com.icc.qasker.quiz.entity;

import com.icc.qasker.global.entity.CreatedAt;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter
@NoArgsConstructor
@Setter
public class Explanation extends CreatedAt {

    @EmbeddedId
    private ProblemId id;
    @Column(columnDefinition = "TEXT")
    private String content;

    @OneToOne
    @MapsId
    @JoinColumns({
            @JoinColumn(name = "problem_set_id", referencedColumnName = "problem_set_id"),
            @JoinColumn(name = "number", referencedColumnName = "number")
    })
    private Problem problem;
}
