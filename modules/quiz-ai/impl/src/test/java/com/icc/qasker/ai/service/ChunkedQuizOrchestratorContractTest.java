package com.icc.qasker.ai.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.icc.qasker.ai.GeminiFileService;
import com.icc.qasker.ai.QuizBatchSink;
import com.icc.qasker.ai.dto.AIProblem;
import com.icc.qasker.ai.dto.GeminiFileUploadResponse.FileMetadata;
import com.icc.qasker.ai.dto.GenerationRequestToAI;
import com.icc.qasker.ai.dto.QualityVerdict;
import com.icc.qasker.ai.exception.GeminiInfraException;
import com.icc.qasker.ai.properties.QAskerAiProperties;
import com.icc.qasker.ai.service.blank.BlankQuizOrchestrator;
import com.icc.qasker.ai.service.multiple.MultipleQuizOrchestrator;
import com.icc.qasker.ai.service.ox.OXQuizOrchestrator;
import com.icc.qasker.ai.service.quality.QualityGate;
import com.icc.qasker.ai.service.support.GeminiMetricsRecorder;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Flux;
import tools.jackson.databind.ObjectMapper;

/**
 * 청크형 오케스트레이터(MULTIPLE/BLANK/OX) 공통 동작 회귀 테스트 — 배치 인터리빙 1-패스 생성.
 *
 * <p>문제+해설을 한 호출({@code chatModel.stream})로 스트리밍 생성한다. 본 테스트는 전달 계약(개수·순서·초과 drop·부분 보존·전량 실패 전파)과
 * 중복 회피 지침의 배치별 주입을 검증한다.
 */
class ChunkedQuizOrchestratorContractTest {

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
    // 게이트는 이 계약 테스트 범위 밖 — 전량 통과로 스텁해 Phase 1 전달 계약만 검증한다.
    qualityGate = mock(QualityGate.class);
    when(qualityGate.verify(any(), any(), any(), any(), any())).thenReturn(QualityVerdict.pass());

