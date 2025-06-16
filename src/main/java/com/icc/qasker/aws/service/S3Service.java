package com.icc.qasker.aws.service;

import com.icc.qasker.aws.dto.S3UploadRequest;
import com.icc.qasker.aws.dto.S3UploadResponse;
import com.icc.qasker.aws.util.FileUploadValidator;
import com.icc.qasker.aws.util.ToPDFConverter;
import com.icc.qasker.global.error.CustomException;
import com.icc.qasker.global.error.ExceptionMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Slf4j
@Service
public class S3Service {

    @Value("${aws.s3.bucket-name}")
    private String bucketName;
    @Value("${aws.cloudfront.base-url}")
    private String cloudFrontBaseUrl;

    private final ToPDFConverter toPDFConverter;
    private final FileUploadValidator fileUploadValidator;
    private final S3Client s3Client;

    public S3Service(FileUploadValidator fileUploadValidator, S3Client s3Client, ToPDFConverter toPDFConverter) {
        this.fileUploadValidator = fileUploadValidator;
        this.s3Client = s3Client;
        this.toPDFConverter = toPDFConverter;
    }

    public S3UploadResponse uploadFile(S3UploadRequest s3UploadRequest) {
        MultipartFile multipartFile = s3UploadRequest.getFile();
        fileUploadValidator.checkOf(multipartFile);

        String fileName = multipartFile.getOriginalFilename();
        String timestamp = LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmssSSS"));


        if (fileName.endsWith(".pdf")) {
            String keyName = timestamp + "_" + fileName;
            RequestBody requestBody = getRequestBody(multipartFile);
            handleUpload(keyName, requestBody);
            return S3UploadResponse.builder().uploadedUrl(cloudFrontBaseUrl + "/" + keyName).build();
        }

        File convertedFile = null;
        try {
            convertedFile = toPDFConverter.convert(multipartFile);
            String keyName = timestamp + "_" + StringUtils.stripFilenameExtension(fileName) + ".pdf";
            RequestBody requestBody = getRequestBody(convertedFile);
            handleUpload(keyName, requestBody);
            return S3UploadResponse.builder().uploadedUrl(cloudFrontBaseUrl + "/" + keyName).build();
        } catch (Exception e) {
            throw new CustomException(ExceptionMessage.NO_FILE_UPLOADED);
        } finally {
            if (convertedFile != null && !convertedFile.delete()) {
                log.warn("Failed to delete temporary input file: {}", convertedFile.getAbsolutePath());
            }
        }
    }

    private void handleUpload(String keyName, RequestBody requestBody) {
        PutObjectRequest putObjectRequest =
                PutObjectRequest
                        .builder()
                        .bucket(bucketName)
                        .key(keyName)
                        .build();

        s3Client.putObject(putObjectRequest, requestBody);
    }

    private RequestBody getRequestBody(MultipartFile multipartFile) {
        try {
            return RequestBody.fromInputStream(multipartFile.getInputStream(), multipartFile.getSize());
        } catch (IOException e) {
            throw new CustomException(ExceptionMessage.NO_FILE_UPLOADED);
        }
    }

    private RequestBody getRequestBody(File file) {
        return RequestBody.fromFile(file);
    }
}
