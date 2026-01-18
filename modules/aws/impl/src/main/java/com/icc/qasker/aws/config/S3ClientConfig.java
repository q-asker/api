package com.icc.qasker.aws.config;

import com.icc.qasker.aws.properties.AwsS3Properties;
import lombok.AllArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

@Configuration
@AllArgsConstructor
public class S3ClientConfig {

    private final AwsS3Properties awsS3Properties;

    @Bean
    public S3Client s3Client() {
        String accessKey = awsS3Properties.accessKey();
        String secretKey = awsS3Properties.secretKey();
        String region = awsS3Properties.region();

        AwsBasicCredentials awsCredentials = AwsBasicCredentials.create(accessKey, secretKey);

        return S3Client.builder().region(Region.of(region))
            .credentialsProvider(StaticCredentialsProvider.create(awsCredentials)).build();
    }

    @Bean
    public S3Presigner s3Presigner() {
        return S3Presigner.builder()
            .region(Region.of(awsS3Properties.region()))
            .credentialsProvider(StaticCredentialsProvider.create(
                AwsBasicCredentials.create(
                    awsS3Properties.accessKey(),
                    awsS3Properties.secretKey()
                )
            ))
            .build();
    }
}