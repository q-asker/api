package com.icc.qasker.aws.controller;

import com.icc.qasker.aws.dto.request.UpdateLogRequest;
import com.icc.qasker.aws.dto.response.UpdateLogResponse;
import com.icc.qasker.aws.service.UpdateLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "UpdateLog", description = "최신 변경사항 관련 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/updateLog")
public class UpdateLogController {

    private final UpdateLogService updateService;

    @Operation(summary = "변경사항 업데이트를 가져온다")
    @GetMapping
    public ResponseEntity<UpdateLogResponse> getUpdateLog() {
        return ResponseEntity.ok(updateService.getUpdateLog());
    }

    @Operation(summary = "변경사항 업데이트를 보낸다")
    @PostMapping
    public ResponseEntity<UpdateLogResponse> createUpdateLog(
        @RequestBody UpdateLogRequest request) {
        return ResponseEntity.ok(updateService.createUpdateLog(request));
    }
}
