package com.freecharge.smsprofilerservice.service.impl;

import com.freecharge.smsprofilerservice.aws.config.AmazonParsingSQSResourceConfig;
import com.freecharge.smsprofilerservice.aws.config.AmazonS3ResourceConfig;
import com.freecharge.smsprofilerservice.dao.s3.service.S3AmazonService;
import com.freecharge.smsprofilerservice.model.SmsDetail;
import com.freecharge.smsprofilerservice.model.SmsInfo;
import com.freecharge.smsprofilerservice.model.SmsRequest;
import com.freecharge.smsprofilerservice.service.callables.S3GetDataCallable;
import com.freecharge.smsprofilerservice.utils.JsonUtil;
import com.google.common.collect.Lists;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;
import software.amazon.awssdk.utils.CollectionUtils;

import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

@Slf4j
@Service
public class BacklogSmsProcessor {


    private final S3AmazonService s3AmazonService;

    private final AmazonParsingSQSResourceConfig parsingSQSResourceConfig;

    private final SqsClient amazonSQS;

    private ExecutorService executorService;

    private Integer bucketPartitionNumber;

    @Autowired
    public BacklogSmsProcessor(@NonNull final S3AmazonService s3AmazonService,
                               @NonNull final AmazonParsingSQSResourceConfig parsingSQSResourceConfig,
                               @NonNull @Qualifier("smsParsingBean") final SqsClient amazonSQS,
                               @NonNull final AmazonS3ResourceConfig amazonS3ResourceConfig) {
        this.s3AmazonService = s3AmazonService;
        this.parsingSQSResourceConfig = parsingSQSResourceConfig;
        this.amazonSQS = amazonSQS;
        this.executorService = Executors.newFixedThreadPool(amazonS3ResourceConfig.getDownloadThreadCount());
        this.bucketPartitionNumber = amazonS3ResourceConfig.getBucketPartitionNumber();
    }

    public void processBacklog(@NonNull final String hashcode) {
        final List<String> buckets = s3AmazonService.listBuckets(hashcode);
        log.info("List of buckets are : {}", buckets);
        final List<List<String>> bucketBag = Lists.partition(buckets, bucketPartitionNumber);
        final List<S3GetDataCallable> futureTasks = new LinkedList<>();
        bucketBag.stream().forEach(bulkData -> {
            final S3GetDataCallable s3GetDataCallable = new S3GetDataCallable(bulkData, s3AmazonService);
            futureTasks.add(s3GetDataCallable);
        });
        try {
            final List<Future<List<SmsInfo>>> futures = executorService.invokeAll(futureTasks);
            futures.stream().forEach(future -> {
                try {
                    final List<SmsInfo> smsInfoResult = future.get();
                    if(!CollectionUtils.isNullOrEmpty(smsInfoResult)) {
                        uploadMessageToSqs(smsInfoResult);
                    }
                } catch (Exception e) {
                    log.error("Error in converting: {}", e.getMessage());
                }
            });
        }catch (Exception e) {
            log.error("Error while getting future: {} ", e.getMessage());
        } finally {
            executorService.shutdown();
        }
    }

    private void uploadMessageToSqs(@NonNull final List<SmsInfo> smsInfo) {
        log.info("Uploading {} to sqs", smsInfo);
        smsInfo.stream().forEach(e -> {
            final String msg = generateMessage(e);
            amazonSQS.sendMessage(SendMessageRequest.builder()
                    .messageBody(msg)
                    .queueUrl(parsingSQSResourceConfig.getSqsUrl()).build());
        });
    }

    private String generateMessage(@NonNull final SmsInfo smsInfo) {
        final SmsRequest request = new SmsRequest();
        request.setAccessToken(smsInfo.getImsId());
        final SmsDetail smsDetail = new SmsDetail();
        smsDetail.setMsg(smsInfo.getMsg());
        smsDetail.setSender(smsInfo.getSender());
        smsDetail.setDate(smsInfo.getMsgTime());
        request.setSmsDetails(Collections.singletonList(smsDetail));
        final Map<String, String> smsRequestMap = new HashMap<>();
        smsRequestMap.put("Message",JsonUtil.writeValueAsString(request));
        return JsonUtil.writeValueAsString(smsRequestMap);
    }
}
