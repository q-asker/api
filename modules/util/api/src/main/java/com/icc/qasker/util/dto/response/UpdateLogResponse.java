package com.icc.qasker.util.dto.response;

import java.time.Instant;
import java.util.List;

public record UpdateLogResponse(List<UpdateLog> updateLogs) {

    public record UpdateLog(Instant dateTime, String updateText) {

    }
}
