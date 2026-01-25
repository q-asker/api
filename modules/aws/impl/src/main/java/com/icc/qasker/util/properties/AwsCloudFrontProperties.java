package com.icc.qasker.util.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "aws.cloudfront")
public record AwsCloudFrontProperties(String baseUrl) {

};
