package com.icc.qasker.auth.dto.response;

import java.time.Instant;
import java.util.List;

public record SinglePostResponse(Long boardId, String username, String title, String content,
                                 Long viewCount, String status, Instant createAt,
                                 List<String> replies) {

}
