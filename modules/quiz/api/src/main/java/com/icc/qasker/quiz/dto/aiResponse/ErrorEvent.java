package com.icc.qasker.quiz.dto.aiResponse;


public record ErrorEvent(
    String status,
    String message,
    int code
) implements StreamEvent {

}