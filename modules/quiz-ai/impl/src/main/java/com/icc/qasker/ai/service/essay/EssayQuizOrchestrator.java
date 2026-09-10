package com.icc.qasker.ai.service.essay;

import com.icc.qasker.ai.dto.AIProblem;
import com.icc.qasker.ai.dto.GenerationRequestToAI;
import com.icc.qasker.ai.mapper.GeminiEssayQuestionMapper;
import com.icc.qasker.ai.service.ChunkedQuizGenerator;
import com.icc.qasker.ai.service.QuizTypeOrchestrator;
import com.icc.qasker.ai.service.QuizTypeSpec;
import com.icc.qasker.ai.service.essay.prompt.EssayGuideLine;
import com.icc.qasker.ai.service.essay.prompt.EssayRequestPrompt;
import com.icc.qasker.ai.service.i18n.GuideLines;
import com.icc.qasker.ai.structure.GeminiEssayQuestion;
import com.icc.qasker.ai.structure.GeminiEssayResponse;
import com.icc.qasker.ai.structure.GeminiEssayResponseSchema;
import com.icc.qasker.global.quiz.QuizType;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.stereotype.Component;

/**
 * 서술형(ESSAY) 퀴즈 유형. 선지형과 같은 실행 모델(청크 분할·비동기 품질검증·컨텍스트 캐시·멀티턴 대화)을 공유한다.
 *
 * <p>생성 엔진은 {@link ChunkedQuizGenerator} 하나를 공유하고, 이 클래스는 이 유형의 차이만 정의한다.
 */
@Component
@RequiredArgsConstructor
public class EssayQuizOrchestrator
    implements QuizTypeOrchestrator, QuizTypeSpec<GeminiEssayQuestion> {

  private final ChunkedQuizGenerator generator;

  @Override
  public QuizType getSupportedType() {
    return QuizType.ESSAY;
  }

  @Override
  public void generateQuiz(GenerationRequestToAI request) {
    generator.generate(request, this);
  }

  @Override
  public String systemGuideLine(String language) {
    return GuideLines.withLanguage(EssayGuideLine.content, language);
  }

  @Override
  public String requestPrompt(int quizCount, String customInstruction) {
    return EssayRequestPrompt.generate(quizCount, customInstruction);
  }

  @Override
  public Class<GeminiEssayQuestion> elementType() {
    return GeminiEssayQuestion.class;
  }

  @Override
  public String dedupInstruction() {
    return EssayRequestPrompt.DEDUP_INSTRUCTION;
  }

  @Override
  public String responseSchema(String customInstruction) {
    return GeminiEssayResponseSchema.forInstruction(customInstruction);
  }

  @Override
  public boolean accept(GeminiEssayQuestion question) {
    // 서술형은 선지 개념이 없으므로 drop 규칙 없음.
    return true;
  }

  @Override
  public AIProblem toProblem(GeminiEssayQuestion question, List<Integer> sourcePages) {
    // 매퍼 결과를 그대로 보존(재조립 금지) — modelAnswer→선지.content, explanation→선지.explanation, correct=true.
    return GeminiEssayQuestionMapper.toDto(List.of(question), sourcePages).quiz().getFirst();
  }

  @Override
  public Optional<GeminiEssayQuestion> parseFirst(String text) {
    GeminiEssayResponse parsed = new BeanOutputConverter<>(GeminiEssayResponse.class).convert(text);
    if (parsed == null || parsed.questions() == null || parsed.questions().isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(parsed.questions().getFirst());
  }
}
