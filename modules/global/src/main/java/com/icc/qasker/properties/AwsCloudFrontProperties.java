package com.icc.qasker.global.properties;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "aws.cloudfront")
@Getter
@AllArgsConstructor
public class AwsCloudFrontProperties {

    private String baseUrl;
}
