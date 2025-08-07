package com.icc.qasker.auth.normal.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;

@Getter
public class NormalJoinRequest {

    @NotNull(message = "id가 null입니다.")
    private String id;
    @NotNull(message = "password가 null입니다.")
    private String password;
}
