package com.freecharge.smsprofilerservice.aws.sqsconsumer;

import java.util.Map;
import java.util.UUID;

import javax.annotation.PostConstruct;

import org.slf4j.MDC;
import com.freecharge.smsprofilerservice.aws.config.AmazonHashcodeSQSResourceConfig;
import com.freecharge.smsprofilerservice.aws.config.QueueSetup;
import com.freecharge.smsprofilerservice.aws.constants.MessageEvaluationStatus;
import com.freecharge.smsprofilerservice.aws.processor.HashcodeQueueMessageProcessor;
import com.freecharge.smsprofilerservice.exception.JSONParsingException;

import lombok.*;
import lombok.experimental.SuperBuilder;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.services.sqs.SqsClient;


@Slf4j
@Data
@SuperBuilder
@ToString
@EqualsAndHashCode
public class HashcodeConsumer extends AbstractQueueConsumer{

    private final AmazonHashcodeSQSResourceConfig amazonHashcodeSQSResourceConfig;

    private final SqsClient amazonSQS;

    private final HashcodeQueueMessageProcessor hashcodeQueueMessageProcessor;

    private final QueueSetup queueSetup;

    @Setter()
    private Boolean flag = true;

    public HashcodeConsumer(@NonNull final AmazonHashcodeSQSResourceConfig amazonHashcodeSQSResourceConfig,
                            @NonNull final SqsClient amazonSQS,
                            @NonNull final HashcodeQueueMessageProcessor hashcodeQueueMessageProcessor,
                            @NonNull final QueueSetup queueSetup) {
        this.queueSetup = queueSetup;
        this.amazonHashcodeSQSResourceConfig = amazonHashcodeSQSResourceConfig;
        this.amazonSQS = amazonSQS;
        this.hashcodeQueueMessageProcessor = hashcodeQueueMessageProcessor;
    }

    @PostConstruct
    public void init() {
        super.init(queueSetup);
    }

    @Override
    public <T> MessageEvaluationStatus process(T body, String receiptHandle, Map<String, String> messageAttributes) {
        boolean isSuccess = true;
        MDC.put("requestId", UUID.randomUUID().toString());
        try {
            hashcodeQueueMessageProcessor.process(body);
            return MessageEvaluationStatus.SUCCESS;
        } catch (JSONParsingException e) {
            return MessageEvaluationStatus.INVALID_REQUEST;
        } catch (Exception e) {
            log.error("Error while processing message with exception {} and {}", e.getMessage(), e);
            isSuccess = false;
            return MessageEvaluationStatus.ERROR;
        } finally {
//            this.deleteMessageFromQueue(receiptHandle);
            if (isSuccess) {
                this.deleteMessageFromQueue(receiptHandle);
                log.info("Message {} deleted from queue with receipt handle{}", receiptHandle);
            } else {
                int retryCount = getMessageReceiveCount(messageAttributes);
                if ((retryCount + 1) > getRetryCount()) {
                    this.deleteMessageFromQueue(receiptHandle);
                    log.info("Message {} deleted from queue with receipt handle{}", receiptHandle);
                }
            }
            MDC.remove("requestId");
        }
    }
}
