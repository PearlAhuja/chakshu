package com.freecharge.smsprofilerservice.aws.config;

import com.freecharge.vault.PropertiesConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

import java.net.URI;
import java.util.Map;

@Slf4j
@Configuration
public class S3Config {

    private final String s3RuleRegion;

    @Value("${spring.profiles.active:Unknown}")
    private String activeProfile;

    @Autowired
    public S3Config(@Qualifier("applicationProperties") PropertiesConfig applicationProperties) {
        final Map<String, Object> awsProperties = applicationProperties.getProperties();
        this.s3RuleRegion = (String) awsProperties.get("aws.s3.rule.region");
    }

    @Bean
    public S3Client s3Client() {
        if (activeProfile.equalsIgnoreCase("dev")) {
            return S3Client.builder()
                    .region(Region.of(s3RuleRegion))
                    .endpointOverride(URI.create("http://localhost:4566"))
                    .build();
        }
        return S3Client.builder()
                .region(Region.of(s3RuleRegion))
                .build();
    }
}
