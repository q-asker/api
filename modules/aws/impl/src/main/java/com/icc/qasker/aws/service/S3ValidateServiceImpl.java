package com.icc.qasker.aws.service;

import com.icc.qasker.aws.S3ValidateService;
import com.icc.qasker.aws.util.FileUrlValidator;
import com.icc.qasker.global.error.CustomException;
import com.icc.qasker.global.error.ExceptionMessage;
import java.net.HttpURLConnection;
import java.net.URL;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@AllArgsConstructor
public class S3ValidateServiceImpl implements S3ValidateService {

    FileUrlValidator fileUrlValidator;

    @Override
    public boolean isCloudFrontUrl(String url) {
        return fileUrlValidator.isCloudFrontUrl(url);
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
