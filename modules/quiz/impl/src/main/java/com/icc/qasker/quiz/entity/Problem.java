package com.icc.qasker.quiz.entity;

import static jakarta.persistence.FetchType.LAZY;
import static java.util.stream.Collectors.toList;

import com.icc.qasker.global.entity.CreatedAt;
import com.icc.qasker.quiz.dto.aiResponse.ProblemSetGeneratedEvent.QuizGeneratedFromAI;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Entity
@Getter
@Setter
public class Problem extends CreatedAt {

    @EmbeddedId
    private ProblemId id;
    @Column(columnDefinition = "TEXT")
    private String title;

    @ManyToOne(fetch = LAZY)
    @MapsId("problemSetId")
    @JoinColumn(name = "problem_set_id")
    private ProblemSet problemSet;

    @OneToMany(mappedBy = "problem", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Selection> selections;

    @OneToOne(mappedBy = "problem", cascade = CascadeType.ALL, orphanRemoval = true)
    private Explanation explanation;

    @OneToMany(mappedBy = "problem", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ReferencedPage> referencedPages = new ArrayList<>();

    public static Problem of(QuizGeneratedFromAI quizDto, ProblemSet problemSet) {
        Problem problem = new Problem();
        ProblemId problemId = new ProblemId();
        problemId.setNumber(quizDto.getNumber());
        problem.setId(problemId);
        problem.setTitle(quizDto.getTitle());

        problem.setProblemSet(problemSet);

        problem.setSelections(
            quizDto.getSelections().stream()
                .map(selDto -> Selection.of(selDto, problem))
                .collect(toList())
        );
        problem.setExplanation(Explanation.of(quizDto.getExplanation(), problem));

        problem.setReferencedPages(
            quizDto.getReferencedPages().stream()
                .map(page -> ReferencedPage.of(page, problem))
                .collect(toList())
        );

        return problem;
    }
}