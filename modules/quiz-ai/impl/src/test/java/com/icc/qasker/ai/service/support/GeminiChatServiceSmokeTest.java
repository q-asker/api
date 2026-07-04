package com.icc.qasker.ai.service.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.icc.qasker.ai.dto.ChunkInfo;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import tools.jackson.databind.ObjectMapper;

/**
 * 버전업 세이프티 넷 (RT-005): Spring AI 1.1.x → 2.0.0 승격이 GeminiChatService.callAndParse()의
 * chatModel.call() → chatResponse.getResult().getOutput().getText() → JSON 파싱 사슬을 유지하는지 확인한다.
 */
class GeminiChatServiceSmokeTest {

  private ChatModel chatModel;
  private ObjectMapper objectMapper;
  private GeminiMetricsRecorder metricsRecorder;
  private GeminiChatService service;

  @BeforeEach
  void setUp() {
    chatModel = mock(ChatModel.class);
    objectMapper = new ObjectMapper();
    metricsRecorder = mock(GeminiMetricsRecorder.class);
    service = new GeminiChatService(chatModel, objectMapper, metricsRecorder);
  }

  @Test
  void callAndParse_returnsParsedResponse_whenChatModelReturnsValidJson() throws Exception {
    String validJson = "{\"questions\":[]}";
    ChatResponse mockResponse =
        new ChatResponse(
            List.of(new Generation(new AssistantMessage(validJson))),
            ChatResponseMetadata.builder().build());
    when(chatModel.call(any(Prompt.class))).thenReturn(mockResponse);

    ChunkInfo chunk = new ChunkInfo(List.of(1, 2), 5);
    GeminiChatService.ParsedResult result =
        service.callAndParse(chunk, "cache-x", "MULTIPLE", "ko", null);

    assertThat(result).isNotNull();
    assertThat(result.response()).isNotNull();
    assertThat(result.response().questions()).isEmpty();
    verify(chatModel).call(any(Prompt.class));
  }

  @Test
  void callAndParse_returnsNullResponse_whenChatModelReturnsBlankText() throws Exception {
    ChatResponse mockResponse =
        new ChatResponse(
            List.of(new Generation(new AssistantMessage(""))),
            ChatResponseMetadata.builder().build());
    when(chatModel.call(any(Prompt.class))).thenReturn(mockResponse);

    ChunkInfo chunk = new ChunkInfo(List.of(1), 3);
    GeminiChatService.ParsedResult result =
        service.callAndParse(chunk, "cache-x", "OX", "ko", null);

    assertThat(result.response()).isNull();
  }
}
