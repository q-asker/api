package com.icc.qasker.quizmake;

import com.icc.qasker.quizmake.dto.ferequest.GenerationRequest;

public interface GenerationCommandService {

  void triggerGeneration(String userId, GenerationRequest request);
}
