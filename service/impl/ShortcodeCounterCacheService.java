package com.freecharge.smsprofilerservice.service.impl;

import com.freecharge.smsprofilerservice.constant.CacheName;
import com.freecharge.smsprofilerservice.constant.ProfilerConstant;
import com.freecharge.smsprofilerservice.filter.SendersFilter;
import com.freecharge.smsprofilerservice.model.SmsData;
import com.freecharge.smsprofilerservice.model.SmsRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Component
@Slf4j
public class ShortcodeCounterCacheService {

    private CacheManager cacheManager;

    private SendersFilter sendersFilter;

    @Autowired
    public ShortcodeCounterCacheService(CacheManager cacheManager, SendersFilter sendersFilter) {
        this.cacheManager = cacheManager;
        this.sendersFilter = sendersFilter;
    }

    public void incrementShortcodeCounterFromSmsRequest(SmsRequest smsRequest){
        SmsRequest smsRequestCopy = new SmsRequest();
        smsRequestCopy.setSmsDetails(smsRequest.getSmsDetails());
        SmsRequest filteredSmsRequest = sendersFilter.filterOutGarbageShortcodesFromSmsRequest(smsRequestCopy);
        Cache shortcodeCounterCache = cacheManager.getCache(CacheName.SHORTCODE_COUNTER);
        Objects.requireNonNull(shortcodeCounterCache);
        Map<String, AtomicInteger> concurrentMap = new ConcurrentHashMap<>();
        shortcodeCounterCache.putIfAbsent(ProfilerConstant.SHORTCODE_COUNTER_KEY,concurrentMap);
        Map<String, AtomicInteger> newConcurrentMap = (Map<String, AtomicInteger>) shortcodeCounterCache.get(ProfilerConstant.SHORTCODE_COUNTER_KEY).get();
        filteredSmsRequest.getSmsDetails().forEach(smsDetail->{
            newConcurrentMap.putIfAbsent(smsDetail.getSender(), new AtomicInteger(0));
            AtomicInteger counter = newConcurrentMap.get(smsDetail.getSender());
            counter.getAndIncrement();
        });
    }

    public void incrementShortcodeCounterFromSmsData(SmsData smsData){
        SmsData smsDataCopy = new SmsData();
        smsDataCopy.setUserSmsDataList(smsData.getUserSmsDataList());
        smsDataCopy.setImsId(smsData.getImsId());
        SmsData filteredSmsData = sendersFilter.filterOutGarbageShortcodesFromSmsData(smsDataCopy);
        Cache shortcodeCounterCache = cacheManager.getCache(CacheName.SHORTCODE_COUNTER);
        Objects.requireNonNull(shortcodeCounterCache);
        Map<String, AtomicInteger> concurrentMap = new ConcurrentHashMap<>();
        shortcodeCounterCache.putIfAbsent(ProfilerConstant.SHORTCODE_COUNTER_KEY,concurrentMap);
        Map<String, AtomicInteger> newConcurrentMap = (Map<String, AtomicInteger>) shortcodeCounterCache.get(ProfilerConstant.SHORTCODE_COUNTER_KEY).get();
        filteredSmsData.getUserSmsDataList().forEach(userSmsData->{
            newConcurrentMap.putIfAbsent(userSmsData.getSender(), new AtomicInteger(0));
            AtomicInteger counter = newConcurrentMap.get(userSmsData.getSender());
            counter.getAndIncrement();
        });

    }

    public void incrementShortcodeCounterInCacheBy(String shortcode, Integer value){
        Cache shortcodeCounterCache = cacheManager.getCache(CacheName.SHORTCODE_COUNTER);
        Objects.requireNonNull(shortcodeCounterCache);
        Map<String, AtomicInteger> concurrentMap = new ConcurrentHashMap<>();
        concurrentMap.putIfAbsent(shortcode, new AtomicInteger(0));
        shortcodeCounterCache.putIfAbsent(ProfilerConstant.SHORTCODE_COUNTER_KEY,concurrentMap);
        Map<String,AtomicInteger> map = (Map<String, AtomicInteger>) shortcodeCounterCache.get(ProfilerConstant.SHORTCODE_COUNTER_KEY).get();
        AtomicInteger counter = map.get(shortcode);
        counter.getAndUpdate((x) -> (int) (x + value));
    }
}
