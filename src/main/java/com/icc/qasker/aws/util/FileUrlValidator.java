package com.icc.qasker.aws.util;

import lombok.Builder;

@Builder
public class FileUrlValidator {

    private String cloudFrontBaseUrl;
    private String allowedExtensions;
}
