package com.slb.qasker.aws.config;

import com.slb.qasker.aws.util.FileUploadValidator;
import com.slb.qasker.aws.util.FileUrlValidator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

@Configuration
public class FileUploadConfig {

    @Value("${aws.s3.region}")
    private String region;
    @Value("${aws.s3.access-key}")
    private String accessKey;
    @Value("${aws.s3.secret-key}")
    private String secretKey;
    @Value("${aws.s3.max-file-name-length}")
    private int maxFileNameLength;
    @Value("${aws.s3.allowed-extensions}")
    private String allowedExtensions;
    @Value("${aws.cloudfront.base-url}")
    private String cloudFrontBaseUrl;

    @Bean
    public S3Client createS3Client() {
        AwsBasicCredentials awsCredentials = AwsBasicCredentials.create(accessKey, secretKey);

        return S3Client.builder().region(Region.of(region))
            .credentialsProvider(StaticCredentialsProvider.create(awsCredentials)).build();
    }

    @Bean
    public FileUploadValidator createFileUploadValidator() {
        return FileUploadValidator.builder()
            .maxFileNameLength(maxFileNameLength)
            .allowedExtensions(allowedExtensions)
            .build();
    }

    @Bean
    public FileUrlValidator createFileUrlValidator() {
        return FileUrlValidator.builder()
            .cloudFrontBaseUrl(cloudFrontBaseUrl)
            .allowedExtensions(allowedExtensions)
            .build();
    }
}