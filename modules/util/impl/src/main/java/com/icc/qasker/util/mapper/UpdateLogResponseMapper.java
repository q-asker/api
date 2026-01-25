package com.icc.qasker.util.mapper;


import com.icc.qasker.util.dto.response.UpdateLogResponse;
import com.icc.qasker.util.entity.UpdateLog;
import java.util.List;

public final class UpdateLogResponseMapper {

    public static UpdateLogResponse fromEntity(List<UpdateLog> entities) {
        return new UpdateLogResponse(
            entities.stream()
                .map(entity -> new UpdateLogResponse.UpdateLog(
                    entity.getCreatedAt(),
                    entity.getUpdateText()
                ))
                .toList()
        );
    }
}
