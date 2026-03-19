package com.icc.qasker.quiz.dto.feresponse;

import java.util.List;

public record ResultResponse(int number, String explanation, List<Integer> referencedPages) {}
