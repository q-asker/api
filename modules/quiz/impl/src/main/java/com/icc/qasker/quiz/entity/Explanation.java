package com.icc.qasker.quiz.entity;

import static jakarta.persistence.FetchType.LAZY;

import com.icc.qasker.global.entity.CreatedAt;
import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinColumns;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter
@NoArgsConstructor
@Setter
public class Explanation extends CreatedAt {

    private static final int MAX_CONTENT_LENGTH = 20000;

    @EmbeddedId
    private ProblemId id;
    @Column(columnDefinition = "TEXT")
    private String content;

    @OneToOne(fetch = LAZY)
    @MapsId
    @JoinColumns({
        @JoinColumn(name = "problem_set_id", referencedColumnName = "problem_set_id"),
        @JoinColumn(name = "number", referencedColumnName = "number")
    })
    private Problem problem;

    public static Explanation of(String content, Problem problem) {
        Explanation explanation = new Explanation();
        if (content.length() > MAX_CONTENT_LENGTH) {
            content = content.substring(0, MAX_CONTENT_LENGTH);
        }
        explanation.setContent(content);
        explanation.setProblem(problem);
        return explanation;
    }
}
