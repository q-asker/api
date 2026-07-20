package com.icc.qasker.quizhistory.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.icc.qasker.ai.EssayGradingService;
import com.icc.qasker.ai.dto.EssayGradingResult;
import com.icc.qasker.global.component.HashUtil;
import com.icc.qasker.global.error.CustomException;
import com.icc.qasker.quizhistory.entity.EssayGradeLog;
import com.icc.qasker.quizhistory.repository.EssayGradeLogRepository;
import com.icc.qasker.quizset.ProblemSetReadService;
import com.icc.qasker.quizset.dto.readonly.ProblemDetail;
import com.icc.qasker.quizset.dto.readonly.SelectionDetail;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** EssayGradeService 회귀 세이프티 넷. 데이터 정합성 실패 예외와 정상 채점 시 비동기 로그 저장 트리거를 고정한다. */
@ExtendWith(MockitoExtension.class)
class EssayGradeServiceTest {

  @Mock private ProblemSetReadService problemSetReadService;
  @Mock private EssayGradingService essayGradingService;
  @Mock private EssayGradeLogRepository essayGradeLogRepository;
  @Mock private HashUtil hashUtil;

  @InjectMocks private EssayGradeServiceImpl service;

  private ProblemDetail problem(List<SelectionDetail> selections, String explanation) {
    return new ProblemDetail(1, "essay-title", selections, explanation);
  }

  @Test
  @DisplayName("grade: 모범답안(정답 selection)이 없으면 예외")
  void grade_missingModelAnswer_throws() {
    when(hashUtil.decode("ps")).thenReturn(1L);
    when(problemSetReadService.findProblemsByProblemSetId(1L))
        .thenReturn(List.of(problem(List.of(new SelectionDetail("only", false)), "rubric")));

    assertThatThrownBy(() -> service.grade("user1", "ps", 1, "answer", 1))
        .isInstanceOf(CustomException.class);
  }

  @Test
  @DisplayName("grade: 루브릭(explanationContent)이 비어 있으면 예외")
  void grade_blankRubric_throws() {
    when(hashUtil.decode("ps")).thenReturn(1L);
    when(problemSetReadService.findProblemsByProblemSetId(1L))
        .thenReturn(List.of(problem(List.of(new SelectionDetail("model", true)), "  ")));

    assertThatThrownBy(() -> service.grade("user1", "ps", 1, "answer", 1))
        .isInstanceOf(CustomException.class);
  }

  @Test
  @DisplayName("grade: 정상 경로에서 결과 반환 + 비동기 채점 로그 저장 트리거")
  void grade_success_returnsResultAndSavesLog() {
    when(hashUtil.decode("ps")).thenReturn(1L);
    when(problemSetReadService.findProblemsByProblemSetId(1L))
        .thenReturn(List.of(problem(List.of(new SelectionDetail("model", true)), "rubric")));
    EssayGradingResult result = new EssayGradingResult(List.of(), 7, 10, "feedback", "{}");
    when(essayGradingService.grade(
            eq("essay-title"), eq("model"), eq("rubric"), eq("student"), anyInt()))
        .thenReturn(result);

    EssayGradingResult returned = service.grade("user1", "ps", 1, "student", 2);

    assertThat(returned).isSameAs(result);
    verify(essayGradeLogRepository, timeout(2000)).save(any(EssayGradeLog.class));
  }
}
