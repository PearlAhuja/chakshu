package com.freecharge.smsprofilerservice.aws.config;

import lombok.Builder;
import lombok.Data;
import software.amazon.awssdk.services.sqs.SqsClient;

@Data
@Builder
public class QueueSetup {

    private Integer retryVisibilityTimeout;
    private String queueUrl;
    private SqsClient sqsClient;
    private int processMessageThreadCount;
    private Integer messageBatchSize;
    private Integer retryCount;
    private Integer longPollTimeOut;
    private Boolean flag;
}
