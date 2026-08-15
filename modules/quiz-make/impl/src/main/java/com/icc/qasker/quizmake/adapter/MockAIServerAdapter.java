package com.icc.qasker.quizmake.adapter;

import com.icc.qasker.ai.QuizOrchestrationService;
import com.icc.qasker.ai.dto.AIProblem;
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
  public void streamRequest(GenerationRequestToAI request) {
    int quizCount = request.quizCount();
    List<Integer> pages =
        CollectionUtils.isEmpty(request.referencePages()) ? List.of(1) : request.referencePages();

    // REAL_BLANK는 선택형과 산출물 형태가 달라(정답 1선지 + acceptedAnswers 2차원, 오답 선지 없음) 전용 목업을 낸다.
    // 단일 빈칸 + 다중 빈칸(≥2)을 모두 포함해 빈칸별 인정 목록 노출(FR-008) E2E를 실 Gemini 없이 검증한다.
    if ("REAL_BLANK".equals(request.quizType())) {
      realBlankMocks(quizCount, pages).forEach(request.sink()::saveProblem);
      return;
    }

    // 3개 청크로 나누어 전송
    int[][] ranges = {
      {1, quizCount / 3},
      {quizCount / 3 + 1, 2 * (quizCount / 3)},
      {2 * (quizCount / 3) + 1, quizCount}
    };

    for (int[] range : ranges) {
      List<AIProblem> problems = new ArrayList<>();
      for (int i = range[0]; i <= range[1]; i++) {
        // 1번 문항은 마크다운 서식(표·인용·코드·수식)을 담은 대표 픽스처로 낸다 — 기능 005 E2E가 전 요소 렌더를 검증한다.
        problems.add(i == 1 ? markdownFixture(pages) : plainMock(i, pages));
      }
      problems.forEach(request.sink()::saveProblem);
    }
  }

  /** REAL_BLANK 목업 세트: 2번 문항은 다중 빈칸(≥2), 나머지는 단일 빈칸. 정답 1선지 + acceptedAnswers 2차원(index 0=모범답). */
  private static List<AIProblem> realBlankMocks(int quizCount, List<Integer> pages) {
    List<AIProblem> problems = new ArrayList<>();
    for (int i = 1; i <= quizCount; i++) {
      problems.add(i == 2 ? realBlankMultiBlank(pages) : realBlankSingle(i, pages));
    }
    return problems;
  }

  /** 단일 빈칸 REAL_BLANK 목업. acceptedAnswers=[[모범답, 영문 이표기]]. */
  private static AIProblem realBlankSingle(int i, List<Integer> pages) {
    return new AIProblem(
        "진핵세포에서 이중막으로 둘러싸이고 산화적 인산화로 ATP를 합성하는 소기관을 _______(이)라 한다. (mock " + i + ")",
        "Remember — 명칭형",
        List.of(
            new AISelection(
                "미토콘드리아",
                "- **정답 추론**: 이중막·산화적 인산화·ATP 합성은 미토콘드리아의 고유 단서입니다. (mock " + i + ")",
                true,
                List.of(List.of("미토콘드리아", "mitochondria")))),
        pages,
        null);
  }

  /**
   * 다중 빈칸(2칸) REAL_BLANK 목업. acceptedAnswers=[[모범답1,변형...],[모범답2,변형...]] — 빈칸별 구분 렌더(FR-008) 검증용.
   */
  private static AIProblem realBlankMultiBlank(List<Integer> pages) {
    return new AIProblem(
        "세포 분열에서 ①_______은(는) 생식세포 4개를, ②_______은(는) 유전적으로 동일한 딸세포 2개를 만든다.",
        "Understand — 비교형",
        List.of(
            new AISelection(
                "감수분열, 체세포분열",
                "- **정답 추론**: ①은 감수분열, ②는 체세포분열입니다. (mock 다중 빈칸)",
                true,
                List.of(List.of("감수분열", "meiosis"), List.of("체세포분열", "유사분열", "mitosis")))),
        pages,
        null);
  }

  private static AIProblem plainMock(int i, List<Integer> pages) {
    return new AIProblem(
        "Mock question " + i,
        "Mock explanation for question " + i,
        List.of(
            new AISelection("Option A", "Mock explanation A", true),
            new AISelection("Option B", "Mock explanation B", false),
            new AISelection("Option C", "Mock explanation C", false),
            new AISelection("Option D", "Mock explanation D", false)),
        pages,
        null);
  }

  /** 표·인용·코드 펜스·인라인/블록 수식을 담은 마크다운 대표 문항(기능 005 렌더 E2E 픽스처). 이미 개행 분리된 유효 GFM이다. */
  private static AIProblem markdownFixture(List<Integer> pages) {
    String stem =
        """
        다음 표는 세포 소기관의 특성을 비교한 것이다.

        | 소기관 | 위치 | 주요 기능 |
        | :--- | :--- | :--- |
        | 미토콘드리아 | 동물·식물 | 세포 호흡 |
        | 엽록체 | 식물 | 광합성 |

        > **참고**: 광합성 반응식은 $6CO_2 + 6H_2O \\rightarrow C_6H_{12}O_6 + 6O_2$ 이다.

        ```python
        def energy():
            return "ATP"
        ```

        블록 수식으로 나타내면 다음과 같다.

        $$
        E = mc^2
        $$

        위 자료에서 **틀린 항목**을 고르면?""";
    return new AIProblem(
        stem,
        "Analyze",
        List.of(
            new AISelection(
                "미토콘드리아 — 세포 호흡",
                "- **정답 추론**: 미토콘드리아는 세포 호흡으로 ATP를 만든다.\n- **근거**: 표의 *주요 기능* 열 참조.",
                true),
            new AISelection("엽록체 — 광합성", "표와 일치하므로 오답이다.", false),
            new AISelection("리보솜 — 단백질 합성", "표에 없는 항목이다.", false),
            new AISelection("액포 — 저장", "표에 없는 항목이다.", false)),
        pages,
        null);
  }
}
