package com.icc.qasker.aws;

import com.icc.qasker.aws.dto.PresignRequest;
import com.icc.qasker.aws.dto.PresignResponse;

public interface S3Service {

    PresignResponse requestPresign(PresignRequest presignRequest);
}

