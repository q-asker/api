package com.icc.qasker.aws.properties;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@AllArgsConstructor
@ConfigurationProperties(prefix = "aws.s3")
public class AwsS3Properties {

    private String region;
    private String bucketName;
    private String accessKey;
    private String secretKey;
    private int maxFileNameLength;
    private String allowedExtensions;
}
