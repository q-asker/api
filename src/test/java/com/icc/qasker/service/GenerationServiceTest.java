package com.icc.qasker.service;

import com.icc.qasker.quiz.dto.response.AiGenerationResponse;
import com.icc.qasker.quiz.dto.response.QuizGeneratedByAI;
import com.icc.qasker.quiz.entity.ProblemSet;
import com.icc.qasker.quiz.entity.Selection;
import com.icc.qasker.quiz.repository.ProblemRepository;
import com.icc.qasker.quiz.repository.ProblemSetRepository;
import com.icc.qasker.quiz.repository.SelectionRepository;
import com.icc.qasker.quiz.service.GenerationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.reactive.function.client.WebClient;


import java.util.Arrays;
import java.util.List;


import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class GenerationServiceTest {
    @Mock @Qualifier("aiWebClient")
    private WebClient aiWebClient;
    @Mock
    private ProblemSetRepository problemSetRepository;
    @Mock
    private ProblemRepository problemRepository;
    @Mock
    private SelectionRepository selectionRepository;
    @InjectMocks
    private GenerationService generationService;

    private AiGenerationResponse response;
    private QuizGeneratedByAI quiz1;
    private QuizGeneratedByAI quiz2;
    private ProblemSet savedSet;


    @BeforeEach
    void setUp() {
        response = new AiGenerationResponse();
        response.setTitle("예시 문제집 1");

        quiz1 = new QuizGeneratedByAI();
        quiz1.setNumber(1);
        quiz1.setTitle("1+1은 ?");
        quiz1.setSelections(Arrays.asList("1","2","3","4"));
        quiz1.setCorrectAnswer(1);

        quiz2 = new QuizGeneratedByAI();
        quiz2.setNumber(2);
        quiz2.setTitle("대한 민국의 수도는 ?");
        quiz2.setSelections(Arrays.asList("서울","인천","부산","광주"));
        quiz2.setCorrectAnswer(0);

        response.setQuiz(Arrays.asList(quiz1,quiz2));

    }
    @Test
    void givenAiResponse_whenSaveToDB_thenSavedToRepositoriesCorrectly(){
        // given
        when(selectionRepository.saveAll(Mockito.<List<Selection>>any()))
                .thenAnswer(invocation -> invocation.getArgument(0));

        when(problemSetRepository.save(Mockito.any(ProblemSet.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // when
        ProblemSet result = generationService.saveProblemSet(response); // for test
        // then
        assertThat(result.getTitle()).isEqualTo(response.getTitle());
        assertThat(result.getProblems()).hasSize(response.getQuiz().size());
        for (int i = 0; i < response.getQuiz().size(); i++) {
            QuizGeneratedByAI quizFromAi = response.getQuiz().get(i);
            var problem = result.getProblems().get(i);

            assertThat(problem.getTitle()).isEqualTo(quizFromAi.getTitle());
            assertThat(problem.getSelections()).hasSize(quizFromAi.getSelections().size());

            var correctIndex = quizFromAi.getCorrectAnswer();
            var correctSelection = problem.getSelections().get(correctIndex);

            assertThat(problem.getCorrectAnswer()).isEqualTo(correctSelection.getId());
            assertThat(problem.getExplanation().getContent()).isEqualTo(quizFromAi.getExplanation());
        }
    }
}
