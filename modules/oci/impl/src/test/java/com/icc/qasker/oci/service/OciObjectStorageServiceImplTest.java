package com.icc.qasker.oci.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.icc.qasker.oci.properties.AwsCloudFrontProperties;
import com.icc.qasker.oci.properties.OciObjectStorageProperties;
import com.oracle.bmc.objectstorage.transfer.UploadManager;
import com.oracle.bmc.objectstorage.transfer.UploadManager.UploadRequest;
import com.oracle.bmc.objectstorage.transfer.UploadManager.UploadResponse;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class OciObjectStorageServiceImplTest {

  private OciObjectStorageServiceImpl service;
  private UploadManager uploadManager;

  @BeforeEach
  void setUp() {
    uploadManager = mock(UploadManager.class);

    OciObjectStorageProperties ociProperties =
        new OciObjectStorageProperties(
            "test-namespace", "test-bucket", "ap-chuncheon-1", "~/.oci/config", "DEFAULT");

    AwsCloudFrontProperties cloudFrontProperties =
        new AwsCloudFrontProperties("https://files.test.com");

    service =
        new OciObjectStorageServiceImpl(
            cloudFrontProperties, ociProperties, uploadManager, new SimpleMeterRegistry());
  }

  @Test
  @DisplayName("PDF 업로드 시 OCI에 저장하고 CloudFront URL을 반환한다")
  void uploadPdf_pdfFile_returnsCloudFrontUrl() throws IOException {
    // Given: 임시 PDF 파일을 생성한다
    Path pdfFile = Files.createTempFile("test", ".pdf");
    Files.writeString(pdfFile, "dummy pdf content");

    // UploadManager.upload 호출 시 성공 응답을 반환하도록 모킹한다
    when(uploadManager.upload(any(UploadRequest.class))).thenReturn(mock(UploadResponse.class));

    try {
      // When: PDF 파일 업로드를 실행한다
      String cloudFrontUrl = service.uploadPdf(pdfFile, "document.pdf");

      // Then: CloudFront 도메인으로 시작하고 .pdf로 끝나야 한다
      assertThat(cloudFrontUrl).startsWith("https://files.test.com/");
      assertThat(cloudFrontUrl).endsWith(".pdf");

      // Then: UploadManager.upload()이 호출되었는지 검증한다
      verify(uploadManager).upload(any(UploadRequest.class));
    } finally {
      Files.deleteIfExists(pdfFile);
    }
  }
}
