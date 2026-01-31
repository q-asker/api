package com.icc.qasker.aws.mapper;


import com.icc.qasker.aws.dto.response.UpdateLogResponse;
import com.icc.qasker.aws.entity.UpdateLog;
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
