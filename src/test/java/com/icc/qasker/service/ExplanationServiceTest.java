package com.icc.qasker.service;

import com.icc.qasker.dto.request.AnswerRequest;
import com.icc.qasker.dto.request.ExplanationRequest;
import com.icc.qasker.dto.response.ResultResponse;
import com.icc.qasker.entity.Explanation;
import com.icc.qasker.entity.Problem;
import com.icc.qasker.repository.ProblemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExplanationServiceTest {

    @InjectMocks
    private ExplanationService explanationService;

    @Mock
    private ProblemRepository problemRepository;
    // mock data

    private Problem problem1;
    private Problem problem2;
    private Explanation explanation1;
    private Explanation explanation2;

    @BeforeEach
    void setUp() {
        explanation1 = Explanation.builder()
                .content("A라서")
                .build();

        explanation2 = Explanation.builder()
                .content("B라서")
                .build();

        problem1 = Problem.builder()
                .id(1L)
                .correctAnswer("A")
                .explanation(explanation1)
                .build();

        problem2 = Problem.builder()
                .id(2L)
                .correctAnswer("B")
                .explanation(explanation2)
                .build();
    }

    @Test
    void gradeUserAnswers() {
        // given
        AnswerRequest answer1 = new AnswerRequest(1L, "A");
        AnswerRequest answer2 = new AnswerRequest(2L, "B");
        ExplanationRequest request = new ExplanationRequest(Arrays.asList(answer1, answer2));

        when(problemRepository.findById(1L)).thenReturn(Optional.of(problem1));
        when(problemRepository.findById(2L)).thenReturn(Optional.of(problem2));

        // when
        List<ResultResponse> results = explanationService.gradeUserAnswers(request);

        // then
        assertThat(results).hasSize(2);
        assertThat(results.get(0).isCorrect()).isTrue();
        assertThat(results.get(1).isCorrect()).isTrue();
        assertThat(results.get(0).getExplanation()).isEqualTo("A라서");
        assertThat(results.get(1).getExplanation()).isEqualTo("B라서");
    }

    @Test
    void gradeUserAnswersWithInvalidProblemId() {
        // given
        AnswerRequest invalidAnswer = new AnswerRequest(999L, "C");
        ExplanationRequest request = new ExplanationRequest(Arrays.asList(invalidAnswer));

        when(problemRepository.findById(999L)).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> explanationService.gradeUserAnswers(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("해당 문제 ID가 존재하지 않습니다");
    }

    @Test
    void gradeUserAnswersWithoutExplanation() {
        // given
        Problem problemWithoutExplanation = Problem.builder()
                .id(3L)
                .correctAnswer("정답3")
                .build();

        AnswerRequest answer = new AnswerRequest(3L, "정답3");
        ExplanationRequest request = new ExplanationRequest(Arrays.asList(answer));

        when(problemRepository.findById(3L)).thenReturn(Optional.of(problemWithoutExplanation));

        // when
        List<ResultResponse> results = explanationService.gradeUserAnswers(request);

        // then
        assertThat(results).hasSize(1);
        assertThat(results.get(0).getExplanation()).isEqualTo("해당 문제에 대한 해설이 존재하지 않습니다.");
    }
}
