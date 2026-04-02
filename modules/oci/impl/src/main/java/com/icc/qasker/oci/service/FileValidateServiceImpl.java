package com.icc.qasker.oci.service;

import com.icc.qasker.global.error.CustomException;
import com.icc.qasker.global.error.ExceptionMessage;
import com.icc.qasker.oci.FileValidateService;
import com.icc.qasker.oci.properties.CdnProperties;
import com.icc.qasker.oci.properties.FileValidationProperties;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@AllArgsConstructor
public class FileValidateServiceImpl implements FileValidateService {

  private final CdnProperties cdnProperties;
  private final FileValidationProperties fileValidationProperties;

  @Override
  public void checkCdnUrlWithThrowing(String url) {
    if (!url.startsWith(cdnProperties.baseUrl())) {
      throw new CustomException(ExceptionMessage.INVALID_URL_REQUEST);
    }
  }

  @Override
  public void validateFileWithThrowing(String fileName, long fileSize, String contentType) {
    int maxFileNameLength = fileValidationProperties.maxFileNameLength();
    String allowedExtensions = fileValidationProperties.allowedExtensions();

    if (fileSize > fileValidationProperties.maxFileSize()) {
      throw new CustomException(ExceptionMessage.OUT_OF_FILE_SIZE);
    }
    if (fileName == null) {
      throw new CustomException(ExceptionMessage.FILE_NAME_NOT_EXIST);
    }
    if (fileName.length() > maxFileNameLength) {
      throw new CustomException(ExceptionMessage.FILE_NAME_TOO_LONG);
    }
    if (contentType == null) {
      throw new CustomException(ExceptionMessage.EXTENSION_NOT_EXIST);
    }
    if (!allowedExtensions.contains(contentType)) {
      throw new CustomException(ExceptionMessage.EXTENSION_INVALID);
    }
  }
}
