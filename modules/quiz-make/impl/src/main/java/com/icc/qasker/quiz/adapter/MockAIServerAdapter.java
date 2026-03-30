package com.icc.qasker.quiz.adapter;

import com.icc.qasker.ai.QuizOrchestrationService;
import com.icc.qasker.ai.dto.AIProblem;
import com.icc.qasker.ai.dto.AIProblemSet;
import com.icc.qasker.ai.dto.AISelection;
import com.icc.qasker.ai.dto.GenerationRequestToAI;
import java.util.ArrayList;
import java.util.List;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

@Component
@Primary
@Profile("mock")
public class MockAIServerAdapter extends AIServerAdapter {

  public MockAIServerAdapter(QuizOrchestrationService quizOrchestrationService) {
    super(quizOrchestrationService);
  }

  @Override
  public int streamRequest(GenerationRequestToAI request) {
    int quizCount = request.quizCount();
    List<Integer> pages =
        CollectionUtils.isEmpty(request.referencePages()) ? List.of(1) : request.referencePages();

    // 3개 청크로 나누어 전송
    int[][] ranges = {
      {1, quizCount / 3},
      {quizCount / 3 + 1, 2 * (quizCount / 3)},
      {2 * (quizCount / 3) + 1, quizCount}
    };

    for (int[] range : ranges) {
      List<AIProblem> problems = new ArrayList<>();
      for (int i = range[0]; i <= range[1]; i++) {
        problems.add(
            new AIProblem(
                i,
                "Mock question " + i,
                "Mock explanation for question " + i,
                List.of(
                    new AISelection("Option A", "Mock explanation A", true),
                    new AISelection("Option B", "Mock explanation B", false),
                    new AISelection("Option C", "Mock explanation C", false),
                    new AISelection("Option D", "Mock explanation D", false)),
                pages));
      }
      request.questionsConsumer().accept(new AIProblemSet(problems));

      if (range != ranges[ranges.length - 1]) {
        try {
          Thread.sleep(10_000);
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          return 3;
        }
      }
    }
    return 3;
  }
}
