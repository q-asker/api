package com.icc.qasker.ai.support;

import com.google.genai.types.Content;
import com.google.genai.types.Part;
import com.icc.qasker.ai.dto.CacheRef;
import com.icc.qasker.ai.metric.GeminiMetricsRecorder;
import java.time.Duration;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.google.genai.GoogleGenAiChatModel;
import org.springframework.ai.google.genai.cache.CachedContentRequest;
import org.springframework.ai.google.genai.cache.GoogleGenAiCachedContent;

@Slf4j
public final class GeminiContextCacheManager {

  private final ChatModel chatModel;
  private final GeminiMetricsRecorder metricsRecorder;

  public GeminiContextCacheManager(ChatModel chatModel, GeminiMetricsRecorder metricsRecorder) {
    this.chatModel = chatModel;
    this.metricsRecorder = metricsRecorder;
  }

  public Optional<String> defaultModel() {
    if (chatModel instanceof GoogleGenAiChatModel genAiModel) {
      String model = genAiModel.getOptions().getModel();
      if (!model.isBlank()) {
        return Optional.of(model);
      }
    }
    return Optional.empty();
  }

  public Optional<CacheRef> create(
      String label, String systemInstruction, String pdfUri, Duration ttl) {
    return defaultModel().flatMap(model -> create(label, model, systemInstruction, pdfUri, ttl));
  }

  public Optional<CacheRef> create(
      String label, String model, String systemInstruction, String pdfUri, Duration ttl) {
    if (!(chatModel instanceof GoogleGenAiChatModel genAiModel)) {
      metricsRecorder.recordCacheCreate(false);
      return Optional.empty();
    }
    if (model == null || model.isBlank()) {
      metricsRecorder.recordCacheCreate(false);
      return Optional.empty();
    }
    try {
      Content pdf = Content.fromParts(Part.fromUri(pdfUri, "application/pdf"));
      CachedContentRequest request =
          CachedContentRequest.builder()
              .model(model)
              .systemInstruction(systemInstruction)
              .addContent(pdf)
              .ttl(ttl)
              .build();
      GoogleGenAiCachedContent created = genAiModel.getCachedContentService().create(request);
      metricsRecorder.recordCacheCreate(true);
      log.info("{} 컨텍스트 캐시 생성: name={}, model={}", label, created.getName(), model);
      return Optional.of(new CacheRef(created.getName(), model));
    } catch (Exception e) {
      metricsRecorder.recordCacheCreate(false);
      if (isBelowMinimumTokens(e)) {
        // 짧은 문서는 캐시 최소 토큰(4096)에 못 미쳐 거절된다 — 정상 경로라 경보 대상이 아니다
        log.warn("{} 컨텍스트 캐시 생략 — 최소 토큰 미달, 캐시 없이 진행: {}", label, rootMessage(e));
      } else {
        log.error("{} 컨텍스트 캐시 생성 실패 — 캐시 없이 진행(프리픽스 매 호출 전송).", label, e);
      }
      return Optional.empty();
    }
  }

  /** Gemini 가 캐시 내용이 최소 토큰 수에 못 미친다며 거절한 경우인지 원인 사슬에서 찾는다. */
  static boolean isBelowMinimumTokens(Throwable t) {
    for (Throwable c = t; c != null; c = c.getCause()) {
      String m = c.getMessage();
      if (m != null && m.contains("minimum token count")) {
        return true;
      }
    }
    return false;
  }

  private static String rootMessage(Throwable t) {
    Throwable root = t;
    while (root.getCause() != null) {
      root = root.getCause();
    }
    return root.getMessage();
  }

  public void delete(String label, String cacheName) {
    if (cacheName == null || !(chatModel instanceof GoogleGenAiChatModel genAiModel)) {
      return;
    }
    try {
      genAiModel.getCachedContentService().delete(cacheName);
      log.info("{} 컨텍스트 캐시 삭제: {}", label, cacheName);
    } catch (Exception e) {
      log.warn("{} 컨텍스트 캐시 삭제 실패(TTL 만료 대기): {}", label, cacheName, e);
    }
  }
}
