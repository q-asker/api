package com.icc.qasker.aws.service;

import com.icc.qasker.aws.properties.AwsS3Properties;
import com.icc.qasker.aws.properties.LibreOfficeProperties;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

@Slf4j
@Service
@RequiredArgsConstructor
public class ConvertService {

  private static final String TEMP_DIR = System.getProperty("java.io.tmpdir") + File.separator;

  private final S3Client s3Client;
  private final AwsS3Properties s3Properties;
  private final LibreOfficeProperties officeProperties;

  /**
   * S3에서 파일을 다운로드하여 PDF로 변환 후 다시 업로드한다.
   *
   * @param key S3 오브젝트 키
   * @return 변환된 PDF의 S3 키
   */
  public String convertToPdf(String key) {
    String bucket = s3Properties.bucketName();

    // 이미 PDF 파일이면 변환하지 않음
    if (key.toLowerCase().endsWith(".pdf")) {
      log.info("이미 PDF 파일이므로 변환을 건너뜁니다: {}", key);
      return key;
    }

    String fileName = new File(key).getName();
    String downloadPath = TEMP_DIR + fileName;
    String pdfFileName = fileName.substring(0, fileName.lastIndexOf(".")) + ".pdf";
    String uploadKey = key.substring(0, key.lastIndexOf("/") + 1) + pdfFileName;

    try {
      // 1. S3에서 파일 다운로드
      log.info("S3에서 파일 다운로드: bucket={}, key={}", bucket, key);
      s3Client.getObject(
          GetObjectRequest.builder().bucket(bucket).key(key).build(), Path.of(downloadPath));

      // 2. 리브레오피스로 PDF 변환
      log.info("PDF 변환 시작: {}", downloadPath);
      executeLibreOfficeConversion(downloadPath);

      // 3. 변환된 PDF를 S3에 업로드
      File pdfFile = new File(TEMP_DIR + pdfFileName);
      if (!pdfFile.exists()) {
        throw new RuntimeException("PDF 변환 실패: 출력 파일을 찾을 수 없습니다.");
      }

      log.info("변환된 PDF를 S3에 업로드: {}", uploadKey);
      s3Client.putObject(
          PutObjectRequest.builder().bucket(bucket).key(uploadKey).build(),
          RequestBody.fromFile(pdfFile));

      return uploadKey;
    } catch (Exception e) {
      log.error("PDF 변환 중 오류 발생: key={}", key, e);
      throw new RuntimeException("PDF 변환 실패: " + e.getMessage(), e);
    } finally {
      // 임시 파일 정리
      cleanup(downloadPath, TEMP_DIR + pdfFileName);
    }
  }

  // 리브레오피스 프로세스 실행
  private void executeLibreOfficeConversion(String inputPath)
      throws IOException, InterruptedException {
    ProcessBuilder pb =
        new ProcessBuilder(
            officeProperties.officeHome() + File.separator + "soffice",
            "--headless",
            "--invisible",
            "--nodefault",
            "--nolockcheck",
            "--nologo",
            "--norestore",
            "--convert-to",
            "pdf",
            "--outdir",
            TEMP_DIR,
            inputPath);

    Map<String, String> env = pb.environment();
    env.put("HOME", TEMP_DIR);

    Process process = pb.start();
    int exitCode = process.waitFor();

    if (exitCode != 0) {
      throw new RuntimeException("리브레오피스 변환 실패 (exit code: " + exitCode + ")");
    }
  }

  // 임시 파일 정리
  private void cleanup(String... paths) {
    for (String path : paths) {
      File file = new File(path);
      if (file.exists() && !file.delete()) {
        log.warn("임시 파일 삭제 실패: {}", path);
      }
    }
  }
}
