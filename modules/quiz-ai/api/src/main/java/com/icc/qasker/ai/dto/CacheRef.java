package com.icc.qasker.ai.dto;

/**
 * 생성된 Vertex 컨텍스트 캐시 참조(리소스 이름 + 생성 모델). 추론 요청은 반드시 동일 모델을 써야 한다(불일치 시 Vertex 400). 생성 캐시와 Pass 1
 * 검증 캐시가 공통으로 사용하는 api 계약 타입이다.
 */
public record CacheRef(String name, String model) {}
