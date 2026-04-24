package com.icc.qasker.ai.mapper;

import com.icc.qasker.ai.dto.AIProblem;
import com.icc.qasker.ai.dto.AIProblemSet;
import com.icc.qasker.ai.dto.AISelection;
import com.icc.qasker.ai.structure.GeminiEssayQuestion;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/** GeminiEssayQuestion → AIProblemSet 변환. modelAnswer를 Selection(content, true)로 매핑한다. */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class GeminiEssayQuestionMapper {

  private static final Pattern PAGE_PATTERN = Pattern.compile("\\[(\\d+)p\\]\\s*>");

  public static AIProblemSet toDto(List<GeminiEssayQuestion> questions) {
    return toDto(questions, null);
  }

  /**
   * ESSAY 문항을 AIProblemSet으로 변환한다. modelAnswer는 Selection(modelAnswer, true)로 저장하고, explanation은
   * appliedInstruction 위치에 임시 저장하여 다음 레이어에서 explanationContent로 매핑한다.
   */
  public static AIProblemSet toDto(List<GeminiEssayQuestion> questions, List<Integer> sourcePages) {
    List<AIProblem> result =
        questions.stream()
            .map(
                q -> {
                  // modelAnswer → Selection(content=modelAnswer, correct=true)
                  List<AISelection> selections =
                      q.modelAnswer() != null
                          ? List.of(
                              new AISelection(
                                  remapText(q.modelAnswer(), sourcePages),
                                  remapText(q.explanation(), sourcePages),
                                  true))
                          : List.of();

                  return new AIProblem(
                      q.content(),
                      q.bloomsLevel(),
                      selections,
                      remapPages(q.referencedPages(), sourcePages),
                      remapText(q.appliedInstruction(), sourcePages));
                })
            .toList();

    return new AIProblemSet(result);
  }

  private static String remapText(String text, List<Integer> sourcePages) {
    if (text == null || sourcePages == null || sourcePages.isEmpty()) {
      return text;
    }

    StringBuilder sb = new StringBuilder();
    Matcher matcher = PAGE_PATTERN.matcher(text);
    int lastEnd = 0;

    while (matcher.find()) {
      sb.append(text, lastEnd, matcher.start());
      try {
        int aiPage = Integer.parseInt(matcher.group(1));
        int index = aiPage - 1;
        if (index >= 0 && index < sourcePages.size()) {
          sb.append("[").append(sourcePages.get(index)).append("p] >");
        } else {
          sb.append(matcher.group());
        }
      } catch (NumberFormatException e) {
        sb.append(matcher.group());
      }
      lastEnd = matcher.end();
    }
    sb.append(text.substring(lastEnd));
    return sb.toString();
  }

  private static List<Integer> remapPages(List<Integer> aiPages, List<Integer> sourcePages) {
    if (aiPages == null) return List.of();
    if (sourcePages == null || sourcePages.isEmpty()) return aiPages;

    return aiPages.stream()
        .map(
            page -> {
              int index = page - 1;
              if (index >= 0 && index < sourcePages.size()) {
                return sourcePages.get(index);
              }
              return page;
            })
        .distinct()
        .sorted()
        .toList();
  }
}
