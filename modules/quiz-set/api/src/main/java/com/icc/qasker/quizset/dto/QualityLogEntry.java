package com.icc.qasker.quizset.dto;

/**
 * 문항 첫 생성본(v1) upsert 입력. problem은 순수 서빙만 책임지므로 생성 근거(질문·해설·미달 사유)는 이 로그(problem_quality_log)에 문항
 * 1:1로 저장된다. 재생성된 개선본(v2)은 {@code attachV2}로 별도 부착한다.
 *
 * @param v1QuestionJson 첫 생성본 질문(stem+선지)의 JSON 직렬화
 * @param v1Explanation 첫 생성본 해설 마크다운
 * @param v1Feedback 생성 게이트 미달 사유(통과 시 null)
 */
public record QualityLogEntry(
    Long problemSetId,
    int number,
    String v1QuestionJson,
    String v1Explanation,
    String v1Feedback) {}
