package com.icc.qasker.ai.service;

import com.icc.qasker.ai.GeminiFileService;
import com.icc.qasker.ai.dto.GeminiFileUploadResponse.FileMetadata;
import com.icc.qasker.ai.prompt.quiz.common.QuizType;
import com.icc.qasker.ai.prompt.quiz.system.SystemPrompt;
import com.icc.qasker.ai.prompt.quiz.user.UserPrompt;
import com.icc.qasker.ai.structure.GeminiResponse;
import java.net.URI;
import java.util.List;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.content.Media;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.util.MimeType;

/**
 * Implicit Caching 수동 테스트.
 *
 * <p>실제 Gemini API를 호출하므로 @Disabled 상태로 유지한다. 테스트 시 @Disabled를 제거하고 pdfUrl을 실제 CloudFront URL로
 * 변경한다.
 */
@Disabled("실제 API 호출 — 수동 테스트 시에만 활성화")
@SpringBootTest
class ImplicitCachingTest {

  @Autowired private ChatModel chatModel;
  @Autowired private GeminiFileService geminiFileService;

  // 테스트할 실제 CloudFront URL로 변경
  private static final String PDF_URL = "https://files.q-asker.com/실제파일.pdf";
  private static final String STRATEGY = "MULTIPLE";

  @Test
  @DisplayName("동일 prefix로 3회 호출하여 cachedContentTokenCount 변화를 관찰한다")
  void testImplicitCaching() {
    FileMetadata metadata = geminiFileService.uploadPdf(PDF_URL);
    System.out.println("[Implicit 테스트] 파일 업로드 완료: name=" + metadata.name());

    var converter = new BeanOutputConverter<>(GeminiResponse.class);
    String jsonSchema = converter.getJsonSchema();
    QuizType strategy = QuizType.valueOf(STRATEGY);
    String systemPromptText = SystemPrompt.generate(strategy, jsonSchema);

    try {
      for (int i = 1; i <= 3; i++) {
        UserMessage userMessage =
            UserMessage.builder()
                .text(UserPrompt.generate(List.of(i, i + 1, i + 2), 1))
                .media(
                    List.of(
                        new Media(new MimeType("application", "pdf"), URI.create(metadata.uri()))))
                .build();

        SystemMessage systemMessage = new SystemMessage(systemPromptText);

        Prompt prompt =
            new Prompt(
                List.of(systemMessage, userMessage),
                GoogleGenAiChatOptions.builder().responseMimeType("application/json").build());

        ChatResponse chatResponse = chatModel.call(prompt);

        if (chatResponse.getMetadata() != null && chatResponse.getMetadata().getUsage() != null) {
          var usage = chatResponse.getMetadata().getUsage();
          System.out.printf(
              "[Implicit 캐싱 테스트 %d회차] 입력: %d토큰, 출력: %d토큰, 메타데이터: %s%n",
              i, usage.getPromptTokens(), usage.getCompletionTokens(), chatResponse.getMetadata());
        }
      }
    } finally {
      geminiFileService.deleteFile(metadata.name());
    }
  }
}
