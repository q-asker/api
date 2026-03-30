package com.icc.qasker.oci;

import java.nio.file.Path;

public interface ObjectStorageService {

  /** PDF 파일을 Object Storage에 업로드하고 CloudFront URL을 반환한다. */
  String uploadPdf(Path pdfFile, String originalFileName);
}
