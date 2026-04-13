package com.icc.qasker.board.entity;

import com.icc.qasker.global.entity.CreatedAt;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

@Entity
@Table(name = "reply")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Reply extends CreatedAt {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long replyId;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "board_id", nullable = false)
  @OnDelete(action = OnDeleteAction.CASCADE)
  private Board board;

  @Column(name = "admin_id", nullable = false)
  private String adminId;

  @Column(nullable = false, columnDefinition = "LONGTEXT")
  private String content;

  @Builder
  public Reply(Board board, String adminId, String content) {
    this.board = board;
    this.adminId = adminId;
    this.content = content;
  }
}
