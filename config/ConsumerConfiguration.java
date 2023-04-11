package com.freecharge.smsprofilerservice.config;

import com.freecharge.smsprofilerservice.aws.processor.CvSmsQueueMessageProcessor;
import com.freecharge.smsprofilerservice.aws.processor.HashcodeQueueMessageProcessor;
import com.freecharge.smsprofilerservice.aws.processor.SmsParsingQueueMessageProcessor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import com.freecharge.smsprofilerservice.aws.config.AmazonCVSQSResourceConfig;
import com.freecharge.smsprofilerservice.aws.config.AmazonHashcodeSQSResourceConfig;
import com.freecharge.smsprofilerservice.aws.config.AmazonParsingSQSResourceConfig;
import com.freecharge.smsprofilerservice.aws.config.QueueSetup;
import com.freecharge.smsprofilerservice.aws.sqsconsumer.HashcodeConsumer;
import com.freecharge.smsprofilerservice.aws.sqsconsumer.SmsCVMessageConsumer;
import com.freecharge.smsprofilerservice.aws.sqsconsumer.SmsParsingMessageConsumer;
import com.freecharge.smsprofilerservice.converter.SmsRequestConverter;
import com.freecharge.smsprofilerservice.filter.SendersFilter;
import com.freecharge.smsprofilerservice.service.impl.BacklogSmsProcessor;
import com.freecharge.smsprofilerservice.service.impl.ShortcodeCounterCacheService;
import com.freecharge.smsprofilerservice.service.impl.SmsTokenizerManager;
import software.amazon.awssdk.services.sqs.SqsClient;

@Configuration
public class ConsumerConfiguration {

    @Autowired
    private  AmazonHashcodeSQSResourceConfig amazonHashcodeSQSResourceConfig;

    @Autowired
    @Qualifier("smsHashcodeBean")
    private SqsClient smsHashcodeBeanAmazonSQS;

    @Autowired
    private BacklogSmsProcessor backlogSmsProcessor;

    @Autowired
    @Qualifier("smsCVBean")
    private SqsClient smsCVBeanAmazonSQS;

    @Autowired
    private AmazonCVSQSResourceConfig amazonCVSQSResourceConfig;

    @Autowired
    private SmsRequestConverter smsRequestConverter;

    @Autowired
    private SendersFilter sendersFilter;

    @Autowired
    private SmsTokenizerManager smsTokenizerManager;

    @Autowired
    private ShortcodeCounterCacheService shortcodeCounterCacheService;


    @Autowired
    private AmazonParsingSQSResourceConfig amazonParsingSQSResourceConfig;

    @Autowired
    @Qualifier("smsParsingBean")
    private SqsClient smsParsingBeanAmazonSQS;

    @Autowired
    private ShortcodeCounterCacheService shortcodeCacheService;

    @Autowired
    private CvSmsQueueMessageProcessor cvSmsQueueMessageProcessor;

    @Autowired
    private SmsParsingQueueMessageProcessor smsParsingQueueMessageProcessor;

    @Autowired
    private HashcodeQueueMessageProcessor hashcodeQueueMessageProcessor;



    @Bean
    @Autowired
    public HashcodeConsumer hashcodeConsumer() {
        final QueueSetup queueSetup = QueueSetup.builder()
                .sqsClient(smsHashcodeBeanAmazonSQS)
                .messageBatchSize(amazonHashcodeSQSResourceConfig.getMessageBatchSize())
                .processMessageThreadCount(amazonHashcodeSQSResourceConfig.getProcessMessageThreadCount())
                .queueUrl(amazonHashcodeSQSResourceConfig.getSqsUrl())
                .retryCount(amazonHashcodeSQSResourceConfig.getRetryCount())
                .longPollTimeOut(amazonHashcodeSQSResourceConfig.getLongPollTimeOut())
                .retryVisibilityTimeout(amazonHashcodeSQSResourceConfig.getRetryVisibilityTimeout())
                .flag(true)
                .build();
        return HashcodeConsumer.builder()
                .amazonHashcodeSQSResourceConfig(amazonHashcodeSQSResourceConfig)
                .hashcodeQueueMessageProcessor(hashcodeQueueMessageProcessor)
                .amazonSQS(smsHashcodeBeanAmazonSQS)
                .queueSetup(queueSetup)
                .build();
    }

    @Bean
    @Autowired
    public SmsCVMessageConsumer smsCVMessageConsumer() {
        final QueueSetup queueSetup =  QueueSetup.builder()
                .sqsClient(smsCVBeanAmazonSQS)
                .messageBatchSize(amazonCVSQSResourceConfig.getMessageBatchSize())
                .processMessageThreadCount(amazonCVSQSResourceConfig.getProcessMessageThreadCount())
                .queueUrl(amazonCVSQSResourceConfig.getSqsUrl())
                .retryCount(amazonCVSQSResourceConfig.getRetryCount())
                .longPollTimeOut(amazonCVSQSResourceConfig.getLongPollTimeOut())
                .retryVisibilityTimeout(amazonCVSQSResourceConfig.getRetryVisibilityTimeout())
                .flag(true)
                .build();
        return SmsCVMessageConsumer.builder()
                .amazonCVSQSResourceConfig(amazonCVSQSResourceConfig)
                .queueSetup(queueSetup)
                .sqs(smsCVBeanAmazonSQS)
                .cvSmsQueueMessageProcessor(cvSmsQueueMessageProcessor)
                .build();
    }

    @Bean
    @Autowired
    public SmsParsingMessageConsumer smsParsingMessageConsumer() {
        final QueueSetup queueSetup = QueueSetup.builder()
                .sqsClient(smsParsingBeanAmazonSQS)
                .messageBatchSize(amazonParsingSQSResourceConfig.getMessageBatchSize())
                .processMessageThreadCount(amazonParsingSQSResourceConfig.getProcessMessageThreadCount())
                .queueUrl(amazonParsingSQSResourceConfig.getSqsUrl())
                .retryCount(amazonParsingSQSResourceConfig.getRetryCount())
                .longPollTimeOut(amazonParsingSQSResourceConfig.getLongPollTimeOut())
                .retryVisibilityTimeout(amazonParsingSQSResourceConfig.getRetryVisibilityTimeout())
                .flag(true)
                .build();
        return SmsParsingMessageConsumer.builder()
                .queueSetup(queueSetup)
                .amazonParsingSQSResourceConfig(amazonParsingSQSResourceConfig)
                .sqs(smsParsingBeanAmazonSQS)
                .smsParsingQueueMessageProcessor(smsParsingQueueMessageProcessor)
                .build();
    }

}
