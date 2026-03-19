package com.icc.qasker.aws;

import java.nio.file.Path;

public interface S3Service {

  /** PDF 파일을 S3에 업로드하고 CloudFront URL을 반환한다. */
  String uploadPdf(Path pdfFile, String originalFileName);
}
