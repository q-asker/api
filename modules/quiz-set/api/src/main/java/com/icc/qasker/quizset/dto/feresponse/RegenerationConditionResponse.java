package com.icc.qasker.quizset.dto.feresponse;

import com.icc.qasker.quizset.dto.ferequest.enums.QuizType;
import java.util.List;

/**
 * 세트의 생성 조건을 되돌려주는 응답(동일 재현용). 즉시 재생성 vs 옵션 화면 폴백의 판정은 프론트가 {@code documentAvailable &&
 * pageNumbers?.length && language}로 수행한다(서버 집계 플래그를 두지 않아 계약이 얇다).
 *
 * <p>{@code documentAvailable}은 문서가 지금 유효한지의 서버 소유 판정 자리다. 문서 만료 능동검사는 이번 스코프에서 미도입이라(후속 과제) 현 단계
 * 항상 true. legacy 세트(pageNumbers·language 미저장)는 두 값이 null로 내려가 프론트 폴백으로 유도된다.
 */
public record RegenerationConditionResponse(
    QuizType quizType,
    Integer quizCount,
    List<Integer> pageNumbers,
    String language,
    String customInstruction,
    String uploadedUrl,
    String title,
    boolean documentAvailable) {}
