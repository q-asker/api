package com.slb.qasker.aws.util;

import com.slb.qasker.global.error.CustomError;
import com.slb.qasker.global.error.ErrorMessage;
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
            throw new CustomError(ErrorMessage.NO_FILE_UPLOADED);
        }
        if (fileName == null) {
            throw new CustomError(ErrorMessage.FILE_NAME_NOT_EXIST);
        }
        if (fileName.length() > maxFileNameLength) {
            throw new CustomError(ErrorMessage.FILE_NAME_TOO_LONG);
        }
        if (contentType == null) {
            throw new CustomError(ErrorMessage.EXTENSION_NOT_EXIST);
        }
        if (!allowedExtensions.contains(contentType)) {
            throw new CustomError(ErrorMessage.EXTENSION_INVALID);
        }
    }
}