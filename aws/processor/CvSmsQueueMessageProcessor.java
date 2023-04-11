package com.freecharge.smsprofilerservice.aws.processor;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import com.fasterxml.jackson.core.type.TypeReference;
import com.freecharge.smsprofilerservice.constant.ErrorCodeAndMessage;
import com.freecharge.smsprofilerservice.converter.SmsRequestConverter;
import com.freecharge.smsprofilerservice.filter.SendersFilter;
import com.freecharge.smsprofilerservice.model.SmsData;
import com.freecharge.smsprofilerservice.model.SmsInfo;
import com.freecharge.smsprofilerservice.service.impl.ShortcodeCounterCacheService;
import com.freecharge.smsprofilerservice.service.impl.SmsTokenizerManager;
import com.freecharge.smsprofilerservice.utils.JsonUtil;
import com.freecharge.smsprofilerservice.utils.ValidateBeanAnnotation;
import lombok.Builder;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
@Builder
public class CvSmsQueueMessageProcessor {

    private final SmsRequestConverter smsRequestConverter;

    private final SendersFilter sendersFilter;

    private final SmsTokenizerManager smsTokenizerManager;

    private final ShortcodeCounterCacheService shortcodeCounterCacheService;

    @Autowired
    public CvSmsQueueMessageProcessor(@NonNull final SmsRequestConverter smsRequestConverter,
                                      @NonNull final SendersFilter sendersFilter,
                                      @NonNull final SmsTokenizerManager smsTokenizerManager,
                                      @NonNull final ShortcodeCounterCacheService shortcodeCounterCacheService) {
        this.smsRequestConverter = smsRequestConverter;
        this.sendersFilter = sendersFilter;
        this.smsTokenizerManager = smsTokenizerManager;
        this.shortcodeCounterCacheService = shortcodeCounterCacheService;

    }

    public <T> boolean process(T body) {
        try {
            final SmsData smsData = JsonUtil.convertStringIntoObject((String) body, new TypeReference<SmsData>() {
            });
            log.debug("Message received from CV service is {} ", body);
            ValidateBeanAnnotation.validate(body, ErrorCodeAndMessage.INVALID_SMS_REQUEST);
            shortcodeCounterCacheService.incrementShortcodeCounterFromSmsData(smsData);
            final SmsData filteredSmsData = sendersFilter.filterActiveSendersFromSmsData(smsData);
            log.debug("Message parse : {}", filteredSmsData);
            if (!CollectionUtils.isEmpty(filteredSmsData.getUserSmsDataList())) {
                List<SmsInfo> smsInfoList = smsRequestConverter.covertSmsData(filteredSmsData);
                smsInfoList.stream().forEach(smsInfo -> smsTokenizerManager.process(smsInfo));
            } else {
                log.debug("No whitelisted sender msg present");
            }

            return true;
        } catch (Exception e) {
            log.error("Error while processing message with exception {} and {}", e.getMessage(), e);
           throw e;
        }
    }
}
