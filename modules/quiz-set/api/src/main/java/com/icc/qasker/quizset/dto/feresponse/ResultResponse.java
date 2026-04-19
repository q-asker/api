package com.icc.qasker.quizset.dto.feresponse;

import java.util.List;

public record ResultResponse(int number, String explanation, List<Integer> referencedPages) {}
