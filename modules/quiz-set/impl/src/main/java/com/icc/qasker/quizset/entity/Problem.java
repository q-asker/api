package com.icc.qasker.quizset.entity;

import static jakarta.persistence.FetchType.LAZY;

import com.icc.qasker.global.entity.CreatedAt;
import com.icc.qasker.quizset.converter.AcceptedAnswerListConverter;
import com.icc.qasker.quizset.converter.IntegerListConverter;
import com.icc.qasker.quizset.converter.SelectionListConverter;
import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.LazyGroup;

@Entity
@Getter
// 순수 서빙 엔티티(질문·선지·해설·페이지·지시). 품질/생성 근거는 problem_quality_log로 분리.
// 바이트코드 인핸스먼트 dirty tracking과 결합해 변경 컬럼만 UPDATE(예: 해설 저장 시 explanation_content만).
@DynamicUpdate
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class Problem extends CreatedAt {

  @EmbeddedId private ProblemId id;

  @Column(columnDefinition = "TEXT")
  private String title;

  @ManyToOne(fetch = LAZY)
  @MapsId("problemSetId")
  @JoinColumn(name = "problem_set_id")
  private ProblemSet problemSet;

  @Convert(converter = SelectionListConverter.class)
  @Column(columnDefinition = "TEXT", nullable = false)
  @Builder.Default
  private List<Selection> selections = new ArrayList<>();

  // REAL_BLANK 관용 채점용 허용답안(빈칸 순서대로 {모범답안, 허용변형}). 허용변형이 없는 문항(이 기능
  // 이전 생성분 포함)은 null — 이때 클라이언트는 완전일치+표기정규화 폴백으로 채점한다. 판정은 클라이언트가 하고
  // 백엔드는 이 값을 생성·저장·전달만 한다.
  @Convert(converter = AcceptedAnswerListConverter.class)
  @Column(columnDefinition = "TEXT")
  private List<AcceptedAnswer> acceptedAnswers;

  @Basic(fetch = LAZY)
  @LazyGroup("explanation")
  @Column(columnDefinition = "TEXT")
  private String explanationContent;

  @Convert(converter = IntegerListConverter.class)
  @Column(columnDefinition = "TEXT", nullable = false)
  @Builder.Default
  private List<Integer> referencedPages = new ArrayList<>();

  @Column(columnDefinition = "TEXT")
  private String appliedInstruction;

  // Phase 1: 문제 생성 시 선택지와 참조 페이지를 바인딩
  public void bindQuizData(List<Selection> selections, List<Integer> referencedPages) {
    this.selections = selections == null ? List.of() : List.copyOf(selections);
    this.referencedPages = referencedPages == null ? List.of() : List.copyOf(referencedPages);
  }

  // 문제 생성 시 REAL_BLANK 허용답안을 바인딩. null이면 허용변형 없음(폴백 채점 대상).
  public void bindAcceptedAnswers(List<AcceptedAnswer> acceptedAnswers) {
    this.acceptedAnswers = acceptedAnswers == null ? null : List.copyOf(acceptedAnswers);
  }

  public void updateAppliedInstruction(String appliedInstruction) {
    this.appliedInstruction = appliedInstruction;
  }

  // 해설 저장·갱신 시 호출 (문항 매핑·해설 재검토)
  public void updateExplanation(String explanationContent) {
    this.explanationContent = explanationContent;
  }
}
