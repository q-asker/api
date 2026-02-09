package com.icc.qasker.aws;

public interface S3ValidateService {

    void validateFileWithThrowing(String fileName, long fileSize, String contentType);
}
