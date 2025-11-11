package com.icc.qasker;

import com.icc.qasker.dto.S3UploadRequest;
import com.icc.qasker.dto.S3UploadResponse;

public interface S3Service {
    
    S3UploadResponse uploadFile(S3UploadRequest s3UploadRequest);
}

