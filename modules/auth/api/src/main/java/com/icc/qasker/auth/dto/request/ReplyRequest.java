package com.icc.qasker.auth.dto.request;

import jakarta.validation.constraints.NotBlank;

public record ReplyRequest(
    @NotBlank(message = "댓글이 없습니다.")
    String content) {

}
