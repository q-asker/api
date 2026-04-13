package com.icc.qasker.board.entity;

import com.icc.qasker.board.dto.BoardCategory;
import com.icc.qasker.global.entity.CreatedAt;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLRestriction;
import org.springframework.data.annotation.LastModifiedDate;

@Entity
@Getter
@Table(name = "board")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@SQLRestriction("status != 'DELETED'")
public class Board extends CreatedAt {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long boardId;

  @Column(name = "user_id", nullable = false)
  private String userId;

  @Column(nullable = false, length = 100)
  private String title;

  @Column(nullable = false, columnDefinition = "LONGTEXT")
  private String content;

  private Long viewCount;

  @Enumerated(EnumType.STRING)
  private BoardStatus status;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private BoardCategory category;

  @LastModifiedDate private Instant updatedAt;

  @OneToMany(mappedBy = "board", cascade = CascadeType.ALL, orphanRemoval = true)
  private List<Reply> replies = new ArrayList<>();

  @Builder
  public Board(String userId, String title, String content, BoardCategory category) {
    this.userId = userId;
    this.title = title;
    this.content = content;
    this.viewCount = 0L;
    this.status = BoardStatus.IN_PROGRESS;
    this.category = (category != null) ? category : BoardCategory.INQUIRY;
  }

  public void update(String title, String content) {
    this.title = title;
    this.content = content;
  }

  public void changeStatus(BoardStatus status) {
    this.status = status;
  }

  public void incrementViewCount() {
    this.viewCount += 1;
  }
}
