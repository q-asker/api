package com.icc.qasker.oci;

public interface FileValidateService {

  void checkCdnUrlWithThrowing(String url);

  void validateFileWithThrowing(String fileName, long fileSize, String contentType);
}
