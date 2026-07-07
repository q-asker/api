package com.icc.qasker.quizset;

import com.icc.qasker.quizset.dto.airesponse.ExplanationGeneratedFromAI;
import com.icc.qasker.quizset.dto.airesponse.ProblemSetGeneratedEvent.QuizGeneratedFromAI;
import com.icc.qasker.quizset.dto.ferequest.enums.QuizType;
import java.util.List;

public interface QuizCommandService {

  Long initProblemSet(
      String userId,
      String sessionId,
      String title,
      Integer totalQuizCount,
      QuizType quizType,
      String uploadUrl,
      String customInstruction);

  void updateStatus(Long problemSetId, GenerationStatus status);

  List<Integer> saveBatch(List<QuizGeneratedFromAI> generatedProblems, Long problemSetId);

  /**
   * Phase 2: 저장된 문제 <b>1건</b>을 단일 엔티티로 재로드해 해설만 후속 저장한다(스트리밍 해설 완성 시마다 호출 — enhancement lazy +
   * inline dirty tracking 활용). 문항 질문·선지 내용·정답은 변경하지 않으며, 세트에 존재하지 않는 문항 번호는 건너뛴다.
   */
  void saveExplanation(Long problemSetId, ExplanationGeneratedFromAI explanation);

  /**
   * Phase 2 배치: 해설 <b>N건</b>을 하나의 트랜잭션에서 후속 저장한다. 문항별 read-modify-write를 단일 트랜잭션으로 묶어 커밋·redo
   * fsync·JDBC 왕복을 N→1로 줄인다(문항 1건당 개별 트랜잭션 대비). 각 문항 처리 규칙은 {@link #saveExplanation}과 동일하다.
   */
  void saveExplanations(Long problemSetId, List<ExplanationGeneratedFromAI> explanations);
}
