package com.icc.qasker.quiz.service;

import com.icc.qasker.quiz.dto.response.UpdateLogResponse;
import com.icc.qasker.quiz.mapper.UpdateLogResponseMapper;
import com.icc.qasker.quiz.repository.UpdateLogRepository;
import lombok.AllArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

@Service
@AllArgsConstructor
public class UpdateLogService {

    private final UpdateLogRepository updateLogRepository;

    @Cacheable(value = "recentUpdates")
    public UpdateLogResponse getUpdate() {
        return UpdateLogResponseMapper.fromEntity(
            updateLogRepository.findTop3ByOrderByDateTimeDesc());
    }
}
