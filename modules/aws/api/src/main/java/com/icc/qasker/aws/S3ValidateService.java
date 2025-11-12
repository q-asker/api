package com.icc.qasker.aws;

public interface S3ValidateService {

    boolean isCloudFrontUrl(String url);

    void validateS3Bucket(String url);
}
