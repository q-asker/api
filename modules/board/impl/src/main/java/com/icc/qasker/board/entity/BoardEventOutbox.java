package com.icc.qasker.board.entity;

import com.icc.qasker.board.event.BoardEventType;
import com.icc.qasker.global.entity.CreatedAt;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Table(name = "board_event_outbox")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BoardEventOutbox extends CreatedAt {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false)
  private Long boardId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 30)
  private BoardEventType eventType;

  @Lob
  @Column(nullable = false)
  private String payload;

  @Column(nullable = false)
  private boolean published;

  @Builder
  public BoardEventOutbox(Long boardId, BoardEventType eventType, String payload) {
    this.boardId = boardId;
    this.eventType = eventType;
    this.payload = payload;
    this.published = false;
  }

  public void markPublished() {
    this.published = true;
  }
}
