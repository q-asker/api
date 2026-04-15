package com.icc.qasker.quiz.dto.feresponse;

import java.util.List;

public record ExplanationResponse(List<ResultResponse> results, String fileUrl) {}
