package com.icc.qasker.aws;

import org.springframework.web.multipart.MultipartFile;

public interface S3ValidateService {

    void checkCloudFrontUrlWithThrowing(String url);

    void validateFileWithThrowing(MultipartFile multipartFile);
}
