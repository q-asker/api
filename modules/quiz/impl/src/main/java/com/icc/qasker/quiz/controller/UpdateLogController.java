package com.icc.qasker.quiz.controller;

import com.icc.qasker.quiz.dto.response.UpdateLogResponse;
import com.icc.qasker.quiz.service.UpdateLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/update")
public class UpdateLogController {

    private final UpdateLogService updateService;

    @GetMapping
    public ResponseEntity<UpdateLogResponse> getUpdate() {
        return ResponseEntity.ok(updateService.getUpdate());
    }
}
