package com.icc.qasker.aws.service;

import com.icc.qasker.aws.S3Service;
import com.icc.qasker.aws.S3ValidateService;
import com.icc.qasker.aws.dto.FileExistStatusResponse;
import com.icc.qasker.aws.dto.PresignRequest;
import com.icc.qasker.aws.dto.PresignResponse;
import com.icc.qasker.aws.dto.Status;
import com.icc.qasker.aws.properties.AwsCloudFrontProperties;
import com.icc.qasker.aws.properties.AwsS3Properties;
import com.icc.qasker.global.error.CustomException;
import com.icc.qasker.global.error.ExceptionMessage;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriUtils;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

@Slf4j
@Service
@AllArgsConstructor
public class S3ServiceImpl implements S3Service {

    AwsCloudFrontProperties awsCloudFrontProperties;
    AwsS3Properties awsS3Properties;
    S3Presigner s3Presigner;
    S3Client s3Client;
    S3ValidateService s3ValidateService;

    @Override
    public FileExistStatusResponse checkFileExistence(String cloudfrontUrl) {
        String key = extractKeyFromUrl(cloudfrontUrl);

        try {
            s3Client.headObject(HeadObjectRequest.builder()
                .bucket(awsS3Properties.bucketName())
                .key(key)
                .build());

            return new FileExistStatusResponse(Status.EXIST);
        } catch (NoSuchKeyException e) {
            return new FileExistStatusResponse(Status.NOT_EXIST);
        } catch (Exception e) {
            log.error(e.getMessage());
            throw new CustomException(ExceptionMessage.NO_FILE_UPLOADED);
        }
    }

    private String changeExtensionToPdf(String fileName) {
        int lastDotIndex = fileName.lastIndexOf(".");
        if (lastDotIndex == -1) {
            return fileName + ".pdf";
        }
        return fileName.substring(0, lastDotIndex) + ".pdf";
    }

    @Override
    public PresignResponse requestPresign(PresignRequest req) {
        String originalFileName = req.originalFileName();
        String contentType = req.contentType();
        String extension = getExtensionOf(originalFileName);

        s3ValidateService.validateFileWithThrowing(originalFileName, contentType);
        boolean isPdf = contentType.equals("application/pdf") && extension.equals(".pdf");

        String uuid = UUID.randomUUID().toString();
        String uuidFileName = uuid + extension;
        String uploadKey = isPdf ? uuidFileName : "to-convert/" + uuidFileName;

        Map<String, String> metadata = new HashMap<>();
        metadata.put("original-filename",
            UriUtils.encode(originalFileName, StandardCharsets.UTF_8));

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
        String finalUrl =
            awsCloudFrontProperties.baseUrl() + "/" + changeExtensionToPdf(uuidFileName);
        return new PresignResponse(uploadUrl, finalUrl, isPdf);
    }

    private String getExtensionOf(String fileName) {
        String extension = null;
        int lastDotIndex = fileName.lastIndexOf(".");
        if (lastDotIndex > -1) {
            extension = fileName.substring(lastDotIndex);
        }
        if (extension == null) {
            throw new CustomException(ExceptionMessage.EXTENSION_NOT_EXIST);
        }
        return extension;
    }

    public String extractKeyFromUrl(String cloudFrontUrl) {
        try {
            URI uri = new URI(cloudFrontUrl);
            // "/uploads/test.pdf" 반환
            String path = uri.getPath();

            if (path.startsWith("/")) {
                return path.substring(1);
            }
            return path;
        } catch (Exception e) {
            log.error(e.getMessage());
            throw new CustomException(ExceptionMessage.NO_FILE_UPLOADED);
        }
    }
}

