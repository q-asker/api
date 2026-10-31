package com.icc.qasker.oci.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "oci.object-storage")
public record OciObjectStorageProperties(
    String namespace,
    String imageBucketName,
    String region,
    String configFilePath,
    String profile) {}
