package com.icc.qasker.quiz.util;

import com.icc.qasker.global.error.CustomException;
import com.icc.qasker.global.error.ExceptionMessage;
import com.icc.qasker.quiz.dto.request.AnswerRequest;
import com.icc.qasker.quiz.entity.Problem;

public class ExplanationValidator {

    public static void checkOf(Problem problem, AnswerRequest answer) {
        if (problem == null) {
            throw new CustomException(ExceptionMessage.PROBLEM_NOT_FOUND);
        }
        if (problem.getCorrectAnswer() == null) {
            throw new CustomException(ExceptionMessage.INVALID_CORRECT_ANSWER);
        }
        if (answer.getUserAnswer() == null) {
            throw new CustomException(ExceptionMessage.NULL_ANSWER_INPUT);
        }
    }
}
