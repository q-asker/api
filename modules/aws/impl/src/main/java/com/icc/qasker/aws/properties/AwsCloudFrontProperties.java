package com.icc.qasker.aws.properties;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@AllArgsConstructor
@ConfigurationProperties(prefix = "aws.cloudfront")
public class AwsCloudFrontProperties {

    private String baseUrl;
}
