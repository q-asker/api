package com.icc.qasker.aws.doc;


import com.icc.qasker.aws.dto.request.UpdateLogRequest;
import com.icc.qasker.aws.dto.response.UpdateLogResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@Tag(name = "UpdateLog", description = "최신 변경사항 관련 API")
public interface UpdateLogApiDocs {

    @Operation(summary = "변경사항 업데이트를 가져온다")
    @GetMapping
    ResponseEntity<UpdateLogResponse> getUpdateLog();

    @Operation(summary = "변경사항 업데이트를 보낸다")
    @PostMapping
    ResponseEntity<UpdateLogResponse> createUpdateLog(@RequestBody UpdateLogRequest request);
}
