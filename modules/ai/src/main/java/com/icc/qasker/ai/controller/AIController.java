package com.icc.qasker.ai.controller;

import com.icc.qasker.ai.dto.ChatRequest;
import com.icc.qasker.ai.dto.MyChatResponse;
import com.icc.qasker.ai.service.ChatService;
import com.icc.qasker.ai.service.GeminiFileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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
    private final GeminiFileService geminiFileService;

    @Operation(summary = "AI와 채팅한다")
    @PostMapping("/chat")
    public ResponseEntity<MyChatResponse> chat(
        @RequestBody
        ChatRequest request
    ) {
        return ResponseEntity.ok(chatService.chat(request.prompt()));
    }

    @Operation(summary = "제미나이 분석을 위한 파일을 업로드한다")
    @PostMapping("/upload")
    public ResponseEntity<?> uploadFile(
        @RequestParam
        String fileUrl
    ) {
        return ResponseEntity.ok(geminiFileService.uploadPdf(fileUrl));
    }
}
