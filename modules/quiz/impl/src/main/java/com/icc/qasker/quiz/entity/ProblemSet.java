package com.icc.qasker.quiz.entity;

import com.icc.qasker.global.entity.CreatedAt;
import com.icc.qasker.global.error.CustomException;
import com.icc.qasker.global.error.ExceptionMessage;
import com.icc.qasker.quiz.dto.response.AiGenerationResponse;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Setter
public class ProblemSet extends CreatedAt {


    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String userId;

    @OneToMany(mappedBy = "problemSet", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Problem> problems;

    public static ProblemSet of(AiGenerationResponse aiResponse) {
        return of(aiResponse, null);
    }

    public static ProblemSet of(AiGenerationResponse aiResponse, String userId) {
        if (aiResponse == null || aiResponse.getQuiz() == null) {
            throw new CustomException(ExceptionMessage.NULL_AI_RESPONSE);
        }
        ProblemSet problemSet = new ProblemSet();
        problemSet.setUserId(userId);
        List<Problem> problems = aiResponse.getQuiz().stream()
            .map(quizDto -> Problem.of(quizDto, problemSet))
            .toList();
        problemSet.problems = problems;
        return problemSet;
    }
}
