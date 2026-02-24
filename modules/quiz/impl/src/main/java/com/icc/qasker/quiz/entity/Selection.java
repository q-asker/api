package com.icc.qasker.quiz.entity;

import static jakarta.persistence.FetchType.LAZY;

import com.icc.qasker.global.entity.CreatedAt;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinColumns;
import jakarta.persistence.ManyToOne;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Selection extends CreatedAt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(columnDefinition = "TEXT")
    private String content;
    @Column(nullable = false)
    private boolean correct;

    @ManyToOne(fetch = LAZY)
    @JoinColumns({
        @JoinColumn(name = "problem_set_id", referencedColumnName = "problem_set_id"),
        @JoinColumn(name = "number", referencedColumnName = "number")
    })
    private Problem problem;

}