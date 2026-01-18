package com.icc.qasker.quiz.adapter;

import com.icc.qasker.quiz.dto.request.FeGenerationRequest;
import com.icc.qasker.quiz.dto.response.AiGenerationResponse;
import com.icc.qasker.quiz.dto.response.QuizGeneratedByAI;
import com.icc.qasker.quiz.dto.response.QuizGeneratedByAI.SelectionsOfAi;
import java.util.List;
import java.util.stream.IntStream;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * 부하 테스트용 Mock Adapter 'stress-test' 프로파일이 활성화되었을 때만 빈으로 등록되고,
 *
 * @Primary를 통해 기존 AiServerAdapter보다 우선순위를 가짐.
 */
@Component
@Profile("stress-test")
@Primary
public class MockAiServerAdapter extends AiServerAdapter {

    public MockAiServerAdapter() {
        super(null); // 부모의 RestClient는 필요 없음
    }

    @Override
    public AiGenerationResponse requestGenerate(FeGenerationRequest feGenerationRequest) {
        // 요청된 퀴즈 개수만큼 더미 데이터 생성
        List<QuizGeneratedByAI> quizzes = IntStream.range(0, feGenerationRequest.quizCount())
            .mapToObj(i -> new QuizGeneratedByAI(
                i + 1,
                "Mock Quiz Title " + i,
                List.of(
                    new SelectionsOfAi("Option 1", true),
                    new SelectionsOfAi("Option 2", false),
                    new SelectionsOfAi("Option 3", false),
                    new SelectionsOfAi("Option 4", false)
                ),
                "Mock Explanation " + i,
                List.of()
            ))
            .toList();

        return new AiGenerationResponse("Mock Problem Set Title", quizzes);
    }
}
