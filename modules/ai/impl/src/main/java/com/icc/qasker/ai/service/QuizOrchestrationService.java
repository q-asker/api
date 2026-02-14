package com.icc.qasker.ai.service;

import com.icc.qasker.ai.dto.GeminiFileUploadResponse.FileMetadata;
import com.icc.qasker.ai.dto.ai.AIProblemSet;
import com.icc.qasker.ai.prompt.quiz.common.QuizPromptStrategy;
import com.icc.qasker.ai.prompt.quiz.user.UserPrompt;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class QuizOrchestrationService {

    private final GeminiFileService geminiFileService;
    private final GeminiCacheService geminiCacheService;
    private final ChatModel chatModel;

    public AIProblemSet generateQuiz(String fileUrl, QuizPromptStrategy strategy,
        int quizCount, List<Integer> referencePages) {

        FileMetadata metadata = geminiFileService.uploadPdf(fileUrl);
        log.info("업로드 완료: name={}, uri={}", metadata.name(), metadata.uri());

        var converter = new BeanOutputConverter<>(AIProblemSet.class);
        String jsonSchema = converter.getJsonSchema();

        String cacheName = null;
        try {
            cacheName = geminiCacheService.createCache(metadata.uri(), strategy, jsonSchema);
            log.info("캐시 생성 완료: cacheName={}", cacheName);

            String userPrompt = UserPrompt.generate(referencePages, quizCount);
            ChatResponse response = chatModel.call(
                new Prompt(userPrompt,
                    GoogleGenAiChatOptions.builder()
                        .useCachedContent(true)
                        .cachedContentName(cacheName)
                        .responseMimeType("application/json")
                        .build())
            );

            String jsonText = response.getResult().getOutput().getText();
            log.info("Gemini 응답 수신 (길이: {}자", jsonText.length());

            return converter.convert(jsonText);
        } finally {
            geminiCacheService.deleteCache(cacheName);
            geminiFileService.deleteFile(metadata.name());
        }
    }
}
