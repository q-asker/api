package com.icc.qasker.quiz.entity;

import com.icc.qasker.global.entity.CreatedAt;
import com.icc.qasker.global.error.CustomException;
import com.icc.qasker.global.error.ExceptionMessage;
import com.icc.qasker.quiz.dto.aiResponse.QuizEvent;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class ProblemSet extends CreatedAt {


    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String userId;

    @OneToMany(mappedBy = "problemSet", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Problem> problems;

    @Builder
    public ProblemSet(String userId) {
        this.userId = userId;
    }

    public static ProblemSet of(QuizEvent aiResponse) {
        return of(aiResponse, null);
    }

    public static ProblemSet of(QuizEvent aiResponse, String userId) {
        if (aiResponse == null || aiResponse.getQuiz() == null) {
            throw new CustomException(ExceptionMessage.NULL_AI_RESPONSE);
        }
        ProblemSet problemSet = ProblemSet.builder().userId(userId).build();
        problemSet.problems = aiResponse.getQuiz().stream()
            .map(quizDto -> Problem.of(quizDto, problemSet))
            .toList();
        return problemSet;
    }
}
