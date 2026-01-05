package com.icc.qasker.quiz.dto.response;

import java.time.LocalDateTime;
import java.util.List;

public record UpdateLogResponse(List<updateLog> updateLogs) {

    public record updateLog(LocalDateTime dateTime, String updateText) {

    }
}
