package com.icc.qasker.quiz.service;

import com.icc.qasker.global.component.HashUtil;
import com.icc.qasker.quiz.GenerationService;
import com.icc.qasker.quiz.adapter.AIServerAdapter;
import com.icc.qasker.quiz.dto.feRequest.GenerationRequest;
import com.icc.qasker.quiz.dto.feResponse.GenerationSessionResponse;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Slf4j
@Service
@AllArgsConstructor
public class GenerationServiceImpl implements GenerationService {

    private final HashUtil hashUtil;
    private final AIServerAdapter aiServerAdapter;
    private final Map<Long, SseEmitter> emittersMap = new ConcurrentHashMap<>();
    private final QuizCommandService quizCommandService;

    public GenerationSessionResponse triggerGeneration(
        String useId,
        GenerationRequest request
    ) {
        Long problemSetId = quizCommandService.initProblemSet(useId);
        return new GenerationSessionResponse(hashUtil.encode(problemSetId));
    }
}
