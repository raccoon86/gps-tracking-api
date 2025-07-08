package com.sponovation.runtrack.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client

@Configuration
class AwsConfig {

    @Value("\${aws.access-key-id:}")
    private lateinit var accessKeyId: String

    @Value("\${aws.secret-access-key:}")
    private lateinit var secretAccessKey: String

    @Value("\${aws.region:ap-northeast-2}")
    private lateinit var region: String

    @Bean
    fun s3Client(): S3Client {
        val credentialsProvider = if (accessKeyId.isNotBlank() && secretAccessKey.isNotBlank()) {
            StaticCredentialsProvider.create(
                AwsBasicCredentials.create(accessKeyId, secretAccessKey)
            )
        } else {
            // 로컬 개발 시 기본 credential provider 사용
            null
        }

        val builder = S3Client.builder()
            .region(Region.of(region))

        credentialsProvider?.let { builder.credentialsProvider(it) }

        return builder.build()
    }
} 