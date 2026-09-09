package com.icc.qasker.quizset.repository;

import com.icc.qasker.quizset.entity.Problem;
import com.icc.qasker.quizset.entity.ProblemId;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
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

  List<Problem> findByIdInOrderByIdNumberAsc(Collection<ProblemId> id);

  @EntityGraph(attributePaths = {"explanationContent"})
  @Query("SELECT p FROM Problem p where p.id.problemSetId=:setId ORDER BY p.id.number")
  List<Problem> findExplanationsBySetId(@Param("setId") Long setId);

  /** 여러 세트에 흩어진 문항을 한 번에 읽는다. 복제가 해설까지 그대로 옮기므로 lazy 그룹을 함께 페치해 문항마다 개별 SELECT가 나가지 않게 한다. */
  @EntityGraph(attributePaths = {"explanationContent"})
  @Query("SELECT p FROM Problem p WHERE p.id IN :ids")
  List<Problem> findAllWithExplanationByIdIn(@Param("ids") Collection<ProblemId> ids);
}
