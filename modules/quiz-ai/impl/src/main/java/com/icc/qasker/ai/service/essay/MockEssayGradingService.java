package com.icc.qasker.ai.service.essay;

import com.icc.qasker.ai.EssayGradingService;
import com.icc.qasker.ai.dto.EssayGradingResult;
import com.icc.qasker.ai.dto.EssayGradingResult.ElementScore;
import java.util.List;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

/** ESSAY 채점 mock. Gemini 호출 없이 고정 결과를 반환한다(@Profile("mock")). */
@Service
@Primary
@Profile("mock")
public class MockEssayGradingService implements EssayGradingService {

  @Override
  public EssayGradingResult grade(
      String question, String modelAnswer, String rubric, String studentAnswer, int attemptCount) {
    List<ElementScore> elementScores =
        List.of(
            new ElementScore("Mock element 1", 5, 4, "GOOD", "Mock feedback 1"),
            new ElementScore("Mock element 2", 5, 3, "FAIR", "Mock feedback 2"));
    return new EssayGradingResult(elementScores, 7, 10, "Mock overall feedback", "{}");
  }
}
