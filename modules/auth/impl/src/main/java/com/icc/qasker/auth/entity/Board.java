package com.icc.qasker.auth.entity;

import com.icc.qasker.global.entity.CreatedAt;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.LastModifiedDate;

@Entity
@Table(name = "board")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Board extends CreatedAt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long boardId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 100)
    private String title;

    @Lob
    @Column(nullable = false)
    private String content;

    private Long viewCount = 0L;

    @Enumerated(EnumType.STRING)
    private BoardStatus status;

    @LastModifiedDate
    private LocalDateTime updateAt;

    @Builder
    public Board(User user, String title, String content) {
        this.user = user;
        this.title = title;
        this.content = content;
        this.viewCount = 0L;
        this.status = BoardStatus.IN_PROGRESS;
    }

    public void update(String title, String content) {
        this.title = title;
        this.content = content;
    }

    public void changeStatus(BoardStatus status) {
        this.status = status;
    }
}
