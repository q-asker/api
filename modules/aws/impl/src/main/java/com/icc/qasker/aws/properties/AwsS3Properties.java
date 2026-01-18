package com.icc.qasker.aws.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "aws.s3")
public record AwsS3Properties(
    String region,
    String bucketName,
    String accessKey,
    String secretKey,
    int signatureDuration,
    int maxFileNameLength,
    String allowedExtensions
) {

};
