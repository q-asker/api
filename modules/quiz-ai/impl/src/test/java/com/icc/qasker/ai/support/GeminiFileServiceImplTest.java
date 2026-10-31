package com.icc.qasker.ai.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import com.icc.qasker.ai.dto.GeminiFileUploadResponse.FileMetadata;
import com.icc.qasker.ai.properties.QAskerAiProperties;
import com.icc.qasker.global.error.CustomException;
import com.icc.qasker.global.error.ExceptionMessage;
import com.icc.qasker.oci.FileValidateService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class GeminiFileServiceImplTest {

  private static final String BASE = "https://files.q-asker.com";
  private static final String BUCKET = "test-bucket";

  private Storage storage;
  private GeminiFileServiceImpl service;

  @BeforeEach
  void setUp() {
    storage = mock(Storage.class);
    QAskerAiProperties props = new QAskerAiProperties();
    props.getGcs().setBucketName(BUCKET);
    service =
        new GeminiFileServiceImpl(
            storage, props, BASE, mock(FileValidateService.class), new SimpleMeterRegistry());
  }

  @Test
  void storePdfSavesUuidObjectAndReturnsCdnUrlOfSameName() throws Exception {
    Path pdf = Files.createTempFile("test", ".pdf");
    try {
      String cdnUrl = service.storePdf(pdf, "보고서.pdf");

      ArgumentCaptor<BlobInfo> captor = ArgumentCaptor.forClass(BlobInfo.class);
      verify(storage).createFrom(captor.capture(), eq(pdf));
      String objectName = captor.getValue().getName();
      assertThat(objectName).matches("[0-9a-f-]{36}\\.pdf");
      assertThat(captor.getValue().getBucket()).isEqualTo(BUCKET);
      // CDN 은 같은 객체명을 GCS 에서 읽어 내준다
      assertThat(cdnUrl).isEqualTo(BASE + "/" + objectName);
    } finally {
      Files.deleteIfExists(pdf);
    }
  }

  @Test
  void resolvePdfMapsCdnUrlToGcsUriWithoutReupload() throws Exception {
    Blob blob = mock(Blob.class);
    when(blob.getSize()).thenReturn(10L);
    when(storage.get(BlobId.of(BUCKET, "a.pdf"))).thenReturn(blob);

    FileMetadata meta = service.resolvePdf(BASE + "/a.pdf");

    assertThat(meta.uri()).isEqualTo("gs://" + BUCKET + "/a.pdf");
    verify(storage, org.mockito.Mockito.never()).createFrom(any(BlobInfo.class), any(Path.class));
  }

  @Test
  void resolvePdfOfDeletedObjectBecomesUploadedFileDeleted() {
    // 보관 기한(GCS 수명 주기)이 지나 객체가 없으면 storage.get 은 null 을 돌려준다
    when(storage.get(BlobId.of(BUCKET, "gone.pdf"))).thenReturn(null);

    assertThatThrownBy(() -> service.resolvePdf(BASE + "/gone.pdf"))
        .isInstanceOfSatisfying(
            CustomException.class,
            e -> assertThat(e.getCode()).isEqualTo(ExceptionMessage.UPLOADED_FILE_DELETED.name()));
  }

  @Test
  void resolvePdfRejectsNonFileCdnUrl() {
    assertThatThrownBy(() -> service.resolvePdf("https://images.q-asker.com/images/a.png"))
        .isInstanceOfSatisfying(
            CustomException.class,
            e -> assertThat(e.getCode()).isEqualTo(ExceptionMessage.INVALID_URL_REQUEST.name()));
  }
}
