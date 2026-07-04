package com.icc.qasker.ai.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.icc.qasker.ai.GeminiFileService;
import com.icc.qasker.ai.dto.AIProblemSet;
import com.icc.qasker.ai.dto.GeminiFileUploadResponse.FileMetadata;
import com.icc.qasker.ai.dto.GenerationRequestToAI;
import com.icc.qasker.ai.exception.GeminiInfraException;
import com.icc.qasker.ai.properties.QAskerAiProperties;
import com.icc.qasker.ai.service.blank.BlankQuizOrchestrator;
import com.icc.qasker.ai.service.multiple.MultipleQuizOrchestrator;
import com.icc.qasker.ai.service.ox.OXQuizOrchestrator;
import com.icc.qasker.ai.service.support.GeminiMetricsRecorder;
import com.icc.qasker.ai.strategy.QuizType;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
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
 * 청크형 오케스트레이터(MULTIPLE/BLANK/OX) 공통 동작 회귀 테스트 (문서 검증 테스트 1~3).
 *
 * <p>제안 1(템플릿 메서드 베이스) 리팩터링 전후로 동작이 보존됨을 보장한다.
 */
class ChunkedQuizOrchestratorContractTest {

  private GeminiFileService fileService;
  private ChatModel chatModel;
  private ObjectMapper objectMapper;
  private GeminiMetricsRecorder metricsRecorder;
  private QAskerAiProperties aiProperties;

  @BeforeEach
  void setUp() {
    fileService = mock(GeminiFileService.class);
    chatModel = mock(ChatModel.class);
    objectMapper = new ObjectMapper();
    metricsRecorder = mock(GeminiMetricsRecorder.class);
    aiProperties = new QAskerAiProperties();

    FileMetadata meta =
        new FileMetadata(null, null, null, null, null, null, null, "gs://b/x.pdf", null);
    when(fileService.generateCacheKey(any(), any())).thenReturn("k");
    when(fileService.awaitCachedFileMetadata("k")).thenReturn(Optional.of(meta));
    when(metricsRecorder.recordChunkResult(anyLong(), any())).thenReturn(0.0);
  }

  private QuizTypeOrchestrator orchestrator(String type) {
    return switch (type) {
      case "MULTIPLE" ->
          new MultipleQuizOrchestrator(
              fileService, chatModel, objectMapper, metricsRecorder, aiProperties);
      case "BLANK" ->
          new BlankQuizOrchestrator(
              fileService, chatModel, objectMapper, metricsRecorder, aiProperties);
      case "OX" ->
          new OXQuizOrchestrator(
              fileService, chatModel, objectMapper, metricsRecorder, aiProperties);
      default -> throw new IllegalArgumentException(type);
    };
  }

  private int maxSelection(String type) {
    return "OX".equals(type) ? 2 : 4;
  }

  private GenerationRequestToAI request(
      String type, int quizCount, Consumer<AIProblemSet> consumer) {
    return GenerationRequestToAI.builder()
        .fileUrl("http://f/x.pdf")
        .strategyValue(type)
        .language("KO")
        .quizCount(quizCount)
        .referencePages(List.of(1, 2))
        .questionsConsumer(consumer)
        .build();
  }

