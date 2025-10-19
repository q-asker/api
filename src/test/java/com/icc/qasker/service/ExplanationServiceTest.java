//package com.icc.qasker.service;
//
//import com.icc.qasker.global.error.CustomException;
//import com.icc.qasker.quiz.dto.response.ResultResponse;
//import com.icc.qasker.quiz.entity.Explanation;
//import com.icc.qasker.quiz.entity.Problem;
//import com.icc.qasker.quiz.entity.ProblemId;
//import com.icc.qasker.quiz.entity.ProblemSet;
//import com.icc.qasker.quiz.repository.ProblemRepository;
//import com.icc.qasker.quiz.service.ExplanationService;
//import org.junit.jupiter.api.BeforeEach;
//import org.junit.jupiter.api.Test;
//import org.junit.jupiter.api.extension.ExtendWith;
//import org.mockito.InjectMocks;
//import org.mockito.Mock;
//import org.mockito.junit.jupiter.MockitoExtension;
//
//import java.util.ArrayList;
//import java.util.Arrays;
//import java.util.List;
//import java.util.Optional;
//
//import static org.assertj.core.api.Assertions.assertThat;
//import static org.assertj.core.api.Assertions.assertThatThrownBy;
//import static org.mockito.Mockito.when;
//
//@ExtendWith(MockitoExtension.class)
//class ExplanationServiceTest {
//
//    @InjectMocks
//    private ExplanationService explanationService;
//
//    @Mock
//    private ProblemRepository problemRepository;
//    // mock data
//    private ProblemSet problemSet;
//    private List<Problem> problems;
//    private ProblemId problemId1;
//    private ProblemId problemId2;
//    private Problem problem1;
//    private Problem problem2;
//    private Explanation explanation1;
//    private Explanation explanation2;
//
//    @BeforeEach
//    void setUp() {
//        problems = new ArrayList<>();
//        explanation1 = new Explanation();
//        explanation1.setContent("답이 1이라서");
//
//        explanation2 = new Explanation();
//        explanation2.setContent("답이 5라서");
//
//        problem1 = new Problem();
//        problemId1 = new ProblemId(1L,1);
//        problem1.setId(problemId1);
//        problem1.setCorrectAnswer(1L);
//        problem1.setExplanation(explanation1);
//
//        problem2 = new Problem();
//        problemId2 = new ProblemId(1L,2);
//        problem2.setId(problemId2);
//        problem2.setCorrectAnswer(5L);
//        problem2.setExplanation(explanation2);
//
//        problems.add(problem1);
//        problems.add(problem2);
//        problemSet = new ProblemSet(1L,"예시 문제집 1",problems);
//    }
//
//    @Test
//    void gradeUserAnswers() {
//        // given
//        AnswerRequest answer1 = new AnswerRequest(1, 1L); // expect: true
//        AnswerRequest answer2 = new AnswerRequest(2, 5L); // expect: false
//        ExplanationRequest request = new ExplanationRequest(1L,Arrays.asList(answer1, answer2));
//        ProblemId newProblemId1 = new ProblemId(1L,answer1.getNumber());
//        ProblemId newProblemId2 = new ProblemId(1L,answer2.getNumber());
//
//        // when
//        when(problemRepository.findById(newProblemId1)).thenReturn(Optional.of(problem1));
//        when(problemRepository.findById(newProblemId2)).thenReturn(Optional.of(problem2));
//        List<ResultResponse> results = explanationService.gradeUserAnswers(request);
//
//        // then
//        assertThat(results).hasSize(2);
//        assertThat(results.get(0).isCorrect()).isTrue();
//        assertThat(results.get(1).isCorrect()).isTrue();
//        assertThat(results.get(0).getExplanation()).isEqualTo("답이 1이라서");
//        assertThat(results.get(1).getExplanation()).isEqualTo("답이 5라서");
//    }
//
//    @Test
//    void gradeUserAnswersWithInvalidProblemId() {
//        // given
//        AnswerRequest invalidAnswer = new AnswerRequest(999, 5L);
//        ExplanationRequest request = new ExplanationRequest(1L,Arrays.asList(invalidAnswer));
//        ProblemId newProblemId = new ProblemId(1L,invalidAnswer.getNumber());
//
//        when(problemRepository.findById(newProblemId)).thenReturn(Optional.empty());
//
//        // when & then
//        assertThatThrownBy(() -> explanationService.gradeUserAnswers(request))
//                .isInstanceOf(CustomException.class)
//                .hasMessageContaining("해당 문제를 찾을 수 없습니다.");
//    }
//
//    @Test
//    void gradeUserAnswersWithoutExplanation() {
//        // given
//        Problem problem3 = new Problem();
//        ProblemId problemId3 = new ProblemId(1L,3);
//        problem3.setId(problemId3);
//        problem3.setCorrectAnswer(1L);
//
//        AnswerRequest NoExplanationAnswer = new AnswerRequest(3, 2L);
//        ExplanationRequest request = new ExplanationRequest(1L,Arrays.asList(NoExplanationAnswer));
//        ProblemId newProblemId = new ProblemId(1L,NoExplanationAnswer.getNumber());
//
//        when(problemRepository.findById(newProblemId)).thenReturn(Optional.of(problem3));
//
//        // when
//        List<ResultResponse> results = explanationService.gradeUserAnswers(request);
//
//        // then
//        assertThat(results).hasSize(1);
//        assertThat(results.get(0).getExplanation()).isEqualTo("해설 없음");
//    }
//}
