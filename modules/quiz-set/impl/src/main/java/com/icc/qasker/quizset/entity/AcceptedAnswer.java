package com.icc.qasker.quizset.entity;

import java.util.List;

/**
 * REAL_BLANK 관용 채점용 허용답안(문항 레벨, 빈칸 단위). {@code answer}는 그 빈칸의 모범답안(정답 selection content의 콤마 토큰과 동일
 * 순서), {@code accepted}는 그 빈칸에서 정답으로 인정하는 허용변형(동의어·이표기·약어·영↔한 표기)이다. 오답선지는 생성 후처리에서 제거되어 여기 섞이지
 * 않는다. 관용 판정은 클라이언트가 수행하며, 백엔드는 이 데이터를 생성·저장·전달만 한다.
 */
public record AcceptedAnswer(String answer, List<String> accepted) {}
