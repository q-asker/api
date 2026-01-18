package com.icc.qasker.aws.service;

import com.icc.qasker.aws.S3ValidateService;
import com.icc.qasker.aws.properties.AwsCloudFrontProperties;
import com.icc.qasker.aws.properties.AwsS3Properties;
import com.icc.qasker.global.error.CustomException;
import com.icc.qasker.global.error.ExceptionMessage;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@AllArgsConstructor
public class S3ValidateServiceImpl implements S3ValidateService {

    private final AwsCloudFrontProperties awsCloudFrontProperties;
    private final AwsS3Properties awsS3Properties;

    @Override
    public void checkCloudFrontUrlWithThrowing(String url) {
        if (!url.startsWith(awsCloudFrontProperties.baseUrl())) {
            throw new CustomException(ExceptionMessage.INVALID_URL_REQUEST);
        }
    }

    @Override
    public void validateFileWithThrowing(String fileName, String contentType) {
        int maxFileNameLength = awsS3Properties.maxFileNameLength();
        String allowedExtensions = awsS3Properties.allowedExtensions();

        if (fileName == null) {
            throw new CustomException(ExceptionMessage.FILE_NAME_NOT_EXIST);
        }
        if (fileName.length() > maxFileNameLength) {
            throw new CustomException(ExceptionMessage.FILE_NAME_TOO_LONG);
        }
        if (contentType == null) {
            throw new CustomException(ExceptionMessage.EXTENSION_NOT_EXIST);
        }
        if (!allowedExtensions.contains(contentType)) {
            throw new CustomException(ExceptionMessage.EXTENSION_INVALID);
        }
    }
}
