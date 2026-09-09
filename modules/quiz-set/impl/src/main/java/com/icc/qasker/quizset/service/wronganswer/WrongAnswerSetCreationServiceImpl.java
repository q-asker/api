package com.icc.qasker.quizset.service.wronganswer;

import com.icc.qasker.quizset.GenerationStatus;
import com.icc.qasker.quizset.ProblemSetOrigin;
import com.icc.qasker.quizset.WrongAnswerSetCreationService;
import com.icc.qasker.quizset.dto.WrongAnswerSetCreation;
import com.icc.qasker.quizset.dto.readonly.ProblemLineage;
import com.icc.qasker.quizset.entity.Problem;
import com.icc.qasker.quizset.entity.ProblemId;
import com.icc.qasker.quizset.entity.ProblemSet;
import com.icc.qasker.quizset.repository.ProblemRepository;
import com.icc.qasker.quizset.repository.ProblemSetRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 원본 문항을 그대로 복제해 오답 문제집을 만든다.
 *
 * <p>호출자의 트랜잭션에 참여한다(새 트랜잭션을 열지 않는다) — 세트·문항과 호출자가 함께 남기는 풀이 기록이 한 덩어리로 커밋돼야 반쯤 만들어진 문제집이 목록에 남지 않기
 * 때문이다.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class WrongAnswerSetCreationServiceImpl implements WrongAnswerSetCreationService {

  private final ProblemSetRepository problemSetRepository;
  private final ProblemRepository problemRepository;

  @Override
  public Long create(WrongAnswerSetCreation request) {
    ProblemSet problemSet =
        problemSetRepository.save(
            ProblemSet.builder()
                .userId(request.userId())
                .sessionId(request.sessionId())
                .title(request.title())
                .quizType(request.quizType())
                .totalQuizCount(request.sources().size())
                // 자료 없이 만들어진 세트다. file_url 은 NOT NULL DEFAULT '' 이라 빈 문자열이 정상값이다.
                .fileUrl("")
                .origin(ProblemSetOrigin.WRONG_ANSWER)
                .sourceFolderId(request.sourceFolderId())
                // 문항이 이미 확정돼 있으므로 생성 중 상태를 거치지 않는다 — 응답 직후 바로 풀 수 있어야 한다.
                .generationStatus(GenerationStatus.COMPLETED)
                .build());

    problemRepository.saveAll(cloneProblems(request.sources(), problemSet));
    return problemSet.getId();
  }

  /** 요청된 순서대로 1..N 번을 다시 매겨 복제한다. 조회는 한 번에 하고, 순서는 요청 순서를 따른다. */
  private List<Problem> cloneProblems(List<ProblemLineage> sources, ProblemSet target) {
    List<ProblemId> ids =
        sources.stream()
            .map(s -> ProblemId.builder().problemSetId(s.problemSetId()).number(s.number()).build())
            .toList();
    Map<ProblemLineage, Problem> originals =
        problemRepository.findAllWithExplanationByIdIn(ids).stream()
            .collect(
                Collectors.toMap(
                    p -> new ProblemLineage(p.getId().getProblemSetId(), p.getId().getNumber()),
                    Function.identity()));

    List<Problem> clones = new ArrayList<>(sources.size());
    int number = 0;
    for (ProblemLineage source : sources) {
      Problem original = originals.get(source);
      if (original == null) {
        continue;
      }
      clones.add(cloneOne(original, target, ++number));
    }
    return clones;
  }

  private Problem cloneOne(Problem original, ProblemSet target, int number) {
    ProblemLineage ancestor =
        new ProblemLineage(
            original.getOriginProblemSetId() == null
                ? original.getId().getProblemSetId()
                : original.getOriginProblemSetId(),
            original.getOriginNumber() == null
                ? original.getId().getNumber()
                : original.getOriginNumber());

    Problem clone =
        Problem.builder()
            .id(ProblemId.builder().number(number).build())
            .problemSet(target)
            .title(original.getTitle())
            // 원본 자료를 가리키는 페이지 번호는 물려주지 않는다. 오답 문제집엔 자료가 없어 해설의 참조 자료 안내가
            // 사실과 다른 말을 하게 되고, FR-004 가 요구하는 동일성(지문·선택지·정답·해설)에도 들어 있지 않다.
            .originProblemSetId(ancestor.problemSetId())
            .originNumber(ancestor.number())
            .build();
    clone.bindQuizData(original.getSelections(), List.of());
    clone.updateExplanation(original.getExplanationContent());
    clone.updateAppliedInstruction(original.getAppliedInstruction());
    return clone;
  }
}
