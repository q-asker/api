package com.icc.qasker.aws.service;

import com.icc.qasker.aws.dto.S3UploadRequest;
import com.icc.qasker.aws.dto.S3UploadResponse;
import com.icc.qasker.aws.util.FileUploadValidator;
import com.icc.qasker.aws.util.ToPDFConverter;
import com.icc.qasker.global.error.CustomException;
import com.icc.qasker.global.error.ExceptionMessage;
import com.icc.qasker.global.properties.AwsCloudFrontProperties;
import com.icc.qasker.global.properties.AwsS3Properties;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

@Slf4j
@Service
@AllArgsConstructor
public class S3Service {

    private final ToPDFConverter toPDFConverter;
    private final FileUploadValidator fileUploadValidator;
    private final AwsCloudFrontProperties cloudFrontProperties;
    private final AwsS3Properties awsS3Properties;
    private final S3Client s3Client;

    public S3UploadResponse uploadFile(S3UploadRequest s3UploadRequest) {
        MultipartFile multipartFile = s3UploadRequest.getFile();
        fileUploadValidator.checkOf(multipartFile);
        String cloudfrontBaseUrl = cloudFrontProperties.getBaseUrl();

        String fileName = encodePath(multipartFile.getOriginalFilename());
        String timestamp = LocalDateTime.now()
            .format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmssSSS"));

        if (fileName.endsWith(".pdf")) {
            String keyName = timestamp + "_" + fileName;
            RequestBody requestBody = getRequestBody(multipartFile);
            handleUpload(keyName, requestBody);
            return S3UploadResponse.builder()
                .uploadedUrl(cloudFrontProperties.getBaseUrl() + "/" + encodePath(keyName))
                .build();
        }

        File convertedFile = null;
        try {
            convertedFile = toPDFConverter.convert(multipartFile);
            String keyName =
                timestamp + "_" + StringUtils.stripFilenameExtension(fileName) + ".pdf";
            RequestBody requestBody = RequestBody.fromFile(convertedFile);
            handleUpload(keyName, requestBody);
            return
                S3UploadResponse.builder()
                    .uploadedUrl(cloudfrontBaseUrl + "/" + encodePath(keyName))
                    .build();
        } catch (Exception e) {
            throw new CustomException(ExceptionMessage.NO_FILE_UPLOADED);
        } finally {
            if (convertedFile != null && !convertedFile.delete()) {
                log.warn("Failed to delete temporary input file: {}",
                    convertedFile.getAbsolutePath());
            }
        }
    }

    private void handleUpload(String keyName, RequestBody requestBody) {
        PutObjectRequest putObjectRequest =
            PutObjectRequest
                .builder()
                .bucket(awsS3Properties.getBucketName())
                .key(keyName)
                .build();

        s3Client.putObject(putObjectRequest, requestBody);
    }

    private RequestBody getRequestBody(MultipartFile multipartFile) {
        try (InputStream inputStream = multipartFile.getInputStream()) {
            byte[] fileBytes = inputStream.readAllBytes();
            return RequestBody.fromBytes(fileBytes);
        } catch (IOException e) {
            throw new CustomException(ExceptionMessage.NO_FILE_UPLOADED);
        }
    }

    private String encodePath(String path) {
        return URLEncoder.encode(path, StandardCharsets.UTF_8);
    }
}
