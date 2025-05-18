package com.slb.qasker.aws.service;

import com.slb.qasker.aws.dto.S3UploadRequest;
import com.slb.qasker.aws.dto.S3UploadResponse;
import com.slb.qasker.aws.util.FileUploadValidator;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

@Service
public class S3Service {

    @Value("${aws.s3.bucket-name}")
    private String bucketName;
    @Value("${aws.cloudfront.base-url}")
    private String cloudFrontBaseUrl;

    private final FileUploadValidator fileUploadValidator;
    private final S3Client s3Client;

    public S3Service(FileUploadValidator fileUploadValidator, S3Client s3Client) {
        this.fileUploadValidator = fileUploadValidator;
        this.s3Client = s3Client;
    }

    public S3UploadResponse uploadFile(S3UploadRequest s3UploadRequest) {
        MultipartFile multipartFile = s3UploadRequest.getMultipartFile();
        fileUploadValidator.checkOf(multipartFile);

        String timestamp = LocalDateTime.now()
            .format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmssSSS"));
        String keyName = timestamp + "_" + multipartFile.getOriginalFilename();

        PutObjectRequest putObjectRequest =
            PutObjectRequest
                .builder()
                .bucket(bucketName)
                .key(keyName)
                .build();

        try {
            s3Client.putObject(putObjectRequest,
                RequestBody.fromInputStream(multipartFile.getInputStream(),
                    multipartFile.getSize()));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        return S3UploadResponse.builder().url(cloudFrontBaseUrl + "/" + keyName).build();
    }
}
