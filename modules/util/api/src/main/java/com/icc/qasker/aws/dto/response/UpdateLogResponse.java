package com.icc.qasker.aws.dto.response;

import java.time.Instant;
import java.util.List;

public record UpdateLogResponse(List<UpdateLog> updateLogs) {

    public record UpdateLog(Instant dateTime, String updateText) {

    }
}
