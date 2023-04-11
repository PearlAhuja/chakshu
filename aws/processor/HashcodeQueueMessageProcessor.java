package com.freecharge.smsprofilerservice.aws.processor;

import java.util.Objects;

import io.micrometer.core.instrument.util.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.type.TypeReference;
import com.freecharge.smsprofilerservice.constant.ErrorCodeAndMessage;
import com.freecharge.smsprofilerservice.model.HashcodeSqsData;
import com.freecharge.smsprofilerservice.service.impl.BacklogSmsProcessor;
import com.freecharge.smsprofilerservice.utils.JsonUtil;
import com.freecharge.smsprofilerservice.utils.ValidateBeanAnnotation;
import lombok.Builder;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;


@Component
@Slf4j
@Builder
public class HashcodeQueueMessageProcessor {

    private final BacklogSmsProcessor backlogSmsProcessor;

    @Autowired

    public HashcodeQueueMessageProcessor(@NonNull final BacklogSmsProcessor backlogSmsProcessor) {
        this.backlogSmsProcessor = backlogSmsProcessor;
    }



    public <T> boolean process(T body) {
        try {
            final HashcodeSqsData hashcodeSqsData = JsonUtil.convertStringIntoObject((String) body,
                    new TypeReference<HashcodeSqsData>() {
            });
            log.info("Message received from Hashcode sqs is {} ", body);
            ValidateBeanAnnotation.validate(body, ErrorCodeAndMessage.INVALID_SMS_REQUEST);
            if (Objects.nonNull(hashcodeSqsData) && !StringUtils.isEmpty(hashcodeSqsData.getHashcode())) {
                backlogSmsProcessor.processBacklog(hashcodeSqsData.getHashcode());
            } else {
                log.debug("Received blank or null hashcode");
            }

            return true;
        } catch (Exception e) {
            log.error("Error while processing message with exception {} and {}", e.getMessage(), e);
            throw  e;
        }
    }
}
