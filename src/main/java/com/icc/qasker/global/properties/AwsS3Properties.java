package com.icc.qasker.global.properties;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "aws.s3")
@Getter
@AllArgsConstructor
public class AwsS3Properties {

    private String region;
    private String bucketName;
    private String accessKey;
    private String secretKey;
    private int maxFileNameLength;
    private String allowedExtensions;

}
