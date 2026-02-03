package com.icc.qasker.quiz.service;

import com.icc.qasker.global.error.CustomException;
import com.icc.qasker.global.error.ExceptionMessage;
import com.icc.qasker.quiz.dto.aiResponse.ProblemSetGeneratedEvent.QuizGeneratedFromAI;
import com.icc.qasker.quiz.entity.Problem;
import com.icc.qasker.quiz.entity.ProblemSet;
import com.icc.qasker.quiz.entity.ProblemSet.GenerationStatus;
import com.icc.qasker.quiz.repository.ProblemRepository;
import com.icc.qasker.quiz.repository.ProblemSetRepository;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@AllArgsConstructor
public class QuizCommandService {

    private final ProblemSetRepository problemSetRepository;
    private final ProblemRepository problemRepository;

    @Transactional(readOnly = true)
    public GenerationStatus getGenerationStatus(Long problemSetId) {
        return problemSetRepository.findById(problemSetId)
            .map(ProblemSet::getStatus)
            .orElseThrow(() -> new CustomException(ExceptionMessage.PROBLEM_SET_NOT_FOUND));
    }


    @Transactional(readOnly = true)
    public Long getCount(Long id) {
        return problemRepository.countByIdProblemSetId(id);
    }

    @Transactional
    public Long initProblemSet(String userId) {
        ProblemSet problemSet = ProblemSet
            .builder()
            .userId(userId)
            .build();
        problemSetRepository.save(problemSet);
        return problemSet.getId();
    }

    @Transactional
    public void updateStatus(Long problemSetId, GenerationStatus status) {
        ProblemSet problemSet = problemSetRepository.findById(problemSetId)
            .orElseThrow(() -> new CustomException(ExceptionMessage.PROBLEM_SET_NOT_FOUND));
        problemSet.updateStatus(status);
    }

    @Transactional
    public List<Problem> saveAll(List<QuizGeneratedFromAI> generatedProblems, Long problemSetId) {
        ProblemSet problemSet = problemSetRepository.findById(problemSetId)
            .orElseThrow(() -> new CustomException(ExceptionMessage.PROBLEM_SET_NOT_FOUND));
        List<Problem> problems = new ArrayList<>();
        for (QuizGeneratedFromAI quiz : generatedProblems) {
            problems.add(Problem.of(quiz, problemSet));
        }
        problemRepository.saveAll(problems);
        return problems;
    }

    @Transactional
    public void delete(Long id) {
        problemSetRepository.deleteById(id);
    }
}
