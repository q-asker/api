package com.icc.qasker.quiz.dto.response;

import java.time.Instant;
import java.util.List;

public record UpdateLogResponse(List<updateLog> updateLogs) {

    public record updateLog(Instant dateTime, String updateText) {

    }
}
