package com.icc.qasker.ai.dto;

/**
 * 재생성 원본(v1) 부착 정보. 학습자에게 노출되지 않는 미달 원본(v1)을 품질 로그의 해당 문항 행(v2)에 부착해 피드백 루프 전후 비교를 남긴다. 현재본(v2)은
 * problem(서빙) + 품질 로그로 이미 저장되므로 여기엔 v1만 담는다.
 *
 * @param number v2에 부여된 세트 내 번호
 * @param v1Json 미달 원본(v1)의 JSON 직렬화
 * @param v1Feedback v1에 대한 게이트 미달 사유
 */
public record RegenerationRecord(int number, String v1Json, String v1Feedback) {}
