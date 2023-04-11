package com.freecharge.smsprofilerservice.aws.config;

import com.freecharge.vault.PropertiesConfig;
import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.Map;


@Data
@Component
public class AmazonCVSQSResourceConfig {

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

    @Autowired
    public AmazonCVSQSResourceConfig(@Qualifier("applicationProperties") PropertiesConfig propertiesConfig) {
        final Map<String, Object> awsProperties = propertiesConfig.getProperties();
        this.sqsRuleRegion = (String) awsProperties.get("aws.cv.sqs.region");
        this.sqsUrl = (String) awsProperties.get("aws.cv.sqs.url");
        this.messageBatchSize = (Integer) awsProperties.get("aws.cv.sqs.message.batchSize");
        this.longPollTimeOut = (Integer) awsProperties.get("aws.cv.sqs.long.poll.timeout");
        this.retryCount = (Integer) awsProperties.get("aws.cv.sqs.retry.count");
        this.processMessageThreadCount = (Integer) awsProperties.get("aws.cv.sqs.process.thread.count");
        this.retryVisibilityTimeout = (Integer) awsProperties.get("aws.cv.sqs.retry.visibility.timeout");
        this.pickCredentialsFromPropertiesFile = (Boolean) awsProperties.get("aws.cv.sqs.pickCredentialsFromPropertiesFile");
        this.awsArn = (String) awsProperties.get("aws.arn");
        this.awsArnName = (String) awsProperties.get("aws.arn.name");
    }
}
