package com.icc.qasker.oci.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "cdn")
public record CdnProperties(String imageBaseUrl, String fileBaseUrl) {}
