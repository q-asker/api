package com.icc.qasker.ai.service.quality;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.icc.qasker.ai.GeminiFileService;
import com.icc.qasker.ai.QuizBatchSink;
import com.icc.qasker.ai.dto.AIExplanation;
import com.icc.qasker.ai.dto.AIProblem;
import com.icc.qasker.ai.dto.GeminiFileUploadResponse.FileMetadata;
import com.icc.qasker.ai.dto.GenerationRequestToAI;
import com.icc.qasker.ai.dto.QualityVerdict;
import com.icc.qasker.ai.properties.QAskerAiProperties;
import com.icc.qasker.ai.service.multiple.MultipleQuizOrchestrator;
import com.icc.qasker.ai.service.support.GeminiMetricsRecorder;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Flux;
import tools.jackson.databind.ObjectMapper;

/**
 * 생성 게이트(T014/T015) 통합 동작 검증. mock 검증으로 특정 문항을 미달 처리했을 때, 미달본(v1)이 저장(SSE 발행)되지 않고 재생성 v2가 저장되며,
 * delivered가 통과분+v2만 카운트하고, 재생성 불가 시 제외됨을 확인한다(quickstart S1/S3).
 */
class QualityGateOrchestratorTest {

  private GeminiFileService fileService;
  private ChatModel chatModel;
  private GeminiMetricsRecorder metricsRecorder;
  private QAskerAiProperties aiProperties;
  private QualityGate qualityGate;

  @BeforeEach
  void setUp() {
    fileService = mock(GeminiFileService.class);
    chatModel = mock(ChatModel.class);
    metricsRecorder = mock(GeminiMetricsRecorder.class);
    aiProperties = new QAskerAiProperties();
    qualityGate = mock(QualityGate.class);

    FileMetadata meta =
        new FileMetadata(null, null, null, null, null, null, null, "gs://b/x.pdf", null);
    when(fileService.generateCacheKey(any(), any())).thenReturn("k");
    when(fileService.awaitCachedFileMetadata("k")).thenReturn(Optional.of(meta));
  }

  private MultipleQuizOrchestrator orchestrator() {
    return new MultipleQuizOrchestrator(
        fileService, chatModel, new ObjectMapper(), metricsRecorder, aiProperties, qualityGate);
  }

  private GenerationRequestToAI request(int quizCount, QuizBatchSink sink) {
    return GenerationRequestToAI.builder()
        .fileUrl("http://f/x.pdf")
        .strategyValue("MULTIPLE")
        .language("KO")
        .quizCount(quizCount)
        .referencePages(List.of(1))
        .sink(sink)
        .build();
  }

  private ChatResponse resp(String text) {
    return new ChatResponse(
        List.of(new Generation(new AssistantMessage(text))),
        ChatResponseMetadata.builder().build());
  }

  private String question(String content) {
    return "{\"content\":\""
        + content
        + "\",\"bloomsLevel\":\"Remember\",\"referencedPages\":[1],"
        + "\"selections\":[{\"content\":\"s0\",\"correct\":true},{\"content\":\"s1\",\"correct\":false}]}";
  }

  private String questionsJson(String... contents) {
    List<String> qs = new ArrayList<>();
    for (String c : contents) {
      qs.add(question(c));
    }
    return "{\"questions\":[" + String.join(",", qs) + "]}";
  }

  @Test
  @DisplayName("미달 문항(v1)은 미저장·미노출, 재생성 v2가 저장되고 delivered는 통과분+v2만 카운트한다")
  void belowThresholdHeldAndRegenerated() {
    FakeSink sink = new FakeSink();
    // 스트림: Q0(통과), Q1(미달→보류), Q2(통과)
    when(chatModel.stream(any(Prompt.class)))
        .thenReturn(Flux.just(resp(questionsJson("Q0", "Q1", "Q2"))));
    // 재생성 호출(blocking): Q1의 개선본 Q1v2 산출
    when(chatModel.call(any(Prompt.class))).thenReturn(resp(questionsJson("Q1v2")));

    // Q1(v1)만 미달, 나머지(및 Q1v2)는 통과
    when(qualityGate.verify(any(AIProblem.class), any(), any(), any()))
        .thenAnswer(
            inv -> {
              AIProblem p = inv.getArgument(0);
              return "Q1".equals(p.content())
                  ? QualityVerdict.below("정답 근거 불명확")
                  : QualityVerdict.pass();
            });

    orchestrator().generateQuiz(request(3, sink));

    // v1 Q1은 저장되지 않고, 통과분(Q0,Q2) + 재생성본(Q1v2)만 저장
    assertThat(sink.contents()).containsExactly("Q0", "Q2", "Q1v2");
    assertThat(sink.contents()).doesNotContain("Q1");
  }

  @Test
  @DisplayName("재생성 불가 시 미달 문항은 제외되어 문항 수가 줄어든다")
  void regenerationImpossibleExcludes() {
    FakeSink sink = new FakeSink();
    when(chatModel.stream(any(Prompt.class)))
        .thenReturn(Flux.just(resp(questionsJson("Q0", "Q1", "Q2"))));
    // 재생성 응답이 비어 있음 → 재생성 불가
    when(chatModel.call(any(Prompt.class))).thenReturn(resp("{\"questions\":[]}"));

    when(qualityGate.verify(any(AIProblem.class), any(), any(), any()))
        .thenAnswer(
            inv ->
                "Q1".equals(((AIProblem) inv.getArgument(0)).content())
                    ? QualityVerdict.below("미달")
                    : QualityVerdict.pass());

    orchestrator().generateQuiz(request(3, sink));

    assertThat(sink.contents()).containsExactly("Q0", "Q2");
  }

  /** 저장 순서를 기록하는 테스트용 sink. */
  private static class FakeSink implements QuizBatchSink {
    final List<AIProblem> saved = new ArrayList<>();
    final AtomicInteger counter = new AtomicInteger(1);

    @Override
    public int saveProblem(AIProblem problem) {
      saved.add(problem);
      return counter.getAndIncrement();
    }

    @Override
    public void markProblemsReady() {}

    @Override
    public void saveExplanation(AIExplanation explanation) {}

    List<String> contents() {
      return saved.stream().map(AIProblem::content).toList();
    }
  }
}
