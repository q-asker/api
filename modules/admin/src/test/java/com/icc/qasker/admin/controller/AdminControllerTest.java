package com.icc.qasker.admin.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.icc.qasker.admin.properties.ImageUploadProperties;
import com.icc.qasker.board.BoardAdminService;
import com.icc.qasker.board.dto.request.PostRequest;
import com.icc.qasker.board.dto.request.ReplyRequest;
import com.icc.qasker.global.error.CustomException;
import com.icc.qasker.global.error.ExceptionMessage;
import com.icc.qasker.oci.ObjectStorageService;
import com.icc.qasker.quizset.QualityReviewService;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;

/**
 * AdminController 회귀 테스트.
 *
 * <p>이미지 업로드 검증 분기를 private 메서드로 추출하는 리팩터링의 안전망이다. 검증 순서/에러 코드/위임 호출이 리팩터링 전후로 동일함을 보장한다.
 */
class AdminControllerTest {

  private static final ImageUploadProperties IMAGE_PROPERTIES =
      new ImageUploadProperties(
          5 * 1024 * 1024, Set.of("image/jpeg", "image/png", "image/gif", "image/webp"));

  private BoardAdminService boardAdminService;
  private ObjectStorageService objectStorageService;
  private AdminController adminController;

  @BeforeEach
  void setUp() {
    boardAdminService = mock(BoardAdminService.class);
    objectStorageService = mock(ObjectStorageService.class);
    adminController =
        new AdminController(
            boardAdminService,
            objectStorageService,
            IMAGE_PROPERTIES,
            mock(QualityReviewService.class));
  }

  private MultipartFile validImageFile() throws IOException {
    MultipartFile file = mock(MultipartFile.class);
    when(file.isEmpty()).thenReturn(false);
    when(file.getSize()).thenReturn(1024L);
    when(file.getContentType()).thenReturn("image/png");
    when(file.getOriginalFilename()).thenReturn("shot.png");
    when(file.getInputStream()).thenReturn(new ByteArrayInputStream(new byte[] {1, 2, 3}));
    return file;
  }

  @Nested
  @DisplayName("이미지 업로드 검증")
  class UploadImageValidation {

    @Test
    @DisplayName("빈 파일이면 FILE_NAME_NOT_EXIST")
    void emptyFile() {
      MultipartFile file = mock(MultipartFile.class);
      when(file.isEmpty()).thenReturn(true);

      assertThatThrownBy(() -> adminController.uploadImage(file))
          .isInstanceOf(CustomException.class)
          .hasMessage(ExceptionMessage.FILE_NAME_NOT_EXIST.getMessage());
    }

    @Test
    @DisplayName("5MB 초과면 OUT_OF_FILE_SIZE")
    void tooLarge() {
      MultipartFile file = mock(MultipartFile.class);
      when(file.isEmpty()).thenReturn(false);
      when(file.getSize()).thenReturn(IMAGE_PROPERTIES.maxSize() + 1);

      assertThatThrownBy(() -> adminController.uploadImage(file))
          .isInstanceOf(CustomException.class)
          .hasMessage(ExceptionMessage.OUT_OF_FILE_SIZE.getMessage());
    }

    @Test
    @DisplayName("contentType이 null이면 EXTENSION_INVALID")
    void nullContentType() {
      MultipartFile file = mock(MultipartFile.class);
      when(file.isEmpty()).thenReturn(false);
      when(file.getSize()).thenReturn(1024L);
      when(file.getContentType()).thenReturn(null);

      assertThatThrownBy(() -> adminController.uploadImage(file))
          .isInstanceOf(CustomException.class)
          .hasMessage(ExceptionMessage.EXTENSION_INVALID.getMessage());
    }

    @Test
    @DisplayName("허용되지 않은 contentType이면 EXTENSION_INVALID")
    void disallowedContentType() {
      MultipartFile file = mock(MultipartFile.class);
      when(file.isEmpty()).thenReturn(false);
      when(file.getSize()).thenReturn(1024L);
      when(file.getContentType()).thenReturn("application/pdf");

      assertThatThrownBy(() -> adminController.uploadImage(file))
          .isInstanceOf(CustomException.class)
          .hasMessage(ExceptionMessage.EXTENSION_INVALID.getMessage());
    }
  }

  @Test
  @DisplayName("유효한 이미지면 스토리지에 업로드하고 URL을 반환한다")
  void uploadImageSuccess() throws IOException {
    MultipartFile file = validImageFile();
    when(objectStorageService.uploadImage(any(), anyLong(), eq("image/png"), eq("shot.png")))
        .thenReturn("https://cdn.example.com/shot.png");

    ResponseEntity<Map<String, String>> response = adminController.uploadImage(file);

    assertThat(response.getStatusCode().value()).isEqualTo(200);
    assertThat(response.getBody()).containsEntry("url", "https://cdn.example.com/shot.png");
    verify(objectStorageService).uploadImage(any(), eq(1024L), eq("image/png"), eq("shot.png"));
  }

  @Test
  @DisplayName("스토리지 업로드 중 IOException이면 FILE_UPLOAD_FAILED")
  void uploadImageIoException() throws IOException {
    MultipartFile file = mock(MultipartFile.class);
    when(file.isEmpty()).thenReturn(false);
    when(file.getSize()).thenReturn(1024L);
    when(file.getContentType()).thenReturn("image/jpeg");
    when(file.getOriginalFilename()).thenReturn("a.jpg");
    when(file.getInputStream()).thenThrow(new IOException("stream closed"));

    assertThatThrownBy(() -> adminController.uploadImage(file))
        .isInstanceOf(CustomException.class)
        .hasMessage(ExceptionMessage.FILE_UPLOAD_FAILED.getMessage());
  }

  @Nested
  @DisplayName("게시판 관리 위임")
  class BoardDelegation {

    @Test
    @DisplayName("createUpdateLog는 BoardAdminService에 위임한다")
    void createUpdateLog() {
      PostRequest request = new PostRequest("제목", "내용");

      adminController.createUpdateLog(request, "admin-1");

      verify(boardAdminService).createUpdateLog(request, "admin-1");
    }

    @Test
    @DisplayName("updateUpdateLog는 BoardAdminService에 위임한다")
    void updateUpdateLog() {
      PostRequest request = new PostRequest("제목", "내용");

      adminController.updateUpdateLog(7L, request, "admin-1");

      verify(boardAdminService).updateUpdateLog(7L, request, "admin-1");
    }

    @Test
    @DisplayName("reply는 BoardAdminService에 boardId/userId/content를 전달한다")
    void reply() {
      ReplyRequest replyRequest = new ReplyRequest("관리자 답변");

      adminController.reply(replyRequest, 9L, "admin-1");

      verify(boardAdminService).reply(9L, "admin-1", "관리자 답변");
    }
  }
}
