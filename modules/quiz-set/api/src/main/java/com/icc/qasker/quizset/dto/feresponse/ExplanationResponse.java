package com.icc.qasker.quizset.dto.feresponse;

import com.icc.qasker.quizset.GenerationStatus;
import java.util.List;

/**
 * 세트 전체 해설 응답. 아직 진행 중(GENERATING)인데 해설이 없는 문항은 "해설 준비 중"으로, 생성이 종결(COMPLETED·FAILED)됐는데도 해설이 없는
 * 문항은 "해설 없음"으로 표기된다.
 */
public record ExplanationResponse(
    List<ResultResponse> results, String fileUrl, GenerationStatus generationStatus) {}
