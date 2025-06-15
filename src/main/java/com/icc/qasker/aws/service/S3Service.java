package com.icc.qasker.aws.service;

import com.icc.qasker.aws.dto.S3UploadRequest;
import com.icc.qasker.aws.dto.S3UploadResponse;
import com.icc.qasker.aws.util.FileUploadValidator;
import com.icc.qasker.aws.util.PPTtoPDFConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Service
public class S3Service {

    @Value("${aws.s3.bucket-name}")
    private String bucketName;
    @Value("${aws.cloudfront.base-url}")
    private String cloudFrontBaseUrl;

    private final PPTtoPDFConverter ppTtoPDFConverter;
    private final FileUploadValidator fileUploadValidator;
    private final S3Client s3Client;

    public S3Service(FileUploadValidator fileUploadValidator, S3Client s3Client, PPTtoPDFConverter ppTtoPDFConverter) {
        this.fileUploadValidator = fileUploadValidator;
        this.s3Client = s3Client;
        this.ppTtoPDFConverter = ppTtoPDFConverter;
    }

    public S3UploadResponse uploadFile(S3UploadRequest s3UploadRequest) {
        MultipartFile multipartFile = s3UploadRequest.getFile();
        fileUploadValidator.checkOf(multipartFile);

        String timestamp = LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmssSSS"));
        String keyName = timestamp + "_" + multipartFile.getOriginalFilename();
        if (keyName.endsWith(".pdf")) {
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
            return S3UploadResponse.builder().uploadedUrl(cloudFrontBaseUrl + "/" + keyName).build();
        }
        try {
            File convertedFile = ppTtoPDFConverter.convertPPTtoPDF(multipartFile);
            String pdfKyName = keyName.replace(".pptx", ".pdf");
            PutObjectRequest putObjectRequest =
                    PutObjectRequest
                            .builder()
                            .bucket(bucketName)
                            .key(pdfKyName)
                            .build();
            s3Client.putObject(putObjectRequest,
                    RequestBody.fromFile(convertedFile));
            return S3UploadResponse.builder().uploadedUrl(cloudFrontBaseUrl + "/" + pdfKyName).build();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
