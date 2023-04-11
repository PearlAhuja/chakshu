package com.freecharge.smsprofilerservice.aws.sqsconsumer;

import com.freecharge.smsprofilerservice.aws.config.QueueSetup;
import com.freecharge.smsprofilerservice.aws.constants.MessageEvaluationStatus;
import com.freecharge.smsprofilerservice.dao.mysql.repository.DailyCountRepository;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.*;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
@Data
@SuperBuilder
@ToString
@EqualsAndHashCode
@NoArgsConstructor
public abstract class AbstractQueueConsumer {

    private QueueSetup queueSetup;
    private SqsClient sqs;
    private ExecutorService executorService;
    private Boolean flag;

    @Autowired
    private DailyCountRepository dailyCountRepository;

    /**
     * @param queueSetup Generic method to set up the queue and start poller
     */
    protected void init(QueueSetup queueSetup) {
        this.queueSetup = queueSetup;
        this.sqs = queueSetup.getSqsClient();
        this.flag = queueSetup.getFlag();
        executorService = Executors.newFixedThreadPool(queueSetup.getProcessMessageThreadCount());
        for (int i = 0; i < queueSetup.getProcessMessageThreadCount(); i++) {
            executorService.submit(() -> processMessage());
        }
       // processMessage();
    }

    protected void processMessage() {
        log.info("Calling process message thread to fetch and process message.");
        while (flag) {
            List<Message> messages = getMessageFromQueue();
            if (!CollectionUtils.isEmpty(messages)) {
                for (Message message : messages) {
                    MessageEvaluationStatus status = this.process(message.body(), message.receiptHandle(), message.attributesAsStrings());
                    switch (status) {
                        case SUCCESS:
                            break;
                        case INVALID_REQUEST:
                            log.debug("Invalid request found, Stop processing");
                            break;
                        case ERROR:
                            log.debug("Error while processing message with body : {}", message.body());
                            break;
                        default:
                            log.debug("Invalid status while parsing message: {}", message);
                    }
                }
            }
        }
    }

    public abstract <T> MessageEvaluationStatus process(T body, String receiptHandle, Map<String, String> messageAttributes);

    protected List<Message> getMessageFromQueue() {
        try {
            log.debug("Going to fetching message from queue for url : {}", queueSetup.getQueueUrl());
            String queueUrl = queueSetup.getQueueUrl();
            ReceiveMessageRequest receiveMessageRequest = ReceiveMessageRequest.builder()
                    .queueUrl(queueUrl)
                    .attributeNames(QueueAttributeName.ALL)
                    .maxNumberOfMessages(queueSetup.getMessageBatchSize())
                    .visibilityTimeout(queueSetup.getRetryVisibilityTimeout())
                    .waitTimeSeconds(queueSetup.getLongPollTimeOut())
                    .build();
            return sqs.receiveMessage(receiveMessageRequest).messages();
        } catch (Exception e) {
            log.error("Error while fetching message from queue {} with exception {}.",queueSetup.getQueueUrl(), e.getMessage());
        }
        return null;
    }

    protected Integer getMessageReceiveCount(Map<String, String> message) {
        log.debug("Message received" + message);
        String approximateReceiveCount = message.get(MessageSystemAttributeName.APPROXIMATE_RECEIVE_COUNT.toString());
        if (!Objects.isNull(approximateReceiveCount) && !StringUtils.isEmpty(approximateReceiveCount))
            return Integer.parseInt(approximateReceiveCount);
        return 1;
    }

    public void deleteMessageFromQueue(String receiptHandle) {
        try {
            String queueUrl = queueSetup.getQueueUrl();
            log.debug("Message to be deleted with handle : " + receiptHandle);
            sqs.deleteMessage(DeleteMessageRequest.builder()
                    .queueUrl(queueUrl)
                    .receiptHandle(receiptHandle)
                    .build());
            log.debug("Message Deleted...");
        } catch (Exception e) {
            log.error("Error while deleting message with receipt handle: {} from url: {} with exception: {} .", receiptHandle, queueSetup.getQueueUrl(), e.getMessage());
        }
    }

    public Integer getRetryCount() {
        return Objects.isNull(queueSetup.getRetryCount()) ? 5 : queueSetup.getRetryCount();
    }

}
