package com.icc.qasker.aws;

public interface S3ValidateService {

    void checkCloudFrontUrlWithThrowing(String url);


    void validateFileWithThrowing(String fileName, long fileSize, String contentType);
}
