package com.icc.qasker.aws.util;

import com.icc.qasker.global.error.CustomException;
import com.icc.qasker.global.error.ExceptionMessage;
import lombok.AllArgsConstructor;
import lombok.Builder;

@Builder
@AllArgsConstructor
public class FileUrlValidator {

    private String cloudFrontBaseUrl;
    private String allowedExtensions;

    public boolean isCloudFrontUrl(String url) {
        if (!url.startsWith(cloudFrontBaseUrl)) {
            throw new CustomException(ExceptionMessage.INVALID_URL_REQUEST);
        }
        return true;
    }
}
