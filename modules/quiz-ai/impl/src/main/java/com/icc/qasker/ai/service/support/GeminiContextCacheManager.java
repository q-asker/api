package com.icc.qasker.ai.service.support;

import com.google.genai.types.Content;
import com.google.genai.types.Part;
import com.icc.qasker.ai.dto.CacheRef;
import java.time.Duration;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.google.genai.GoogleGenAiChatModel;
import org.springframework.ai.google.genai.cache.CachedContentRequest;
import org.springframework.ai.google.genai.cache.GoogleGenAiCachedContent;

/**
 * Vertex 컨텍스트 캐시(systemInstruction + PDF 프리픽스) 생성·삭제 공통 헬퍼. 생성 캐시(문제 생성 지침)와 Pass 1 검증 캐시(검증 루브릭)가
 * 동일한 절차를 쓰므로 한곳에 모은다. ChatModel이 GoogleGenAiChatModel이 아니거나 생성이 실패하면 empty를 반환해 호출 측이 캐시 없는 폴백으로
 * 강등하게 한다. chatModel만 의존하는 무상태 래퍼라 소유자가 생성자에서 합성한다.
 */
@Slf4j
public final class GeminiContextCacheManager {

  private final ChatModel chatModel;

  public GeminiContextCacheManager(ChatModel chatModel) {
    this.chatModel = chatModel;
  }

  /** ChatModel의 기본 모델. GoogleGenAiChatModel이 아니거나 모델이 비었으면 empty. */
  public Optional<String> defaultModel() {
    if (chatModel instanceof GoogleGenAiChatModel genAiModel) {
      String model = genAiModel.getOptions().getModel();
      if (model != null && !model.isBlank()) {
        return Optional.of(model);
      }
    }
    return Optional.empty();
  }

  /** ChatModel 기본 모델로 컨텍스트 캐시를 생성한다(생성 흐름용). */
  public Optional<CacheRef> create(
      String label, String systemInstruction, String pdfUri, Duration ttl) {
    return defaultModel().flatMap(model -> create(label, model, systemInstruction, pdfUri, ttl));
  }

  /**
   * 지정 모델로 systemInstruction + PDF 프리픽스 컨텍스트 캐시를 생성한다. GoogleGenAiChatModel이 아니거나 model이 비었거나 생성이
   * 실패하면(최소 토큰 미달 등) empty를 반환해 캐시 없는 폴백으로 강등한다.
   *
   * @param label 로그 식별 라벨(예: "MULTIPLE", "Pass 1 검증")
   */
  public Optional<CacheRef> create(
      String label, String model, String systemInstruction, String pdfUri, Duration ttl) {
    if (!(chatModel instanceof GoogleGenAiChatModel genAiModel)) {
      return Optional.empty();
    }
    if (model == null || model.isBlank()) {
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
      log.info("{} 컨텍스트 캐시 생성: name={}, model={}", label, created.getName(), model);
      return Optional.of(new CacheRef(created.getName(), model));
    } catch (Exception e) {
      log.warn("{} 컨텍스트 캐시 생성 실패 — 캐시 없이 진행(프리픽스 매 호출 전송).", label, e);
      return Optional.empty();
    }
  }

  /** 컨텍스트 캐시를 삭제한다. 실패해도 TTL로 만료되므로 경고만 남긴다. */
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
