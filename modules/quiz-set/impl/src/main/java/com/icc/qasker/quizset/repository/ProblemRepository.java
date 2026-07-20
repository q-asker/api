package com.icc.qasker.quizset.repository;

import com.icc.qasker.quizset.entity.Problem;
import com.icc.qasker.quizset.entity.ProblemId;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProblemRepository extends JpaRepository<Problem, ProblemId> {

  @Query(
      """
        SELECT p
        FROM Problem p
        WHERE p.id.number > :number
        AND p.id.problemSetId = :problemSetId
        ORDER BY p.id.number ASC
        """)
  List<Problem> findRemainingProblems(
      @Param("problemSetId") Long problemSetId, @Param("number") Integer number);

  List<Problem> findByIdProblemSetId(Long problemSetId);

  // Pass 2 재검토용 — 세트/묶음 전량을 managed 상태로 로드한다.
  List<Problem> findByIdProblemSetIdIn(Collection<Long> problemSetIds);

  List<Problem> findByIdInOrderByIdNumberAsc(Collection<ProblemId> id);

  @EntityGraph(attributePaths = {"explanationContent"})
  @Query("SELECT p FROM Problem p where p.id.problemSetId=:setId ORDER BY p.id.number")
  List<Problem> findExplanationsBySetId(@Param("setId") Long setId);

  @Modifying
  @Query("DELETE FROM Problem p WHERE p.id.problemSetId IN :problemSetIds")
  void deleteByProblemSetIdIn(@Param("problemSetIds") Collection<Long> problemSetIds);
}
