package com.icc.qasker.board.dto.response;

import java.util.List;

public record PostPageResponse(
    List<PostResponse> posts, long totalElements, int totalPages, int size, int number) {}