  /** selCount개 선택지를 가진 문항 JSON. 첫 선택지가 정답. */
  private String question(int idx, int selCount) {
    String selections =
        IntStream.range(0, selCount)
            .mapToObj(
                s ->
                    "{\"content\":\"s"
                        + s
                        + "\",\"correct\":"
                        + (s == 0)
                        + ",\"explanation\":\"e\"}")
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

  private List<String> deliveredContents(List<AIProblemSet> sets) {
    return sets.stream().map(s -> s.quiz().get(0).content()).toList();
  }

  @ParameterizedTest
  @ValueSource(strings = {"MULTIPLE", "BLANK", "OX"})
  void deliversExactlyQuizCount_inOrder(String type) {
    List<AIProblemSet> collected = new ArrayList<>();
    when(chatModel.stream(any(Prompt.class))).thenReturn(fluxOf(questionsJson(List.of(1, 1, 1))));

    orchestrator(type).generateQuiz(request(type, 3, collected::add));

    assertThat(deliveredContents(collected)).containsExactly("Q0", "Q1", "Q2");
  }

  @ParameterizedTest
  @ValueSource(strings = {"MULTIPLE", "BLANK", "OX"})
  void truncatesWhenChunkOverProduces(String type) {
    List<AIProblemSet> collected = new ArrayList<>();
    when(chatModel.stream(any(Prompt.class)))
        .thenReturn(fluxOf(questionsJson(List.of(1, 1, 1, 1))));

    // quizCount=2 이지만 4문항 방출 → 2개로 잘림
    orchestrator(type).generateQuiz(request(type, 2, collected::add));

    assertThat(deliveredContents(collected)).containsExactly("Q0", "Q1");
  }

  @ParameterizedTest
  @ValueSource(strings = {"MULTIPLE", "BLANK", "OX"})
  void dropsQuestionsExceedingMaxSelectionCount(String type) {
    List<AIProblemSet> collected = new ArrayList<>();
    int oversize = maxSelection(type) + 1;
    // idx0은 초과 선택지 → drop. idx1, idx2만 전달.
    when(chatModel.stream(any(Prompt.class)))
        .thenReturn(fluxOf(questionsJson(List.of(oversize, 1, 1))));

    orchestrator(type).generateQuiz(request(type, 3, collected::add));

    assertThat(deliveredContents(collected)).containsExactly("Q1", "Q2");
  }

  @ParameterizedTest
  @ValueSource(strings = {"MULTIPLE", "BLANK", "OX"})
  void preservesEarlierChunk_whenLaterChunkFails(String type) {
    aiProperties.getChunk().setChunkSize(1); // quizCount=2 → 청크 2개
    List<AIProblemSet> collected = new ArrayList<>();
    when(chatModel.stream(any(Prompt.class)))
        .thenReturn(fluxOf(questionsJson(List.of(1))), Flux.error(new RuntimeException("boom")));

    int chunksDone = orchestrator(type).generateQuiz(request(type, 2, collected::add));

    assertThat(deliveredContents(collected)).containsExactly("Q0");
    assertThat(chunksDone).isEqualTo(1);
  }

  @ParameterizedTest
  @ValueSource(strings = {"MULTIPLE", "BLANK", "OX"})
  void rethrows_whenNothingDelivered(String type) {
    List<AIProblemSet> collected = new ArrayList<>();
    when(chatModel.stream(any(Prompt.class))).thenReturn(Flux.error(new RuntimeException("boom")));

    assertThatThrownBy(() -> orchestrator(type).generateQuiz(request(type, 1, collected::add)))
        .isInstanceOf(GeminiInfraException.class);
    assertThat(collected).isEmpty();
  }

  @ParameterizedTest
  @ValueSource(strings = {"MULTIPLE", "BLANK", "OX"})
  void injectsPreviousContextOnSecondChunkOnly(String type) {
    aiProperties.getChunk().setChunkSize(1); // quizCount=2 → 청크 2개
    List<AIProblemSet> collected = new ArrayList<>();
    when(chatModel.stream(any(Prompt.class)))
        .thenReturn(fluxOf(questionsJson(List.of(1))), fluxOf(questionsJson(List.of(1))));

    orchestrator(type).generateQuiz(request(type, 2, collected::add));

    ArgumentCaptor<Prompt> captor = ArgumentCaptor.forClass(Prompt.class);
    verify(chatModel, times(2)).stream(captor.capture());
    List<Prompt> prompts = captor.getAllValues();

    String base = QuizType.valueOf(type).getSystemGuideLine("KO");
    String firstSystem = prompts.get(0).getInstructions().get(0).getText();
    String secondSystem = prompts.get(1).getInstructions().get(0).getText();

    assertThat(firstSystem).isEqualTo(base);
    assertThat(secondSystem).contains("직전 청크 누적 문항 요약");
    assertThat(secondSystem).contains(typeSpecificDedupMarker(type));
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
