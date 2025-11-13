package com.icc.qasker.aws.service;

import com.icc.qasker.aws.S3Service;
import com.icc.qasker.aws.S3ValidateService;
import com.icc.qasker.aws.dto.S3UploadRequest;
import com.icc.qasker.aws.dto.S3UploadResponse;
import com.icc.qasker.aws.properties.AwsCloudFrontProperties;
import com.icc.qasker.aws.properties.AwsS3Properties;
import com.icc.qasker.aws.properties.LibreOfficeProperties;
import com.icc.qasker.global.error.CustomException;
import com.icc.qasker.global.error.ExceptionMessage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jodconverter.core.office.OfficeException;
import org.jodconverter.core.office.OfficeManager;
import org.jodconverter.core.office.OfficeUtils;
import org.jodconverter.local.JodConverter;
import org.jodconverter.local.office.LocalOfficeManager;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

@Slf4j
@Service
@AllArgsConstructor
public class S3ServiceImpl implements S3Service {

    private final AwsCloudFrontProperties cloudFrontProperties;
    private final S3ValidateService s3ValidateService;
    private final LibreOfficeProperties libreOfficeProperties;
    private final AwsS3Properties awsS3Properties;
    private final S3Client s3Client;

    @Override
    public S3UploadResponse uploadFile(S3UploadRequest s3UploadRequest) {
        MultipartFile multipartFile = s3UploadRequest.getFile();
        s3ValidateService.validateFileWithThrowing(multipartFile);
        String cloudfrontBaseUrl = cloudFrontProperties.baseUrl();

        String fileName = encodePath(multipartFile.getOriginalFilename());
        String timestamp = LocalDateTime.now()
            .format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmssSSS"));

        if (fileName.endsWith(".pdf")) {
            String keyName = timestamp + "_" + fileName;
            RequestBody requestBody = getRequestBody(multipartFile);
            handleUpload(keyName, requestBody);
            return S3UploadResponse.builder()
                .uploadedUrl(cloudFrontProperties.baseUrl() + "/" + encodePath(keyName))
                .build();
        }

        File convertedFile = convertToPDF(multipartFile);
        String keyName =
            timestamp + "_" + StringUtils.stripFilenameExtension(fileName) + ".pdf";
        RequestBody requestBody = RequestBody.fromFile(convertedFile);
        handleUpload(keyName, requestBody);
        return
            S3UploadResponse.builder()
                .uploadedUrl(cloudfrontBaseUrl + "/" + encodePath(keyName))
                .build();
    }

    private void handleUpload(String keyName, RequestBody requestBody) {
        PutObjectRequest putObjectRequest =
            PutObjectRequest
                .builder()
                .bucket(awsS3Properties.bucketName())
                .key(keyName)
                .build();

        s3Client.putObject(putObjectRequest, requestBody);
    }

    private RequestBody getRequestBody(MultipartFile multipartFile) {
        try (InputStream inputStream = multipartFile.getInputStream()) {
            byte[] fileBytes = inputStream.readAllBytes();
            return RequestBody.fromBytes(fileBytes);
        } catch (IOException e) {
            throw new CustomException(
                ExceptionMessage.NO_FILE_UPLOADED);
        }
    }

    private String encodePath(String path) {
        return URLEncoder.encode(path, StandardCharsets.UTF_8);
    }

    private File convertToPDF(MultipartFile target) {
        OfficeManager officeManager = null;
        File inputFile = null;

        try {
            inputFile = Files.createTempFile("upload-", "." + getExtension(target)).toFile();
            target.transferTo(inputFile);
            File outputFile = Files.createTempFile("converted-", ".pdf").toFile();
            officeManager = LocalOfficeManager.builder()
                .officeHome(libreOfficeProperties.officeHome())
                .portNumbers(libreOfficeProperties.port())
                .install()
                .build();
            officeManager.start();
            JodConverter
                .convert(inputFile)
                .to(outputFile)
                .execute();

            return outputFile;
        } catch (OfficeException e) {
            log.error("Error during office conversion: {}", e.getMessage());
            throw new CustomException(ExceptionMessage.NO_FILE_UPLOADED);
        } catch (IOException e) {
            log.error("Error during file handling: {}", e.getMessage());
            throw new CustomException(ExceptionMessage.NO_FILE_UPLOADED);
        } finally {
            OfficeUtils.stopQuietly(officeManager);
            if (inputFile != null && !inputFile.delete()) {
                log.warn("Failed to delete temporary input file: {}", inputFile.getAbsolutePath());
            }
        }
    }

    private String getExtension(MultipartFile multipartFile) {
        String originalFilename = multipartFile.getOriginalFilename();
        return StringUtils.getFilenameExtension(originalFilename);
    }
}

