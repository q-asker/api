package com.slb.qasker.aws.util;

import com.slb.qasker.global.error.CustomException;
import com.slb.qasker.global.error.ExceptionMessage;
import lombok.Builder;
import org.springframework.web.multipart.MultipartFile;

@Builder
public class FileUploadValidator {

    private int maxFileNameLength;
    private String allowedExtensions;

    public void checkOf(MultipartFile multipartFile) {
        String fileName = multipartFile.getOriginalFilename();
        String contentType = multipartFile.getContentType();

        if (multipartFile.isEmpty()) {
            throw new CustomException(ExceptionMessage.NO_FILE_UPLOADED);
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