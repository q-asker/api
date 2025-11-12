package com.icc.qasker.aws;

import com.icc.qasker.aws.dto.S3UploadRequest;
import com.icc.qasker.aws.dto.S3UploadResponse;

public interface S3Service {

    S3UploadResponse uploadFile(S3UploadRequest s3UploadRequest);
}

