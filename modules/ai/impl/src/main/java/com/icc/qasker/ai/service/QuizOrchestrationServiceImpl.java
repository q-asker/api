package com.icc.qasker.ai.service;

import com.icc.qasker.ai.GeminiCacheService;
import com.icc.qasker.ai.GeminiFileService;
import com.icc.qasker.ai.QuizOrchestrationService;
import com.icc.qasker.ai.dto.AIProblemSet;
import com.icc.qasker.ai.dto.ChunkInfo;
import com.icc.qasker.ai.dto.GeminiFileUploadResponse.FileMetadata;
import com.icc.qasker.ai.mapper.GeminiProblemSetMapper;
import com.icc.qasker.ai.prompt.quiz.user.UserPrompt;
import com.icc.qasker.ai.structure.GeminiProblem;
import com.icc.qasker.ai.structure.GeminiProblemSet;
import com.icc.qasker.ai.util.ChunkSplitter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
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
public class QuizOrchestrationServiceImpl implements QuizOrchestrationService {

    private static final int MAX_CHUNK_COUNT = 10;
    private static final int MAX_SELECTION_COUNT = 4;

    private final GeminiFileService geminiFileService;
    private final GeminiCacheService geminiCacheService;
    private final ChatModel chatModel;

    @Override
    public void generateQuiz(
        String fileUrl,
        String strategyValue,
        int quizCount,
        List<Integer> referencePages,
        Consumer<AIProblemSet> onChunkCompleted
    ) {
        FileMetadata metadata = geminiFileService.uploadPdf(fileUrl);
        log.info("업로드 완료: name={}, uri={}", metadata.name(), metadata.uri());

        var converter = new BeanOutputConverter<>(GeminiProblemSet.class);
        String jsonSchema = converter.getJsonSchema();

        String cacheName = geminiCacheService.createCache(metadata.uri(), strategyValue,
            jsonSchema);
        try {
            log.info("캐시 생성 완료: cacheName={}", cacheName);

            List<ChunkInfo> chunks = ChunkSplitter.createPageChunks(
                referencePages, quizCount, MAX_CHUNK_COUNT
            );
            log.info("청크 분할 완료: {}개 청크", chunks.size());

            AtomicInteger numberCounter = new AtomicInteger(1);

            try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
                List<CompletableFuture<Void>> futures = new ArrayList<>(chunks.size());

                for (ChunkInfo chunk : chunks) {
                    CompletableFuture<Void> future = CompletableFuture.runAsync(
                        () -> processChunk(
                            chunk, cacheName, converter, strategyValue, numberCounter,
                            onChunkCompleted
                        ),
                        executor
                    );
                    futures.add(future);
                }

                CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).join();
            }
            log.info("전체 병렬 생성 완료: 총 {}번까지 번호 할당됨", numberCounter.get() - 1);
        } finally {
            geminiCacheService.deleteCache(cacheName);
            geminiFileService.deleteFile(metadata.name());
        }
    }

    private void processChunk(
        ChunkInfo chunk,
        String cacheName,
        BeanOutputConverter<GeminiProblemSet> converter,
        String strategyValue,
        AtomicInteger numberCounter,
        Consumer<AIProblemSet> onChunkCompleted
    ) {
        try {
            log.debug("청크 처리 시작: pages={}, quizCount={}",
                chunk.referencedPages(), chunk.quizCount());

            String userPrompt = UserPrompt.generate(
                chunk.referencedPages(), chunk.quizCount()
            );

            ChatResponse response = chatModel.call(
                new Prompt(userPrompt,
                    GoogleGenAiChatOptions.builder()
                        .useCachedContent(true)
                        .cachedContentName(cacheName)
                        .responseMimeType("application/json")
                        .build())
            );

            String jsonText = response.getResult().getOutput().getText();
            log.debug("청크 응답 수신 (길이: {}자)", jsonText.length());

            GeminiProblemSet geminiResult = converter.convert(jsonText);

            if (geminiResult == null || geminiResult.quiz() == null
                || geminiResult.quiz().isEmpty()) {
                log.warn("청크 결과 비어있음: pages={}", chunk.referencedPages());
                return;
            }

            GeminiProblem firstProblem = geminiResult.quiz().getFirst();
            if (firstProblem.selections() != null
                && firstProblem.selections().size() > MAX_SELECTION_COUNT) {
                log.warn("선택지 초과로 청크 폐기: {}개 선택지, pages={}",
                    firstProblem.selections().size(), chunk.referencedPages());
                return;
            }

            AIProblemSet result = GeminiProblemSetMapper.toDto(
                geminiResult, strategyValue, chunk.referencedPages(), numberCounter
            );

            onChunkCompleted.accept(result);

            log.debug("청크 처리 완료: pages={}, 문제 {}개",
                chunk.referencedPages(), result.quiz().size());
        } catch (Exception e) {
            log.error("청크 처리 실패 (계속 진행): pages={}, error={}",
                chunk.referencedPages(), e.getMessage());
        }
    }
}
