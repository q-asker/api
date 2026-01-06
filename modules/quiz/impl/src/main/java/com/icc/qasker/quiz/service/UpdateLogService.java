package com.icc.qasker.quiz.service;

import com.icc.qasker.quiz.dto.request.UpdateLogRequest;
import com.icc.qasker.quiz.dto.response.UpdateLogResponse;
import com.icc.qasker.quiz.entity.UpdateLog;
import com.icc.qasker.quiz.mapper.UpdateLogResponseMapper;
import com.icc.qasker.quiz.repository.UpdateLogRepository;
import lombok.AllArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@AllArgsConstructor
public class UpdateLogService {

    private final UpdateLogRepository updateLogRepository;

    @Cacheable(value = "recentUpdateLog")
    @Transactional(readOnly = true)
    public UpdateLogResponse getUpdateLog() {
        return UpdateLogResponseMapper.fromEntity(
            updateLogRepository.findTop3ByOrderByCreatedAtDesc());
    }

    @Transactional
    @CacheEvict(value = "recentUpdateLog", allEntries = true)
    public void createUpdateLog(UpdateLogRequest request) {
        updateLogRepository.save(UpdateLog.builder()
            .updateText(request.updateText())
            .build());
    }
}
