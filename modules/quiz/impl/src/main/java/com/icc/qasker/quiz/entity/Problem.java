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
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
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
    private List<ReferencedPage> referencedPages = new ArrayList<>();

    // 이하 헬퍼 함수
    public static Problem of(QuizGeneratedFromAI quizDto, ProblemSet problemSet) {
        Problem problem = new Problem();
        ProblemId problemId = ProblemId.builder()
            .number(quizDto.getNumber())
            .build();

        return Problem.builder()
            .id(problemId)
            .title(quizDto.getTitle())
            .problemSet(problemSet)
            .selections(
                quizDto.getSelections().stream()
                    .map(selDto -> Selection.of(selDto, problem))
                    .collect(toList())
            )
            .explanation(Explanation.of(quizDto.getExplanation(), problem))
            .referencedPages(
                quizDto.getReferencedPages().stream()
                    .map(page -> ReferencedPage.of(page, problem))
                    .collect(toList())
            ).build();
    }
}