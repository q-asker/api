package com.icc.qasker.board.entity;

import com.icc.qasker.global.entity.CreatedAt;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Table(name = "feedback_board")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FeedbackBoard extends CreatedAt {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long feedbackBoardId;

  @Column(name = "user_id")
  private String userId;

  @Column(name = "content", columnDefinition = "LONGTEXT")
  private String content;

  @Builder
  public FeedbackBoard(String userId, String content) {
    this.userId = userId;
    this.content = content;
  }
}
