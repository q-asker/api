package com.icc.qasker.aws.service;

import com.icc.qasker.aws.S3Service;
import com.icc.qasker.aws.dto.PresignRequest;
import com.icc.qasker.aws.dto.PresignResponse;
import com.icc.qasker.aws.properties.AwsCloudFrontProperties;
import com.icc.qasker.aws.properties.AwsS3Properties;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriUtils;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

@Slf4j
@Service
@AllArgsConstructor
public class S3ServiceImpl implements S3Service {

    AwsCloudFrontProperties awsCloudFrontProperties;
    AwsS3Properties awsS3Properties;
    S3Presigner s3Presigner;

    @Override
    public PresignResponse requestPresign(PresignRequest req) {

        String extension = "";
        String originalFileName = req.originalFileName();
        int lastDotIndex = originalFileName.lastIndexOf(".");
        if (lastDotIndex > -1) {
            extension = originalFileName.substring(lastDotIndex);
        }

        boolean isPdf = req.contentType().equals("application/pdf") && extension.equals(".pdf");

        String uploadKey = UUID.randomUUID().toString() + extension;

        String encodedFileName = UriUtils.encode(originalFileName, StandardCharsets.UTF_8);

        Map<String, String> metadata = new HashMap<>();
        metadata.put("original-filename", encodedFileName);

        PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
            .signatureDuration(Duration.ofSeconds(awsS3Properties.signatureDuration()))
            .putObjectRequest(r -> r
                .bucket(awsS3Properties.bucketName())
                .key(uploadKey)
                .contentType(req.contentType())
                .contentLength(req.fileSize())
                .metadata(metadata))
            .build();

        String uploadUrl = s3Presigner.presignPutObject(presignRequest).url().toString();
        String finalUrl = awsCloudFrontProperties.baseUrl() + "/" + uploadKey;

        return new PresignResponse(uploadUrl, finalUrl, isPdf);
    }
}

