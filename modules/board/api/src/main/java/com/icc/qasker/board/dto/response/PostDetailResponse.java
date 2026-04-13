package com.icc.qasker.board.dto.response;

import java.time.Instant;
import java.util.List;

public record PostDetailResponse(
    Long boardId,
    String username,
    String title,
    String content,
    Long viewCount,
    String status,
    Instant createdAt,
    List<String> replies,
    boolean isWriter,
    String category) {}
