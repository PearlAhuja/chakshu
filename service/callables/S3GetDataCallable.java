package com.freecharge.smsprofilerservice.service.callables;

import com.freecharge.smsprofilerservice.dao.s3.service.S3AmazonService;
import com.freecharge.smsprofilerservice.model.SmsInfo;
import lombok.Data;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.Callable;

@Slf4j
@Data
public class S3GetDataCallable implements Callable<List<SmsInfo>> {

    private List<String> buckets;
    private S3AmazonService service;


    public S3GetDataCallable(@NonNull final List<String> buckets,
                             @NonNull final S3AmazonService service) {
        this.buckets = buckets;
        this.service = service;
    }

    @Override
    public List<SmsInfo> call() throws Exception {
        final List<SmsInfo> smsInfos = new LinkedList<>();
        buckets.stream().forEach(e -> {
            smsInfos.addAll(service.downloadMessage(e));
        });
        return smsInfos;
    }
}
