package com.icc.qasker.oci;

public interface FileValidateService {

  void checkCloudFrontUrlWithThrowing(String url);

  void validateFileWithThrowing(String fileName, long fileSize, String contentType);
}
