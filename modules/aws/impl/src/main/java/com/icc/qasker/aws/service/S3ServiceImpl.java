package com.icc.qasker.aws.service;

import com.icc.qasker.aws.ConvertService;
import com.icc.qasker.aws.S3Service;
import com.icc.qasker.aws.S3ValidateService;
import com.icc.qasker.aws.dto.PresignRequest;
import com.icc.qasker.aws.dto.PresignResponse;
import com.icc.qasker.aws.properties.AwsCloudFrontProperties;
import com.icc.qasker.aws.properties.AwsS3Properties;
import com.icc.qasker.global.error.CustomException;
import com.icc.qasker.global.error.ExceptionMessage;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.util.UriUtils;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
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
  ConvertService convertService;

  @Override
  public PresignResponse requestPresign(PresignRequest req) {
    String originalFileName = req.originalFileName();
    String contentType = req.contentType();
    String extension = getExtensionOf(originalFileName);
    long fileSize = req.fileSize();

    s3ValidateService.validateFileWithThrowing(originalFileName, fileSize, contentType);

    // PDF 전용 — 비PDF 파일은 POST /s3/upload-non-pdf를 사용해야 한다
    boolean isPdf = contentType.equals("application/pdf") && extension.equals(".pdf");
    if (!isPdf) {
      throw new CustomException(ExceptionMessage.EXTENSION_INVALID);
    }

    String uuid = UUID.randomUUID().toString();
    String uploadKey = uuid + extension;

    Map<String, String> metadata = new HashMap<>();
    metadata.put("original-filename", UriUtils.encode(originalFileName, StandardCharsets.UTF_8));

    PutObjectPresignRequest presignRequest =
        PutObjectPresignRequest.builder()
            .signatureDuration(Duration.ofSeconds(awsS3Properties.signatureDuration()))
            .putObjectRequest(
                r ->
                    r.bucket(awsS3Properties.bucketName())
                        .key(uploadKey)
                        .contentType(contentType)
                        .contentLength(fileSize)
                        .metadata(metadata))
            .build();

    String uploadUrl = s3Presigner.presignPutObject(presignRequest).url().toString();
    String finalUrl = awsCloudFrontProperties.baseUrl() + "/" + uploadKey;
    return new PresignResponse(uploadUrl, finalUrl, true);
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

  @Override
  public PresignResponse uploadNonPdfFile(MultipartFile file) {
    String originalFileName = file.getOriginalFilename();
    String contentType = file.getContentType();
    long fileSize = file.getSize();

    s3ValidateService.validateFileWithThrowing(originalFileName, fileSize, contentType);

    Path tempFile = null;
    Path pdfFile = null;

    try {
      // 1. 임시 디렉토리에 파일 저장
      String extension = getExtensionOf(originalFileName);
      String uuid = UUID.randomUUID().toString();
      tempFile = Files.createTempFile(uuid, extension);
      file.transferTo(tempFile.toFile());

      // 2. PDF 변환
      log.info("비PDF 파일 변환 시작: {}", originalFileName);
      pdfFile = convertService.convertToPdf(tempFile);

      // 3. 변환된 PDF를 S3에 업로드
      String s3Key = uuid + ".pdf";
      Map<String, String> metadata = new HashMap<>();
      metadata.put("original-filename", UriUtils.encode(originalFileName, StandardCharsets.UTF_8));

      s3Client.putObject(
          PutObjectRequest.builder()
              .bucket(awsS3Properties.bucketName())
              .key(s3Key)
              .contentType("application/pdf")
              .metadata(metadata)
              .build(),
          RequestBody.fromFile(pdfFile));

      // 4. CloudFront URL 생성
      String finalUrl = awsCloudFrontProperties.baseUrl() + "/" + s3Key;
      log.info("비PDF 파일 변환 및 업로드 완료: {} -> {}", originalFileName, finalUrl);

      return new PresignResponse(null, finalUrl, true);
    } catch (CustomException e) {
      throw e;
    } catch (Exception e) {
      log.error("비PDF 파일 업로드 중 오류 발생: {}", originalFileName, e);
      throw new CustomException(ExceptionMessage.CONVERT_FAILED);
    } finally {
      // 5. 임시 파일 정리
      deleteIfExists(tempFile);
      deleteIfExists(pdfFile);
    }
  }

  private void deleteIfExists(Path path) {
    if (path != null) {
      try {
        Files.deleteIfExists(path);
      } catch (Exception e) {
        log.warn("임시 파일 삭제 실패: {}", path, e);
      }
    }
  }
}
