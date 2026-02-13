package com.icc.qasker.ai.controller;

import com.icc.qasker.ai.dto.ChatRequest;
import com.icc.qasker.ai.dto.MyChatResponse;
import com.icc.qasker.ai.dto.ai.AIProblemSet;
import com.icc.qasker.ai.prompt.quiz.common.QuizType;
import com.icc.qasker.ai.service.ChatService;
import com.icc.qasker.ai.service.FacadeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "AI", description = "AI 관련 API")
@RestController
@RequestMapping("/ai")
@RequiredArgsConstructor
public class AIController {

    private final ChatService chatService;
    private final FacadeService facadeService;

    @Operation(summary = "AI와 채팅한다")
    @PostMapping("/chat")
    public ResponseEntity<MyChatResponse> chat(
        @RequestBody
        ChatRequest request
    ) {
        return ResponseEntity.ok(chatService.chat(request.prompt()));
    }

    @Operation(summary = "PDF로 퀴즈를 생성한다 (Structured Output 테스트)")
    @PostMapping("/test-quiz")
    public ResponseEntity<AIProblemSet> testQuiz(
        @RequestParam String fileUrl,
        @RequestParam QuizType quizType,
        @RequestParam int quizCount,
        @RequestParam List<Integer> pageNumbers
    ) {
        return ResponseEntity.ok(
            facadeService.generateQuiz(fileUrl, quizType, quizCount, pageNumbers)
        );
    }
}
