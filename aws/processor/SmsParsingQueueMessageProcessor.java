package com.freecharge.smsprofilerservice.aws.processor;

import com.fasterxml.jackson.core.type.TypeReference;
import com.freecharge.smsprofilerservice.constant.ErrorCodeAndMessage;
import com.freecharge.smsprofilerservice.converter.SmsRequestConverter;
import com.freecharge.smsprofilerservice.dao.mysql.entity.DailyCount;
import com.freecharge.smsprofilerservice.dao.mysql.repository.DailyCountRepository;
import com.freecharge.smsprofilerservice.filter.SendersFilter;
import com.freecharge.smsprofilerservice.model.SmsInfo;
import com.freecharge.smsprofilerservice.model.SmsRequest;
import com.freecharge.smsprofilerservice.service.impl.ShortcodeCounterCacheService;
import com.freecharge.smsprofilerservice.service.impl.SmsTokenizerManager;
import com.freecharge.smsprofilerservice.utils.JsonUtil;
import com.freecharge.smsprofilerservice.utils.ValidateBeanAnnotation;
import lombok.Builder;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Map;


@Component
@Slf4j
@Builder
public class SmsParsingQueueMessageProcessor {

    private final SmsRequestConverter smsRequestConverter;

    private final SendersFilter sendersFilter;

    private final SmsTokenizerManager smsTokenizerManager;

    private final ShortcodeCounterCacheService shortcodeCacheService;


    private final DailyCountRepository dailyCountRepository;

    @Autowired
    public SmsParsingQueueMessageProcessor(@NonNull final SmsRequestConverter smsRequestConverter,
                                           @NonNull final SendersFilter sendersFilter, @NonNull final SmsTokenizerManager smsTokenizerManager,
                                           @NonNull final ShortcodeCounterCacheService shortcodeCacheService,
                                          DailyCountRepository dailyCountRepository
	) {
	this.smsRequestConverter = smsRequestConverter;
	this.sendersFilter = sendersFilter;
	this.smsTokenizerManager = smsTokenizerManager;
	this.shortcodeCacheService = shortcodeCacheService;
	this.dailyCountRepository = dailyCountRepository;
    }

    public <T> boolean process(T body) {
	try {
	    String msgBody = (String) body;
	    Map<String, String> messageMap = JsonUtil.convertStringIntoObject(msgBody, Map.class);
	    String serializedMessage = messageMap.get("Message");
	    log.info("MessageReceivedFrom parsing service serializedMessage is : {}", serializedMessage);
	    final SmsRequest smsRequest = JsonUtil.convertStringIntoObject(serializedMessage,
		    new TypeReference<SmsRequest>() {
		    });
		SimpleDateFormat sdf = new SimpleDateFormat("dd-MM-yyyy");

		String date = sdf.format(new Date()) + "_RAW_MESSAGE_COUNT";
		DailyCount dailyCount = (dailyCountRepository.findByDate(date) == null) ? new DailyCount(date, 0) : dailyCountRepository.findByDate(date);
		dailyCount.setCount(dailyCount.getCount() + smsRequest.getSmsDetails().size());
		dailyCountRepository.save(dailyCount);
	    ValidateBeanAnnotation.validate(body, ErrorCodeAndMessage.INVALID_SMS_REQUEST);
	    shortcodeCacheService.incrementShortcodeCounterFromSmsRequest(smsRequest);

	    final SmsRequest filteredSmsRequest = sendersFilter.filterActiveSendersFromSmsRequest(smsRequest);

	    if (!CollectionUtils.isEmpty(filteredSmsRequest.getSmsDetails())) {
		List<SmsInfo> smsInfoList = smsRequestConverter.covertSmsRequest(filteredSmsRequest);
			String date2 = sdf.format(new Date()) + "_POST_SENDER_FILTER_COUNT";
			DailyCount dailyCount2 = (dailyCountRepository.findByDate(date2) == null) ? new DailyCount(date2, 0) : dailyCountRepository.findByDate(date2);
			dailyCount2.setCount(dailyCount2.getCount() + smsInfoList.size());
			dailyCountRepository.save(dailyCount2);
		smsInfoList.stream().forEach(smsInfo -> {

			smsTokenizerManager.process(smsInfo);
		});
	    } else {
			log.debug("No whitelisted sender msg present");
	    }

	    return true;
	} catch (Exception e) {
	    log.error("Error while processing message with exceptionMessage {} and completeException {}",
		    e.getMessage(), e);
	    throw e;
	}
    }
}
