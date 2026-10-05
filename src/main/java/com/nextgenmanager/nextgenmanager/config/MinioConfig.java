package com.nextgenmanager.nextgenmanager.config;

import io.minio.MinioClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MinioConfig {

    @Value("${minio.url}")
    private String endpoint;

    @Value("${minio.access-key}")
    private String accessKey;

    @Value("${minio.secret-key}")
    private String secretKey;

    // Needed when minio.url points at AWS S3 (e.g. https://s3.ap-south-1.amazonaws.com): without it
    // the client signs for us-east-1 and every call to a bucket in another region fails.
    // Leave blank for a self-hosted MinIO.
    @Value("${minio.region:}")
    private String region;

    @Bean
    public MinioClient minioClient() {
        MinioClient.Builder builder = MinioClient.builder()
                .endpoint(endpoint)
                .credentials(accessKey, secretKey);
        if (region != null && !region.isBlank()) {
            builder.region(region.trim());
        }
        return builder.build();
    }
}
