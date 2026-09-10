package com.icc.qasker.quizset.dto;

import com.icc.qasker.global.quiz.QuizType;
import com.icc.qasker.quizset.dto.readonly.ProblemLineage;
import java.util.List;

/**
 * 틀린 문항을 그대로 복제해 새 세트를 만들 때 필요한 것. {@code sources}는 복제할 원본 문항의 좌표이며, 담기는 순서가 새 세트의 문항 번호 순서가 된다.
 *
 * <p>{@code sessionId}는 호출자가 결정론적으로 만든다 — 컬럼이 UNIQUE라 같은 요청이 두 번 들어와도 두 번째는 제약 위반으로 걸러진다.
 */
public record WrongAnswerSetCreation(
    String userId,
    String sessionId,
    String title,
    QuizType quizType,
    Long sourceFolderId,
    List<ProblemLineage> sources) {}
