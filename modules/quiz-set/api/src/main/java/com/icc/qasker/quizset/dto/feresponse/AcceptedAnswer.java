package com.icc.qasker.quizset.dto.feresponse;

import java.util.List;

/**
 * REAL_BLANK 관용 채점용 허용답안(문항 레벨, 빈칸 단위) 응답 DTO. {@code answer}는 빈칸 모범답안, {@code accepted}는 허용변형
 * (동의어·이표기·약어·영↔한 표기)이다. 응시 조회·히스토리 상세 응답에서 공용으로 쓰인다. 비REAL_BLANK·이 기능 이전 생성분은 상위 문항 필드가 null이다.
 */
public record AcceptedAnswer(String answer, List<String> accepted) {}
