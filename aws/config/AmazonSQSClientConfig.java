package com.freecharge.smsprofilerservice.aws.config;

import com.freecharge.smsprofilerservice.aws.service.AbstractAWSService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;

import java.io.IOException;
import java.net.URI;

@Slf4j
@Configuration
public class AmazonSQSClientConfig extends AbstractAWSService<SqsClient> {

    final AmazonCVSQSResourceConfig amazonCVSQSResourceConfig;

    final AmazonParsingSQSResourceConfig amazonParsingSQSResourceConfig;

    final AmazonHashcodeSQSResourceConfig amazonHashcodeSQSResourceConfig;

    final AmazonReplaySQSResourceConfig amazonReplaySQSResourceConfig;

    final BaseAwsResourceConfig baseAwsResourceConfig;

    @Value("${spring.profiles.active:Unknown}")
    private String activeProfile;

    @Autowired
    public AmazonSQSClientConfig(AmazonCVSQSResourceConfig amazonCVSQSResourceConfig, AmazonParsingSQSResourceConfig amazonParsingSQSResourceConfig, AmazonHashcodeSQSResourceConfig amazonHashcodeSQSResourceConfig, BaseAwsResourceConfig baseAwsResourceConfig ,AmazonReplaySQSResourceConfig amazonReplaySQSResourceConfig) {
        this.amazonCVSQSResourceConfig = amazonCVSQSResourceConfig;
        this.amazonParsingSQSResourceConfig = amazonParsingSQSResourceConfig;
        this.amazonHashcodeSQSResourceConfig = amazonHashcodeSQSResourceConfig;
        this.baseAwsResourceConfig = baseAwsResourceConfig;
        this.amazonReplaySQSResourceConfig=amazonReplaySQSResourceConfig;
    }


    @Bean("replayBean")
    protected SqsClient getReplaySqsBean() throws IOException {
        final URI uri = URI.create(amazonReplaySQSResourceConfig.getSqsUrl());
        if (activeProfile.equalsIgnoreCase("dev")) {
            return SqsClient.builder()
                    .region(Region.of(amazonReplaySQSResourceConfig.getSqsRuleRegion()))
                    .endpointOverride(uri)
                    .credentialsProvider(StaticCredentialsProvider.create(
                            AwsBasicCredentials.create("dummyKey", "dummySecret")))
                    .build();
        }
        SqsClient sqsClient = SqsClient.builder()
                /* .credentialsProvider(getAwsCredentials(amazonSQSResourceConfig.getAwsArn(), amazonSQSResourceConfig.getSqsRuleRegion(),
                         amazonSQSResourceConfig.getAwsArnName()))*/
                .endpointOverride(uri)
                .region(Region.of(amazonReplaySQSResourceConfig.getSqsRuleRegion()))
                .build();
        log.info("Aws Replay Client  created");
        return sqsClient;
    }

    @Bean("smsParsingBean")
    protected SqsClient geResourceClient() throws IOException {
        final URI uri = URI.create(amazonParsingSQSResourceConfig.getSqsUrl());
        if (activeProfile.equalsIgnoreCase("dev")) {
            return SqsClient.builder()
                    .region(Region.of(amazonParsingSQSResourceConfig.getSqsRuleRegion()))
                    .endpointOverride(uri)
                    .credentialsProvider(StaticCredentialsProvider.create(
                            AwsBasicCredentials.create("dummyKey", "dummySecret")))
                    .build();
        }
        /* .credentialsProvider(getAwsCredentials(amazonSQSResourceConfig.getAwsArn(), amazonSQSResourceConfig.getSqsRuleRegion(),
                        amazonSQSResourceConfig.getAwsArnName()))*/
        SqsClient sqsClient = SqsClient.builder()
                /* .credentialsProvider(getAwsCredentials(amazonSQSResourceConfig.getAwsArn(), amazonSQSResourceConfig.getSqsRuleRegion(),
                         amazonSQSResourceConfig.getAwsArnName()))*/
                .endpointOverride(uri)
                .region(Region.of(amazonParsingSQSResourceConfig.getSqsRuleRegion()))
                .build();
        log.info("AwsV2ClientSms parsing SqsClient created");
        return sqsClient;
    }

    @Bean("smsCVBean")
    protected SqsClient getCVSqsBean() throws IOException {
        final URI uri = URI.create(amazonCVSQSResourceConfig.getSqsUrl());
        if (activeProfile.equalsIgnoreCase("dev")) {
            return SqsClient.builder()
                    .region(Region.of(amazonCVSQSResourceConfig.getSqsRuleRegion()))
                    .endpointOverride(uri)
                    .credentialsProvider(StaticCredentialsProvider.create(
                            AwsBasicCredentials.create("dummyKey", "dummySecret")))
                    .build();
        }
        /* .credentialsProvider(getAwsCredentials(amazonSQSResourceConfig.getAwsArn(), amazonSQSResourceConfig.getSqsRuleRegion(),
                        amazonSQSResourceConfig.getAwsArnName()))*/
        SqsClient sqsClient = SqsClient.builder()
                /* .credentialsProvider(getAwsCredentials(amazonSQSResourceConfig.getAwsArn(), amazonSQSResourceConfig.getSqsRuleRegion(),
                         amazonSQSResourceConfig.getAwsArnName()))*/
                .endpointOverride(uri)
                .region(Region.of(amazonCVSQSResourceConfig.getSqsRuleRegion()))
                .build();
        log.info("AwsV2ClientSms CV SqsClient created");
        return sqsClient;
    }

    @Bean("smsHashcodeBean")
    protected SqsClient getHashcodeSqsBean() throws IOException {
        final URI uri = URI.create(amazonHashcodeSQSResourceConfig.getSqsUrl());
        if (activeProfile.equalsIgnoreCase("dev")) {
            return SqsClient.builder()
                    .region(Region.of(amazonHashcodeSQSResourceConfig.getSqsRuleRegion()))
                    .endpointOverride(uri)
                    .credentialsProvider(StaticCredentialsProvider.create(
                            AwsBasicCredentials.create("dummyKey", "dummySecret")))
                    .build();
        }
        /* .credentialsProvider(getAwsCredentials(amazonSQSResourceConfig.getAwsArn(), amazonSQSResourceConfig.getSqsRuleRegion(),
                        amazonSQSResourceConfig.getAwsArnName()))*/
        SqsClient sqsClient = SqsClient.builder()
                /* .credentialsProvider(getAwsCredentials(amazonSQSResourceConfig.getAwsArn(), amazonSQSResourceConfig.getSqsRuleRegion(),
                         amazonSQSResourceConfig.getAwsArnName()))*/
                .endpointOverride(uri)
                .region(Region.of(amazonHashcodeSQSResourceConfig.getSqsRuleRegion()))
                .build();
        log.info("AwsV2ClientSms Hashcode SqsClient created");
        return sqsClient;
    }

}
