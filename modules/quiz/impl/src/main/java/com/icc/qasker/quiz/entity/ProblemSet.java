package com.icc.qasker.quiz.entity;

import com.icc.qasker.global.entity.CreatedAt;
import com.icc.qasker.global.error.CustomException;
import com.icc.qasker.global.error.ExceptionMessage;
import com.icc.qasker.quiz.GenerationStatus;
import com.icc.qasker.quiz.dto.aiResponse.ProblemSetGeneratedEvent;
import com.icc.qasker.quiz.dto.feRequest.enums.QuizType;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
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
public class ProblemSet extends CreatedAt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String userId;

    @OneToMany(mappedBy = "problemSet", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Problem> problems;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    @Column(nullable = false)
    private GenerationStatus status = GenerationStatus.GENERATING;

    @Enumerated(EnumType.STRING)
    private QuizType quizType;

    private Integer totalQuizCount;

    private String sessionId;


    // 이하 헬퍼 함수
    public static ProblemSet of(ProblemSetGeneratedEvent aiResponse) {
        return of(aiResponse, null);
    }

    public static ProblemSet of(ProblemSetGeneratedEvent aiResponse, String userId) {
        if (aiResponse == null || aiResponse.getQuiz() == null) {
            throw new CustomException(ExceptionMessage.NULL_AI_RESPONSE);
        }
        ProblemSet problemSet = ProblemSet.builder().userId(userId).build();
        problemSet.problems = aiResponse.getQuiz().stream()
            .map(quizDto -> Problem.of(quizDto, problemSet))
            .toList();
        return problemSet;
    }

    public void updateStatus(GenerationStatus status) {
        this.status = status;
    }
}
