package com.icc.qasker.ai.service;

import com.icc.qasker.ai.dto.GeminiFileUploadResponse.FileMetadata;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class FacadeService {

    private final GeminiFileService geminiFileService;
    private final GeminiCacheService geminiCacheService;
    private final ChatModel chatModel;

    public Object doBusinessLogic(String fileUrl) {
        FileMetadata metadata = geminiFileService.uploadPdf(fileUrl);
        log.info("업로드 완료: name={}, uri={}", metadata.name(), metadata.uri());

        String cacheName = null;

        try {
            cacheName = geminiCacheService.createCache(metadata.uri());
            log.info("캐시 생성 완료: cacheName={}", cacheName);

            ChatResponse response1 = chatModel.call(
                new Prompt("이 문서의 핵심 내용을 3줄로 요약해줘.",
                    GoogleGenAiChatOptions.builder()
                        .useCachedContent(true)
                        .cachedContentName(cacheName)
                        .build())
            );

            String summary = response1.getResult().getOutput().getText();
            log.info("요약 결과: {}", summary);

            ChatResponse response2 = chatModel.call(
                new Prompt("이 문서에서 가장 중요한 개념 하나를 설명해줘.",
                    GoogleGenAiChatOptions.builder()
                        .useCachedContent(true)
                        .cachedContentName(cacheName)
                        .build())
            );

            String concept = response2.getResult().getOutput().getText();
            log.info("개념 설명: {}", concept);

            return ResponseEntity.ok(Map.of(
                    "cacheName", cacheName,
                    "summary", summary,
                    "concept", concept
                )
            );
        } finally {
            geminiCacheService.deleteCache(cacheName);
            geminiFileService.deleteFile(metadata.name());
        }
    }
}
