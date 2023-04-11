package com.freecharge.smsprofilerservice.aws.config;

import com.freecharge.vault.PropertiesConfig;
import lombok.Data;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.Map;


@Data
@Component
public class AmazonHashcodeSQSResourceConfig {

    private String sqsRuleRegion;
    private String sqsUrl;
    private boolean pickCredentialsFromPropertiesFile;
    private int messageBatchSize;
    private int processMessageThreadCount;
    private int retryVisibilityTimeout;
    private int retryCount;
    private int longPollTimeOut;
    private String awsArn;
    private String awsArnName;

    public AmazonHashcodeSQSResourceConfig(@Qualifier("applicationProperties") PropertiesConfig propertiesConfig) {
        final Map<String, Object> awsProperties = propertiesConfig.getProperties();
        this.sqsRuleRegion = (String) awsProperties.get("aws.hashcode.sqs.region");
        this.sqsUrl = (String) awsProperties.get("aws.hashcode.sqs.url");
        this.messageBatchSize = (Integer) awsProperties.get("aws.hashcode.sqs.message.batchSize");
        this.longPollTimeOut = (Integer) awsProperties.get("aws.hashcode.sqs.long.poll.timeout");
        this.retryCount = (Integer) awsProperties.get("aws.hashcode.sqs.retry.count");
        this.processMessageThreadCount = (Integer) awsProperties.get("aws.hashcode.sqs.process.thread.count");
        this.retryVisibilityTimeout = (Integer) awsProperties.get("aws.hashcode.sqs.retry.visibility.timeout");
        this.pickCredentialsFromPropertiesFile = (Boolean) awsProperties.get("aws.hashcode.sqs.pickCredentialsFromPropertiesFile");
        this.awsArn = (String) awsProperties.get("aws.arn");
        this.awsArnName = (String) awsProperties.get("aws.arn.name");
    }
}
