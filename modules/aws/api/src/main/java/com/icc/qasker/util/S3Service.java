package com.icc.qasker.util;

import com.icc.qasker.util.dto.FileExistStatusResponse;
import com.icc.qasker.util.dto.PresignRequest;
import com.icc.qasker.util.dto.PresignResponse;

public interface S3Service {

    PresignResponse requestPresign(PresignRequest presignRequest);

    FileExistStatusResponse checkFileExistence(String originalFileName);
}

