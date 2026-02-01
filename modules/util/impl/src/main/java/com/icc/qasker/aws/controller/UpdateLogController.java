package com.icc.qasker.aws.controller;

import com.icc.qasker.aws.doc.UpdateLogApiDocs;
import com.icc.qasker.aws.dto.request.UpdateLogRequest;
import com.icc.qasker.aws.dto.response.UpdateLogResponse;
import com.icc.qasker.aws.service.UpdateLogService;
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
public class UpdateLogController implements UpdateLogApiDocs {

    private final UpdateLogService updateService;

    @GetMapping
    public ResponseEntity<UpdateLogResponse> getUpdateLog() {
        return ResponseEntity.ok(updateService.getUpdateLog());
    }


    @PostMapping
    public ResponseEntity<UpdateLogResponse> createUpdateLog(
        @RequestBody UpdateLogRequest request) {
        return ResponseEntity.ok(updateService.createUpdateLog(request));
    }
}
