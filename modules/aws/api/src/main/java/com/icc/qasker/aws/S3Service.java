package com.icc.qasker.aws;

import com.icc.qasker.aws.dto.FileExistStatusResponse;
import com.icc.qasker.aws.dto.PresignRequest;
import com.icc.qasker.aws.dto.PresignResponse;
import org.springframework.web.multipart.MultipartFile;

public interface S3Service {

  PresignResponse requestPresign(PresignRequest presignRequest);

  FileExistStatusResponse checkFileExistence(String originalFileName);

  /** 비PDF 파일을 multipart로 수신하여 PDF 변환 후 S3에 업로드한다. */
  PresignResponse uploadNonPdfFile(MultipartFile file);
}
