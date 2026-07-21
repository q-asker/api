package com.icc.qasker.ai.service.essay;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.icc.qasker.ai.GeminiFileService;
import com.icc.qasker.ai.QuizBatchSink;
import com.icc.qasker.ai.dto.AIProblem;
import com.icc.qasker.ai.dto.AISelection;
import com.icc.qasker.ai.dto.GeminiFileUploadResponse.FileMetadata;
import com.icc.qasker.ai.dto.GenerationRequestToAI;
import com.icc.qasker.ai.dto.QualityVerdict;
import com.icc.qasker.ai.exception.GeminiInfraException;
import com.icc.qasker.ai.properties.QAskerAiProperties;
import com.icc.qasker.ai.service.quality.QualityGate;
import com.icc.qasker.ai.service.support.GeminiMetricsRecorder;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
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
 * 서술형(ESSAY) 오케스트레이터가 청크형 골격 위에서 동작하는 계약 회귀 테스트.
 *
 * <p>선지형과 동일한 실행모델(청크 분할·비동기 검증)을 공유하므로 전달 계약(개수·초과 truncate·부분 보존·전량 실패 전파)이 동일하게 성립하는지, 그리고 서술형
 * 고유 규약(선지 drop 없음 + modelAnswer→단일 선지 데이터 계약)이 보존되는지 검증한다.
 */
class EssayChunkedContractTest {

  private GeminiFileService fileService;
  private ChatModel chatModel;
  private ObjectMapper objectMapper;
  private GeminiMetricsRecorder metricsRecorder;
  private QAskerAiProperties aiProperties;
  private QualityGate qualityGate;

  @BeforeEach
  void setUp() {
    fileService = mock(GeminiFileService.class);
    chatModel = mock(ChatModel.class);
    objectMapper = new ObjectMapper();
    metricsRecorder = mock(GeminiMetricsRecorder.class);
    aiProperties = new QAskerAiProperties();
    aiProperties.getChunk().setChunkSize(15);
    qualityGate = mock(QualityGate.class);
    when(qualityGate.verify(any(), any(), any(), any(), any())).thenReturn(QualityVerdict.pass());

    FileMetadata meta =
        new FileMetadata(null, null, null, null, null, null, null, "gs://b/x.pdf", null);
    when(fileService.generateCacheKey(any(), any())).thenReturn("k");
    when(fileService.awaitCachedFileMetadata("k")).thenReturn(Optional.of(meta));
    when(metricsRecorder.recordChunkResult(anyLong(), any())).thenReturn(0.0);
  }

  /** 저장을 기록하는 테스트용 sink. 비동기 검증 워커들이 동시에 호출하므로 스레드 안전하게 직렬화한다. */
  private static class FakeSink implements QuizBatchSink {
    final List<AIProblem> saved = new ArrayList<>();
    final AtomicInteger counter = new AtomicInteger(1);

    @Override
    public synchronized int saveProblem(AIProblem problem) {
      saved.add(problem);
      return counter.getAndIncrement();
    }

    List<String> contents() {
      return saved.stream().map(AIProblem::content).toList();
    }
  }

  private EssayQuizOrchestrator orchestrator() {
    return new EssayQuizOrchestrator(
        fileService, chatModel, objectMapper, metricsRecorder, aiProperties, qualityGate);
  }

  private GenerationRequestToAI request(int quizCount, QuizBatchSink sink) {
    return GenerationRequestToAI.builder()
        .fileUrl("http://f/x.pdf")
        .strategyValue("ESSAY")
        .language("KO")
        .quizCount(quizCount)
        .referencePages(List.of(1, 2))
        .sink(sink)
        .build();
  }

  /** modelAnswer·explanation을 가진 서술형 문항 JSON(선지 없음). */
  private String essayQuestion(int idx) {
    return "{\"content\":\"Q"
        + idx
        + "\",\"bloomsLevel\":\"Analyze\",\"referencedPages\":[1],\"modelAnswer\":\"A"
        + idx
        + "\",\"explanation\":\"E"
        + idx
        + "\"}";
  }

