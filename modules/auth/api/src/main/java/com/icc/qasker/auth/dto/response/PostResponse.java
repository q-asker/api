package com.icc.qasker.auth.dto.response;

import java.time.Instant;

public record PostResponse(Long boardId, String userName, String title, Long viewCount,
                           String status, Instant createdAt) {

}
