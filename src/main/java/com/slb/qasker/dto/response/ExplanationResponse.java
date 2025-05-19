package com.slb.qasker.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import java.util.List;

@Getter
@AllArgsConstructor
public class ExplanationResponse {
    private List<ResultResponse> results;
}
