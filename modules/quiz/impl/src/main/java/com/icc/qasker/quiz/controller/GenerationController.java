package com.icc.qasker.quiz.controller;

import com.icc.qasker.global.annotation.UserId;
import com.icc.qasker.quiz.GenerationService;
import com.icc.qasker.quiz.doc.GenerationApiDoc;
import com.icc.qasker.quiz.dto.feRequest.GenerationRequest;
import com.icc.qasker.quiz.dto.feResponse.GenerationSessionResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/generation")
public class GenerationController implements GenerationApiDoc {

    private final GenerationService generationService;

    @Override
    public GenerationSessionResponse postProblemSetId(
        @UserId
        String userId,
        @Valid @RequestBody
        GenerationRequest generationRequest) {
        return generationService.triggerGeneration(userId, generationRequest);
    }
}
