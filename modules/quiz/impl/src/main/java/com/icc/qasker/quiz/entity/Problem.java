package com.icc.qasker.quiz.entity;

import static jakarta.persistence.FetchType.LAZY;

import com.icc.qasker.global.entity.CreatedAt;
import com.icc.qasker.quiz.converter.IntegerListConverter;
import com.icc.qasker.quiz.converter.SelectionListConverter;
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

@Entity
@Getter
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
  private List<SelectionData> selections = new ArrayList<>();

  @Column(columnDefinition = "TEXT")
  private String explanationContent;

  @Convert(converter = IntegerListConverter.class)
  @Column(columnDefinition = "TEXT", nullable = false)
  @Builder.Default
  private List<Integer> referencedPages = new ArrayList<>();

  public void bindChildren(
      List<SelectionData> selections, String explanationContent, List<Integer> referencedPages) {
    this.selections = selections;
    this.explanationContent = explanationContent;
    this.referencedPages = referencedPages;
  }
}
