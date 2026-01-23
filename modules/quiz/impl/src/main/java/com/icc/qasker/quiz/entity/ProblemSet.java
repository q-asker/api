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
    private String title;
    private String userId;

    @OneToMany(mappedBy = "problemSet", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Problem> problems;

    /**
     * Create a ProblemSet from an AI generation response with no user identifier.
     *
     * @param aiResponse the AI generation response containing title and quiz data
     * @return a ProblemSet constructed from the given response; the resulting ProblemSet's `userId` will be `null`
     */
    public static ProblemSet of(AiGenerationResponse aiResponse) {
        return of(aiResponse, null);
    }

    /**
     * Create a ProblemSet from an AI generation response and associate it with an optional user ID.
     *
     * Constructs a ProblemSet whose title is taken from the AI response and whose problems are
     * created from the response's quiz items; each Problem is linked to the created ProblemSet.
     *
     * @param aiResponse the AI generation response containing a title and quiz items; must not be null and must contain a quiz list
     * @param userId an optional user identifier to associate with the ProblemSet; may be null
     * @return a new ProblemSet populated with the title, userId, and converted Problem instances
     * @throws CustomException with ExceptionMessage.NULL_AI_RESPONSE if {@code aiResponse} is null or {@code aiResponse.getQuiz()} is null
     */
    public static ProblemSet of(AiGenerationResponse aiResponse, String userId) {
        if (aiResponse == null || aiResponse.getQuiz() == null) {
            throw new CustomException(ExceptionMessage.NULL_AI_RESPONSE);
        }
        ProblemSet problemSet = new ProblemSet();
        problemSet.setTitle(aiResponse.getTitle());
        problemSet.setUserId(userId);
        List<Problem> problems = aiResponse.getQuiz().stream()
            .map(quizDto -> Problem.of(quizDto, problemSet))
            .toList();
        problemSet.problems = problems;
        return problemSet;
    }
}