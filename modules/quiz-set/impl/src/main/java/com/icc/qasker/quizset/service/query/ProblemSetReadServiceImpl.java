package com.icc.qasker.quizset.service.query;

import com.icc.qasker.quizset.ProblemSetReadService;
import com.icc.qasker.quizset.dto.readonly.ProblemDetail;
import com.icc.qasker.quizset.dto.readonly.ProblemSetSummary;
import com.icc.qasker.quizset.dto.readonly.SelectionDetail;
import com.icc.qasker.quizset.entity.Problem;
import com.icc.qasker.quizset.entity.ProblemSet;
import com.icc.qasker.quizset.repository.ProblemRepository;
import com.icc.qasker.quizset.repository.ProblemSetRepository;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProblemSetReadServiceImpl implements ProblemSetReadService {

  private final ProblemSetRepository problemSetRepository;
  private final ProblemRepository problemRepository;

  @Override
  public Optional<ProblemSetSummary> findProblemSetById(Long id) {
    return problemSetRepository.findById(id).map(this::toSummary);
  }

  @Override
  public List<ProblemSetSummary> findProblemSetsByIds(List<Long> ids) {
    return problemSetRepository.findAllById(ids).stream().map(this::toSummary).toList();
  }

  @Override
  public List<ProblemDetail> findProblemsByProblemSetId(Long problemSetId) {
    return problemRepository.findByIdProblemSetId(problemSetId).stream()
        .map(this::toDetail)
        .toList();
  }

  private ProblemSetSummary toSummary(ProblemSet ps) {
    return new ProblemSetSummary(
        ps.getId(), ps.getQuizType(), ps.getTotalQuizCount(), ps.getTitle(), ps.getCreatedAt());
  }

  private ProblemDetail toDetail(Problem p) {
    List<SelectionDetail> selections =
        p.getSelections().stream().map(s -> new SelectionDetail(s.content(), s.correct())).toList();
    return new ProblemDetail(p.getId().getNumber(), p.getTitle(), selections);
  }
}
