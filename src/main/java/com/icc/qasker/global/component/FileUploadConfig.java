package com.icc.qasker.global.component;

import com.icc.qasker.aws.util.FileUploadValidator;
import com.icc.qasker.aws.util.FileUrlValidator;
import com.icc.qasker.global.properties.AwsCloudFrontProperties;
import com.icc.qasker.global.properties.AwsS3Properties;
import lombok.AllArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

@Configuration
@AllArgsConstructor
public class FileUploadConfig {

    private final AwsS3Properties awsS3Properties;
    private final AwsCloudFrontProperties cloudFrontProperties;

    @Bean
    public S3Client createS3Client() {
        String accessKey = awsS3Properties.getAccessKey();
        String secretKey = awsS3Properties.getSecretKey();
        String region = awsS3Properties.getRegion();

        AwsBasicCredentials awsCredentials = AwsBasicCredentials.create(accessKey, secretKey);

        return S3Client.builder().region(Region.of(region))
            .credentialsProvider(StaticCredentialsProvider.create(awsCredentials)).build();
    }

    @Bean
    public FileUploadValidator createFileUploadValidator() {
        int maxFileNameLength = awsS3Properties.getMaxFileNameLength();
        String allowedExtensions = awsS3Properties.getAllowedExtensions();

        return FileUploadValidator.builder()
            .maxFileNameLength(maxFileNameLength)
            .allowedExtensions(allowedExtensions)
            .build();
    }

    @Bean
    public FileUrlValidator createFileUrlValidator() {
        String cloudFrontBaseUrl = cloudFrontProperties.getBaseUrl();
        String allowedExtensions = awsS3Properties.getAllowedExtensions();
        return FileUrlValidator.builder()
            .cloudFrontBaseUrl(cloudFrontBaseUrl)
            .allowedExtensions(allowedExtensions)
            .build();
    }
}