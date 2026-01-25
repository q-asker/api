package com.icc.qasker.quiz.service;

import com.icc.qasker.global.component.SlackNotifier;
import com.icc.qasker.global.error.CustomException;
import com.icc.qasker.global.error.ExceptionMessage;
import com.icc.qasker.global.util.HashUtil;
import com.icc.qasker.quiz.GenerationService;
import com.icc.qasker.quiz.adapter.AiServerAdapter;
import com.icc.qasker.quiz.dto.aiResponse.QuizGeneratedFromAI;
import com.icc.qasker.quiz.dto.feRequest.GenerationRequest;
import com.icc.qasker.quiz.dto.feResponse.ProblemSetResponse;
import com.icc.qasker.quiz.dto.feResponse.ProblemSetResponse.QuizForFe;
import com.icc.qasker.quiz.entity.Problem;
import com.icc.qasker.quiz.entity.ProblemSet;
import com.icc.qasker.quiz.mapper.ProblemSetResponseMapper;
import com.icc.qasker.quiz.repository.ProblemRepository;
import com.icc.qasker.quiz.repository.ProblemSetRepository;
import com.icc.qasker.util.S3Service;
import com.icc.qasker.util.S3ValidateService;
import com.icc.qasker.util.dto.FileExistStatusResponse;
import com.icc.qasker.util.dto.Status;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

@Service
@AllArgsConstructor
public class GenerationServiceImpl implements GenerationService {

    private final ProblemSetResponseMapper problemSetResponseMapper;
    private final SlackNotifier slackNotifier;
    private final AiServerAdapter aiServerAdapter;
    private final ProblemSetRepository problemSetRepository;
    private final HashUtil hashUtil;
    private final S3ValidateService s3ValidateService;
    private final S3Service s3Service;
    private final ProblemRepository problemRepository;

    @Override
    public Flux<ProblemSetResponse> processGenerationRequest(
        GenerationRequest request, String userId) {
        validateFile(request.uploadedUrl());

        ProblemSet problemSet = ProblemSet.builder().userId(userId).build();
        ProblemSet save = problemSetRepository.save(problemSet);
        String id = hashUtil.encode(save.getId());
        Scheduler scheduler = Schedulers.fromExecutor(Executors.newVirtualThreadPerTaskExecutor());

        return aiServerAdapter.requestGenerate(request)
            .publishOn(scheduler)
            .map(aiDto -> {
                List<QuizForFe> quizForFeList = new ArrayList<>();
                for (QuizGeneratedFromAI quizGeneratedFromAI : aiDto.getQuiz()) {
                    Problem problem = Problem.of(quizGeneratedFromAI, problemSet);
                    problemRepository.save(problem);
                    quizForFeList.add(problemSetResponseMapper.fromEntity(problem));
                }
                return new ProblemSetResponse(
                    id,
                    request.quizCount(),
                    quizForFeList
                );
            });
    }

    private void validateFile(String uploadUrl) {
        FileExistStatusResponse fileExistStatusResponse = s3Service.checkFileExistence(uploadUrl);
        if (fileExistStatusResponse.status().equals(Status.NOT_EXIST)) {
            throw new CustomException(ExceptionMessage.FILE_NOT_FOUND_ON_S3);
        }
        s3ValidateService.checkCloudFrontUrlWithThrowing(uploadUrl);
    }
}