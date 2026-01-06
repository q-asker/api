package com.icc.qasker.quiz.controller;

import com.icc.qasker.quiz.dto.request.UpdateLogRequest;
import com.icc.qasker.quiz.dto.response.UpdateLogResponse;
import com.icc.qasker.quiz.service.UpdateLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/updateLog")
public class UpdateLogController {

    private final UpdateLogService updateService;

    @GetMapping
    public ResponseEntity<UpdateLogResponse> getUpdateLog() {
        return ResponseEntity.ok(updateService.getUpdateLog());
    }

    @PostMapping
    public ResponseEntity<?> createUpdateLog(@RequestBody UpdateLogRequest request) {
        updateService.createUpdateLog(request);
        return ResponseEntity.ok().build();
    }
}
