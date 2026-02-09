package com.icc.qasker.quiz.entity;

import static jakarta.persistence.FetchType.LAZY;
import static java.util.stream.Collectors.toList;

import com.icc.qasker.global.entity.CreatedAt;
import com.icc.qasker.quiz.dto.airesponse.ProblemSetGeneratedEvent.QuizGeneratedFromAI;
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
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
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
    @Builder.Default
    private List<ReferencedPage> referencedPages = new ArrayList<>();

    // 이하 헬퍼 함수
    public static Problem of(QuizGeneratedFromAI quizDto, ProblemSet problemSet) {
        Problem problem = new Problem();
        ProblemId problemId = ProblemId.builder()
                .number(quizDto.getNumber())
                .build();

        problem.id = problemId;
        problem.title = quizDto.getTitle();
        problem.problemSet = problemSet;
        problem.selections = quizDto.getSelections() == null ? new ArrayList<>()
                : quizDto.getSelections()
                        .stream()
                        .map(selDto -> Selection.of(selDto, problem))
                        .collect(toList());
        problem.explanation = Explanation.of(quizDto.getExplanation(), problem);
        problem.referencedPages = quizDto.getReferencedPages() == null ? new ArrayList<>()
                : quizDto.getReferencedPages()
                        .stream()
                        .map(page -> ReferencedPage.of(page, problem))
                        .collect(toList());

        return problem;
    }
}