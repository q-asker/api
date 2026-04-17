package com.icc.qasker.ai.service;

import com.google.genai.types.Content;
import com.google.genai.types.FileData;
import com.google.genai.types.Part;
import com.icc.qasker.ai.prompt.QuizType;
import com.icc.qasker.global.error.CustomException;
import com.icc.qasker.global.error.ExceptionMessage;
import java.time.Duration;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.google.genai.cache.CachedContentRequest;
import org.springframework.ai.google.genai.cache.GoogleGenAiCachedContent;
import org.springframework.ai.google.genai.cache.GoogleGenAiCachedContentService;
import org.springframework.ai.model.google.genai.autoconfigure.chat.GoogleGenAiChatProperties;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class GeminiCacheService {

  public record CacheInfo(String name, long tokenCount) {}

  private static final Duration DEFAULT_TTL = Duration.ofMinutes(5);

  private final GoogleGenAiCachedContentService cachedContentService;
  private final String model;

  public GeminiCacheService(
      GoogleGenAiChatProperties properties, GoogleGenAiCachedContentService cachedContentService) {
    this.model = properties.getOptions().getModel();
    this.cachedContentService = cachedContentService;
  }

  public CacheInfo createCache(String fileUri, String strategyValue, String language) {
    try {
      Content pdfContent =
          Content.builder()
              .role("user")
              .parts(
                  Part.builder()
                      .fileData(
                          FileData.builder().fileUri(fileUri).mimeType("application/pdf").build())
                      .build())
              .build();

      QuizType strategy = QuizType.valueOf(strategyValue);
      String systemPrompt = strategy.getSystemGuideLine(language);
      Content systemInstruction =
          Content.builder().parts(List.of(Part.builder().text(systemPrompt).build())).build();

      CachedContentRequest request =
          CachedContentRequest.builder()
              .model(model)
              .systemInstruction(systemInstruction)
              .contents(List.of(pdfContent))
              .ttl(DEFAULT_TTL)
              .build();

      GoogleGenAiCachedContent cache = cachedContentService.create(request);
      String cacheName = cache.getName();
      long tokenCount =
          cache.getUsageMetadata() != null && cache.getUsageMetadata().totalTokenCount().isPresent()
              ? cache.getUsageMetadata().totalTokenCount().get()
              : 0;

      log.info(
          "캐시 생성 완료: name={}, model={}, ttl={}, expireTime={}, tokenCount={}",
          cacheName,
          cache.getModel(),
          cache.getTtl(),
          cache.getExpireTime(),
          tokenCount);

      return new CacheInfo(cacheName, tokenCount);
    } catch (Exception e) {
      throw new CustomException(
          ExceptionMessage.AI_SERVER_RESPONSE_ERROR, "캐시 생성 실패: fileUri=" + fileUri, e);
    }
  }

  public void deleteCache(String cacheName) {
    if (cacheName == null) {
      return;
    }
    try {
      boolean deleted = cachedContentService.delete(cacheName);
      if (deleted) {
        log.info("캐시 삭제 완료: name={}", cacheName);
      } else {
        log.warn("캐시 삭제 실패: name={}", cacheName);
      }
    } catch (Exception e) {
      log.warn("캐시 삭제 중 오류: name={}, error={}", cacheName, e.getMessage());
    }
  }
}
