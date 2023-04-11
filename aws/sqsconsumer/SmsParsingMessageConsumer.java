package com.freecharge.smsprofilerservice.aws.sqsconsumer;

import com.freecharge.smsprofilerservice.aws.config.AmazonParsingSQSResourceConfig;
import com.freecharge.smsprofilerservice.aws.config.QueueSetup;
import com.freecharge.smsprofilerservice.aws.constants.MessageEvaluationStatus;
import com.freecharge.smsprofilerservice.aws.processor.SmsParsingQueueMessageProcessor;
import com.freecharge.smsprofilerservice.exception.JSONParsingException;
import lombok.*;
import lombok.experimental.SuperBuilder;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import software.amazon.awssdk.services.sqs.SqsClient;

import javax.annotation.PostConstruct;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Data
@SuperBuilder
@ToString
@EqualsAndHashCode
public class SmsParsingMessageConsumer extends AbstractQueueConsumer{

    private final AmazonParsingSQSResourceConfig amazonParsingSQSResourceConfig;

    private final SqsClient amazonSQS;

    private final SmsParsingQueueMessageProcessor smsParsingQueueMessageProcessor;

    private final QueueSetup queueSetup;

    @Setter()
    private Boolean flag = true;

    public SmsParsingMessageConsumer(@NonNull final AmazonParsingSQSResourceConfig amazonParsingSQSResourceConfig,
                                     @NonNull final SqsClient amazonSQS,
                                     @NonNull final QueueSetup queueSetup,
                                     @NonNull final SmsParsingQueueMessageProcessor smsParsingQueueMessageProcessor) {
        this.queueSetup = queueSetup;
        this.amazonParsingSQSResourceConfig = amazonParsingSQSResourceConfig;
        this.amazonSQS = amazonSQS;
        this.smsParsingQueueMessageProcessor = smsParsingQueueMessageProcessor;
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
            final Boolean result = smsParsingQueueMessageProcessor.process(body);
            return MessageEvaluationStatus.SUCCESS;
        } catch (JSONParsingException e) {
            return MessageEvaluationStatus.INVALID_REQUEST;
        } catch (Exception e) {
            log.error("Error while processing message with exceptionMessage {} and completeException {}", e.getMessage(), e);
            return MessageEvaluationStatus.ERROR;
        } finally {
            this.deleteMessageFromQueue(receiptHandle);
            log.info("Message deleted from queue with receipt handle{}", receiptHandle);
            MDC.remove("requestId");
        }
    }
}

