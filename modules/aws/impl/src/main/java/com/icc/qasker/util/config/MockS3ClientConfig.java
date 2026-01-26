package com.icc.qasker.util.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;

@Configuration
@Profile("stress-test")
public class MockS3ClientConfig {

    @Bean
    @Primary
    public S3Client mockS3Client() {
        // 익명 클래스로 S3Client의 동작을 가로챔
        return new S3Client() {
            @Override
            public String serviceName() {
                return "s3";
            }

            @Override
            public void close() {
                // Do nothing
            }

            @Override
            public PutObjectResponse putObject(PutObjectRequest putObjectRequest,
                RequestBody requestBody) {
                System.out.println("Mock S3 Upload: " + putObjectRequest.key());

                try {
                    Thread.sleep(2000);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }

                return PutObjectResponse.builder().build();
            }
        };
    }
}