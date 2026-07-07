package com.icc.qasker.quizset.dto;

import com.icc.qasker.quizset.dto.airesponse.RationaleOfAI;

/**
 * 문항 품질 upsert 입력. problem은 순수 서빙만 책임지므로 생성 근거(rationale)·품질 판정(qualityStatus/feedback)은 이 로그
 * (problem_quality_log)에 문항 1:1로 저장된다. 재생성 원본(v1)은 {@code attachRegenerationSource}로 별도 부착한다.
 *
 * @param qualityStatus 품질 상태 이름(OK/BELOW_THRESHOLD/UNVERIFIABLE)
 * @param feedback 미달 사유(통과 시 null)
 */
public record QualityLogEntry(
    Long problemSetId,
    int number,
    RationaleOfAI rationale,
    String qualityStatus,
    String feedback) {}