  private String essayJson(int count) {
    String body =
        IntStream.range(0, count).mapToObj(this::essayQuestion).collect(Collectors.joining(","));
    return "{\"questions\":[" + body + "]}";
  }

  private ChatResponse resp(String text) {
    return new ChatResponse(
        List.of(new Generation(new AssistantMessage(text))),
        ChatResponseMetadata.builder().build());
  }

  private Flux<ChatResponse> fluxOf(String json) {
    return Flux.just(resp(json));
  }

  @Test
  void deliversExactlyQuizCount() {
    FakeSink sink = new FakeSink();
    when(chatModel.stream(any(Prompt.class))).thenReturn(fluxOf(essayJson(3)));

    orchestrator().generateQuiz(request(3, sink));

    assertThat(sink.contents()).containsExactlyInAnyOrder("Q0", "Q1", "Q2");
  }

  @Test
  void truncatesWhenChunkOverProduces() {
    FakeSink sink = new FakeSink();
    when(chatModel.stream(any(Prompt.class))).thenReturn(fluxOf(essayJson(4)));

    orchestrator().generateQuiz(request(2, sink));

    assertThat(sink.contents()).containsExactlyInAnyOrder("Q0", "Q1");
  }

  @Test
  void preservesEarlierChunk_whenLaterChunkFails() {
    aiProperties.getChunk().setChunkSize(1); // quizCount=2 → 청크 2개
    FakeSink sink = new FakeSink();
    when(chatModel.stream(any(Prompt.class)))
        .thenReturn(fluxOf(essayJson(1)), Flux.error(new RuntimeException("boom")));

    orchestrator().generateQuiz(request(2, sink));

    assertThat(sink.contents()).containsExactly("Q0");
  }

  @Test
  void rethrows_whenNothingDelivered() {
    FakeSink sink = new FakeSink();
    when(chatModel.stream(any(Prompt.class))).thenReturn(Flux.error(new RuntimeException("boom")));

    assertThatThrownBy(() -> orchestrator().generateQuiz(request(1, sink)))
        .isInstanceOf(GeminiInfraException.class);
    assertThat(sink.saved).isEmpty();
  }

  /**
   * 서술형 데이터 계약: modelAnswer→단일 선지(content), explanation→선지.explanation, correct=true. 하류 채점이 이 규약에
   * 의존한다.
   */
  @Test
  void preservesEssayDataContract() {
    FakeSink sink = new FakeSink();
    when(chatModel.stream(any(Prompt.class))).thenReturn(fluxOf(essayJson(1)));

    orchestrator().generateQuiz(request(1, sink));

    assertThat(sink.saved).hasSize(1);
    List<AISelection> selections = sink.saved.getFirst().selections();
    assertThat(selections).hasSize(1);
    AISelection modelAnswer = selections.getFirst();
    assertThat(modelAnswer.content()).isEqualTo("A0");
    assertThat(modelAnswer.explanation()).isEqualTo("E0");
    assertThat(modelAnswer.correct()).isTrue();
  }

  /** 미달 문항은 보류됐다가 chatModel.call 재생성본(v2)이 검증 없이 저장된다. */
  @Test
  void regeneratesHeldQuestion() {
    FakeSink sink = new FakeSink();
    when(chatModel.stream(any(Prompt.class))).thenReturn(fluxOf(essayJson(1))); // Q0 → 미달 보류
    when(chatModel.call(any(Prompt.class))).thenReturn(resp(essayJson(1))); // 재생성 Q0(v2)
    when(qualityGate.verify(any(), any(), any(), any(), any()))
        .thenReturn(QualityVerdict.below("모범답안 근거 불명확"));

    orchestrator().generateQuiz(request(1, sink));

    // v1은 저장 안 되고 재생성 v2만 저장(검증 없이).
    assertThat(sink.contents()).containsExactly("Q0");
  }
}
