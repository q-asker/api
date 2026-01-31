package com.icc.qasker.quiz.dto.aiResponse;


import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class ErrorEvent implements StreamEvent {

    String status;
    String message;
    int code;
}