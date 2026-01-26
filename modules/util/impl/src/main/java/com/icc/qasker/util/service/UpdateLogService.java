package com.icc.qasker.util.service;


import com.icc.qasker.util.dto.request.UpdateLogRequest;
import com.icc.qasker.util.dto.response.UpdateLogResponse;
import com.icc.qasker.util.entity.UpdateLog;
import com.icc.qasker.util.mapper.UpdateLogResponseMapper;
import com.icc.qasker.util.repository.UpdateLogRepository;
import lombok.AllArgsConstructor;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@AllArgsConstructor
public class UpdateLogService {

    private final UpdateLogRepository updateLogRepository;

    @Cacheable(value = "recentUpdateLog", key = "'root'")
    @Transactional(readOnly = true)
    public UpdateLogResponse getUpdateLog() {
        return UpdateLogResponseMapper.fromEntity(
            updateLogRepository.findTop3ByOrderByCreatedAtDesc());
    }

    @Transactional
    @CachePut(value = "recentUpdateLog", key = "'root'")
    public UpdateLogResponse createUpdateLog(
        UpdateLogRequest request) {
        updateLogRepository.save(UpdateLog.builder()
            .updateText(request.updateText())
            .build());
        return UpdateLogResponseMapper.fromEntity(
            updateLogRepository.findTop3ByOrderByCreatedAtDesc());
    }
}
