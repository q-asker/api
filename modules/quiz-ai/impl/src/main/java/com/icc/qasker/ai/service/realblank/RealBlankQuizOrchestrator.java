package com.icc.qasker.ai.service.realblank;

import com.icc.qasker.ai.dto.AIProblem;
import com.icc.qasker.ai.dto.GenerationRequestToAI;
import com.icc.qasker.ai.mapper.GeminiRealBlankQuestionMapper;
import com.icc.qasker.ai.service.ChunkedQuizGenerator;
import com.icc.qasker.ai.service.QuizTypeOrchestrator;
import com.icc.qasker.ai.service.QuizTypeSpec;
import com.icc.qasker.ai.service.i18n.GuideLines;
import com.icc.qasker.ai.service.realblank.prompt.RealBlankGuideLine;
import com.icc.qasker.ai.service.realblank.prompt.RealBlankRequestPrompt;
import com.icc.qasker.ai.structure.GeminiRealBlankQuestion;
import com.icc.qasker.ai.structure.GeminiRealBlankResponse;
import com.icc.qasker.ai.structure.GeminiRealBlankResponseSchema;
import com.icc.qasker.global.quiz.QuizType;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.stereotype.Component;

/**
 * REAL_BLANK(직접 입력 단답) 퀴즈 유형. 오답 선택지를 만들지 않고(FR-008), 정답(answer)을 단일 선지로, 인정 범위(acceptedAnswers)를 그
 * 선지에 실어 보존한다.
 *
 * <p>생성 엔진은 {@link ChunkedQuizGenerator} 하나를 공유하고, 이 클래스는 이 유형의 차이만 정의한다.
 */
@Component
@RequiredArgsConstructor
public class RealBlankQuizOrchestrator
    implements QuizTypeOrchestrator, QuizTypeSpec<GeminiRealBlankQuestion> {

  private final ChunkedQuizGenerator generator;

  @Override
  public QuizType getSupportedType() {
    return QuizType.REAL_BLANK;
  }

  @Override
  public void generateQuiz(GenerationRequestToAI request) {
    generator.generate(request, this);
  }

  @Override
  public String systemGuideLine(String language) {
    return GuideLines.withLanguage(RealBlankGuideLine.content, language);
  }

  @Override
  public String requestPrompt(int quizCount, String customInstruction) {
    return RealBlankRequestPrompt.generate(quizCount, customInstruction);
  }

  @Override
  public Class<GeminiRealBlankQuestion> elementType() {
    return GeminiRealBlankQuestion.class;
  }

  @Override
  public String dedupInstruction() {
    return RealBlankRequestPrompt.DEDUP_INSTRUCTION;
  }

  @Override
  public String responseSchema(String customInstruction) {
    return GeminiRealBlankResponseSchema.forInstruction(customInstruction);
  }

  @Override
  public boolean accept(GeminiRealBlankQuestion question) {
    // 직접 입력 단답은 오답 선지 개념이 없으므로 drop 규칙 없음.
    return true;
  }

  @Override
  public AIProblem toProblem(GeminiRealBlankQuestion question, List<Integer> sourcePages) {

    return GeminiRealBlankQuestionMapper.toDto(List.of(question), sourcePages).quiz().getFirst();
  }

  @Override
  public Optional<GeminiRealBlankQuestion> parseFirst(String text) {
    GeminiRealBlankResponse parsed =
        new BeanOutputConverter<>(GeminiRealBlankResponse.class).convert(text);
    if (parsed == null || parsed.questions() == null || parsed.questions().isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(parsed.questions().getFirst());
  }
}
