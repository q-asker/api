package com.icc.qasker.board.dto.response;

import java.util.List;
import org.springframework.data.domain.Page;

public record PostPageResponse(
    List<PostResponse> posts, long totalElements, int totalPages, int size, int number) {

  public static PostPageResponse from(Page<PostResponse> page) {
    return new PostPageResponse(
        page.getContent(),
        page.getTotalElements(),
        page.getTotalPages(),
        page.getSize(),
        page.getNumber());
  }
}