    FileMetadata meta =
        new FileMetadata(null, null, null, null, null, null, null, "gs://b/x.pdf", null);
    when(fileService.generateCacheKey(any(), any())).thenReturn("k");
    when(fileService.awaitCachedFileMetadata("k")).thenReturn(Optional.of(meta));
    when(metricsRecorder.recordChunkResult(anyLong(), any())).thenReturn(0.0);
  }

  /** Phase 1 저장을 순서대로 기록하는 테스트용 sink. */
  private static class FakeSink implements QuizBatchSink {
    final List<AIProblem> saved = new ArrayList<>();
    final AtomicInteger counter = new AtomicInteger(1);
    boolean readyMarked = false;

    @Override
    public int saveProblem(AIProblem problem) {
      saved.add(problem);
      return counter.getAndIncrement();
    }

    @Override
    public void markProblemsReady() {
      readyMarked = true;
    }

    List<String> contents() {
      return saved.stream().map(AIProblem::content).toList();
    }
  }

  private QuizTypeOrchestrator orchestrator(String type) {
    return switch (type) {
      case "MULTIPLE" ->
          new MultipleQuizOrchestrator(
              fileService, chatModel, objectMapper, metricsRecorder, aiProperties, qualityGate);
      case "BLANK" ->
          new BlankQuizOrchestrator(
              fileService, chatModel, objectMapper, metricsRecorder, aiProperties, qualityGate);
      case "OX" ->
          new OXQuizOrchestrator(
              fileService, chatModel, objectMapper, metricsRecorder, aiProperties, qualityGate);
      default -> throw new IllegalArgumentException(type);
    };
  }

  private int maxSelection(String type) {
    return "OX".equals(type) ? 2 : 4;
  }

  private GenerationRequestToAI request(String type, int quizCount, QuizBatchSink sink) {
    return GenerationRequestToAI.builder()
        .fileUrl("http://f/x.pdf")
        .strategyValue(type)
        .language("KO")
        .quizCount(quizCount)
        .referencePages(List.of(1, 2))
        .sink(sink)
        .build();
  }

  /** selCount개 선택지를 가진 문항 JSON. 첫 선택지가 정답. */
  private String question(int idx, int selCount) {
    String selections =
        IntStream.range(0, selCount)
            .mapToObj(s -> "{\"content\":\"s" + s + "\",\"correct\":" + (s == 0) + "}")
            .collect(Collectors.joining(","));
    return "{\"content\":\"Q"
        + idx
        + "\",\"bloomsLevel\":\"Remember\",\"referencedPages\":[1],\"selections\":["
        + selections
        + "]}";
  }

  private String questionsJson(List<Integer> selCounts) {
    String body =
        IntStream.range(0, selCounts.size())
            .mapToObj(i -> question(i, selCounts.get(i)))
            .collect(Collectors.joining(","));
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

  @ParameterizedTest
  @ValueSource(strings = {"MULTIPLE", "BLANK", "OX"})
  void deliversExactlyQuizCount_inOrder(String type) {
    FakeSink sink = new FakeSink();
    when(chatModel.stream(any(Prompt.class))).thenReturn(fluxOf(questionsJson(List.of(1, 1, 1))));

    orchestrator(type).generateQuiz(request(type, 3, sink));

    assertThat(sink.contents()).containsExactly("Q0", "Q1", "Q2");
    assertThat(sink.readyMarked).isTrue();
  }

  @ParameterizedTest
  @ValueSource(strings = {"MULTIPLE", "BLANK", "OX"})
  void truncatesWhenChunkOverProduces(String type) {
    FakeSink sink = new FakeSink();
    when(chatModel.stream(any(Prompt.class)))
        .thenReturn(fluxOf(questionsJson(List.of(1, 1, 1, 1))));

    // quizCount=2 이지만 4문항 방출 → 2개로 잘림
    orchestrator(type).generateQuiz(request(type, 2, sink));

    assertThat(sink.contents()).containsExactly("Q0", "Q1");
  }

  @ParameterizedTest
  @ValueSource(strings = {"MULTIPLE", "BLANK", "OX"})
  void dropsQuestionsExceedingMaxSelectionCount(String type) {
    FakeSink sink = new FakeSink();
    int oversize = maxSelection(type) + 1;
    // idx0은 초과 선택지 → drop. idx1, idx2만 전달.
    when(chatModel.stream(any(Prompt.class)))
        .thenReturn(fluxOf(questionsJson(List.of(oversize, 1, 1))));

    orchestrator(type).generateQuiz(request(type, 3, sink));

    assertThat(sink.contents()).containsExactly("Q1", "Q2");
  }

  @ParameterizedTest
  @ValueSource(strings = {"MULTIPLE", "BLANK", "OX"})
  void preservesEarlierChunk_whenLaterChunkFails(String type) {
    aiProperties.getChunk().setChunkSize(1); // quizCount=2 → 청크 2개
    FakeSink sink = new FakeSink();
    when(chatModel.stream(any(Prompt.class)))
        .thenReturn(fluxOf(questionsJson(List.of(1))), Flux.error(new RuntimeException("boom")));

    int chunksDone = orchestrator(type).generateQuiz(request(type, 2, sink));

    assertThat(sink.contents()).containsExactly("Q0");
    assertThat(chunksDone).isEqualTo(1);
  }

  @ParameterizedTest
  @ValueSource(strings = {"MULTIPLE", "BLANK", "OX"})
  void rethrows_whenNothingDelivered(String type) {
    FakeSink sink = new FakeSink();
    when(chatModel.stream(any(Prompt.class))).thenReturn(Flux.error(new RuntimeException("boom")));

    assertThatThrownBy(() -> orchestrator(type).generateQuiz(request(type, 1, sink)))
        .isInstanceOf(GeminiInfraException.class);
    assertThat(sink.saved).isEmpty();
  }

  @ParameterizedTest
  @ValueSource(strings = {"MULTIPLE", "BLANK", "OX"})
  void injectsDedupInstructionOnSecondChunkUserPromptOnly(String type) {
    aiProperties.getChunk().setChunkSize(1); // quizCount=2 → 청크 2개
    FakeSink sink = new FakeSink();
    when(chatModel.stream(any(Prompt.class)))
        .thenReturn(fluxOf(questionsJson(List.of(1))), fluxOf(questionsJson(List.of(1))));

    orchestrator(type).generateQuiz(request(type, 2, sink));

    // Phase 1(문제)·Phase 2(해설) 모두 stream을 사용하므로 청크당 2회씩 호출된다. 각 요청의 마지막 메시지 = 사용자 프롬프트.
    ArgumentCaptor<Prompt> captor = ArgumentCaptor.forClass(Prompt.class);
    verify(chatModel, atLeast(2)).stream(captor.capture());
    List<String> userTexts =
        captor.getAllValues().stream().map(p -> p.getInstructions().getLast().getText()).toList();

    // 첫 호출(청크0 Phase 1)엔 dedup 지침 없음, 이후 청크1 Phase 1엔 있음
    assertThat(userTexts.get(0)).doesNotContain(typeSpecificDedupMarker(type));
    assertThat(userTexts).anyMatch(t -> t.contains(typeSpecificDedupMarker(type)));
  }

  private String typeSpecificDedupMarker(String type) {
    return switch (type) {
      case "MULTIPLE" -> "다른 패턴 라벨";
      case "BLANK" -> "빈칸 핵심 어휘";
      case "OX" -> "O/X 분포";
      default -> throw new IllegalArgumentException(type);
    };
  }
}
