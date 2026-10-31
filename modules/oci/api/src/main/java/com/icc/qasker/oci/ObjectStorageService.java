package com.icc.qasker.oci;

import java.io.InputStream;

public interface ObjectStorageService {

  /** 이미지 파일을 Object Storage에 업로드하고 CDN URL을 반환한다. */
  String uploadImage(
      InputStream inputStream, long contentLength, String contentType, String originalFileName);
}
