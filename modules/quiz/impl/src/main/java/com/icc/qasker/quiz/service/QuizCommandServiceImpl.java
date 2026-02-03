package com.icc.qasker.quiz.service;

import com.icc.qasker.global.component.HashUtil;
import com.icc.qasker.global.error.CustomException;
import com.icc.qasker.global.error.ExceptionMessage;
import com.icc.qasker.quiz.GenerationStatus;
import com.icc.qasker.quiz.QuizCommandService;
import com.icc.qasker.quiz.dto.aiResponse.ProblemSetGeneratedEvent.QuizGeneratedFromAI;
import com.icc.qasker.quiz.dto.feRequest.enums.QuizType;
import com.icc.qasker.quiz.dto.feResponse.ProblemSetResponse;
import com.icc.qasker.quiz.dto.feResponse.ProblemSetResponse.QuizForFe;
import com.icc.qasker.quiz.entity.Problem;
import com.icc.qasker.quiz.entity.ProblemSet;
import com.icc.qasker.quiz.mapper.ProblemSetResponseMapper;
import com.icc.qasker.quiz.repository.ProblemRepository;
import com.icc.qasker.quiz.repository.ProblemSetRepository;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Slf4j
@Service
@AllArgsConstructor
public class QuizCommandServiceImpl implements QuizCommandService {

    private final ProblemSetRepository problemSetRepository;
    private final ProblemRepository problemRepository;
    private final ProblemSetResponseMapper problemSetResponseMapper;
    private final HashUtil hashUtil;

    @Transactional(readOnly = true)
    @Override
    public GenerationStatus getGenerationStatus(Long problemSetId) {
        return problemSetRepository.findById(problemSetId)
            .map(ProblemSet::getStatus)
            .orElseThrow(() -> new CustomException(ExceptionMessage.PROBLEM_SET_NOT_FOUND));
    }

    @Transactional(readOnly = true)
    @Override
    public Long getCount(Long id) {
        return problemRepository.countByIdProblemSetId(id);
    }

    @Transactional(readOnly = true)
    @Override
    public GenerationStatus getGenerationStatusBySessionId(String sessionId) {
        return problemSetRepository.findStatusBySessionId(sessionId)
            .orElse(GenerationStatus.NOT_EXIST);
    }

    @Transactional(readOnly = true)
    @Override
    public ProblemSetResponse getMissedProblems(String sessionId, String lastEventId) {
        ProblemSet problemSet = problemSetRepository.findBySessionIdOrderByCreatedAtDesc(sessionId)
            .orElseThrow(() -> new CustomException(ExceptionMessage.PROBLEM_SET_NOT_FOUND));

        List<QuizForFe> quizForFeList;
        Long problemSetId = problemSet.getId();
        if (StringUtils.hasText(lastEventId)) {
            List<Problem> problems = problemRepository.findMissedProblems(problemSetId,
                Integer.valueOf(lastEventId));
            quizForFeList = problems.stream()
                .map(problemSetResponseMapper::fromEntity).toList();
        } else {
            List<Problem> problems = problemRepository.findByIdProblemSetId(problemSetId);
            quizForFeList = problems.stream()
                .map(problemSetResponseMapper::fromEntity).toList();
        }
        return new ProblemSetResponse(
            sessionId,
            hashUtil.encode(problemSetId),
            problemSet.getStatus(),
            problemSet.getQuizType(),
            problemSet.getTotalQuizCount(),
            quizForFeList
        );
    }

    @Transactional
    @Override
    public Long initProblemSet(String userId, String sessionId, Integer totalQuizCount,
        QuizType quizType) {
        ProblemSet problemSet = ProblemSet
            .builder()
            .sessionId(sessionId)
            .userId(userId)
            .totalQuizCount(totalQuizCount)
            .quizType(quizType)
            .build();
        problemSetRepository.save(problemSet);
        return problemSet.getId();
    }

    @Transactional
    @Override
    public void updateStatus(Long problemSetId, GenerationStatus status) {
        ProblemSet problemSet = problemSetRepository.findById(problemSetId)
            .orElseThrow(() -> new CustomException(ExceptionMessage.PROBLEM_SET_NOT_FOUND));
        problemSet.updateStatus(status);
    }

    @Transactional
    @Override
    public List<QuizForFe> saveBatch(List<QuizGeneratedFromAI> generatedProblems,
        Long problemSetId) {
        ProblemSet problemSet = problemSetRepository.findById(problemSetId)
            .orElseThrow(() -> new CustomException(ExceptionMessage.PROBLEM_SET_NOT_FOUND));
        List<Problem> problems = new ArrayList<>();
        for (QuizGeneratedFromAI quiz : generatedProblems) {
            problems.add(Problem.of(quiz, problemSet));
        }
        problemRepository.saveAll(problems);

        List<QuizForFe> quizForFeList = new ArrayList<>();
        for (Problem problem : problems) {
            quizForFeList.add(problemSetResponseMapper.fromEntity(problem));
        }
        return quizForFeList;
    }

    @Transactional
    @Override
    public void delete(Long id) {
        problemSetRepository.deleteById(id);
    }
}
