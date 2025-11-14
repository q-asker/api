package com.icc.qasker.aws.service;

import com.icc.qasker.aws.S3ValidateService;
import com.icc.qasker.aws.properties.AwsCloudFrontProperties;
import com.icc.qasker.aws.properties.AwsS3Properties;
import com.icc.qasker.global.error.CustomException;
import com.icc.qasker.global.error.ExceptionMessage;
import java.net.HttpURLConnection;
import java.net.URL;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
@AllArgsConstructor
public class S3ValidateServiceImpl implements S3ValidateService {

    private AwsCloudFrontProperties awsCloudFrontProperties;
    private AwsS3Properties awsS3Properties;

    @Override
    public void checkCloudFrontUrlWithThrowing(String url) {
        if (!url.startsWith(awsCloudFrontProperties.baseUrl())) {
            throw new CustomException(ExceptionMessage.INVALID_URL_REQUEST);
        }
    }

    @Override
    public void validateFileWithThrowing(MultipartFile multipartFile) {
        String fileName = multipartFile.getOriginalFilename();
        String contentType = multipartFile.getContentType();
        int maxFileNameLength = awsS3Properties.maxFileNameLength();
        String allowedExtensions = awsS3Properties.allowedExtensions();

        if (multipartFile.isEmpty()) {
            throw new CustomException(ExceptionMessage.NO_FILE_UPLOADED);
        }
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

    @Override
    public void validateS3Bucket(String uploadedUrl) {
        try {
            URL url = new URL(uploadedUrl);
            URL encodedUrl = new URL(url.getProtocol(), url.getHost(), url.getPort(),
                url.getPath());
            HttpURLConnection connection = (HttpURLConnection) encodedUrl.openConnection();
            if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                throw new CustomException(ExceptionMessage.FILE_NOT_FOUND_ON_S3);
            }
        } catch (Exception e) {
            throw new CustomException(ExceptionMessage.FILE_NOT_FOUND_ON_S3);
        }
    }
}
