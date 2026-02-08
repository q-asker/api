package com.icc.qasker.quiz.entity;

import com.icc.qasker.global.entity.CreatedAt;
import com.icc.qasker.global.error.CustomException;
import com.icc.qasker.global.error.ExceptionMessage;
import com.icc.qasker.quiz.GenerationStatus;
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
import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Entity
@Getter
@Slf4j
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class ProblemSet extends CreatedAt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String userId;

    @OneToMany(mappedBy = "problemSet", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<Problem> problems = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    @Builder.Default
    @Column(nullable = false)
    private GenerationStatus status = GenerationStatus.GENERATING;

    @Enumerated(EnumType.STRING)
    private QuizType quizType;

    private Integer totalQuizCount;

    @Column(unique = true, nullable = false)
    private String sessionId;

    // 이하 헬퍼 함수
    public void updateStatus(GenerationStatus status) {
        if (status == null) {
            log.error("ProblemSet status는 null일 수 없습니다");
            throw new CustomException(ExceptionMessage.DEFAULT_ERROR);
        }
        this.status = status;
    }
}
