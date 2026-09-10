package com.icc.qasker.ai.service.multiple;

import com.icc.qasker.ai.dto.AIProblem;
import com.icc.qasker.ai.dto.GenerationRequestToAI;
import com.icc.qasker.ai.service.ChunkedQuizGenerator;
import com.icc.qasker.ai.service.QuizTypeOrchestrator;
import com.icc.qasker.ai.service.QuizTypeSpec;
import com.icc.qasker.ai.service.SelectionQuizSupport;
import com.icc.qasker.ai.service.i18n.GuideLines;
import com.icc.qasker.ai.service.multiple.prompt.MultipleGuideLine;
import com.icc.qasker.ai.service.multiple.prompt.MultipleRequestPrompt;
import com.icc.qasker.ai.structure.GeminiQuestion;
import com.icc.qasker.global.quiz.QuizType;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 객관식(MULTIPLE) 퀴즈 유형. 생성 엔진은 {@link ChunkedQuizGenerator} 하나를 공유하고, 이 클래스는 이 유형의 차이만 정의한다. */
@Component
@RequiredArgsConstructor
public class MultipleQuizOrchestrator
    implements QuizTypeOrchestrator, QuizTypeSpec<GeminiQuestion> {

  /** 이 유형에서 허용하는 최대 선지 수. 초과 문항은 채택하지 않는다. */
  private static final int MAX_SELECTION_COUNT = 4;

  private final ChunkedQuizGenerator generator;

  @Override
  public QuizType getSupportedType() {
    return QuizType.MULTIPLE;
  }

  @Override
  public void generateQuiz(GenerationRequestToAI request) {
    generator.generate(request, this);
  }

  @Override
  public String systemGuideLine(String language) {
    return GuideLines.withLanguage(MultipleGuideLine.content, language);
  }

  @Override
  public String requestPrompt(int quizCount, String customInstruction) {
    return MultipleRequestPrompt.generate(quizCount, customInstruction);
  }

  @Override
  public Class<GeminiQuestion> elementType() {
    return SelectionQuizSupport.elementType();
  }

  @Override
  public String dedupInstruction() {
    return MultipleRequestPrompt.DEDUP_INSTRUCTION;
  }

  @Override
  public String responseSchema(String customInstruction) {
    return SelectionQuizSupport.responseSchema(customInstruction);
  }

  @Override
  public boolean accept(GeminiQuestion question) {
    return SelectionQuizSupport.accept(question, MAX_SELECTION_COUNT);
  }

  @Override
  public AIProblem toProblem(GeminiQuestion question, List<Integer> sourcePages) {
    return SelectionQuizSupport.toProblem(question, sourcePages);
  }

  @Override
  public Optional<GeminiQuestion> parseFirst(String text) {
    return SelectionQuizSupport.parseFirst(text);
  }
}
