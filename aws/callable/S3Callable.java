package com.freecharge.smsprofilerservice.aws.callable;

import com.freecharge.smsprofilerservice.aws.accessor.S3Accessor;
import com.freecharge.smsprofilerservice.dao.mysql.model.TemplateModel;
import com.freecharge.smsprofilerservice.service.OpsService;
import com.freecharge.smsprofilerservice.utils.JsonUtil;
import lombok.Data;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.concurrent.Callable;

@Slf4j
@Data
public class S3Callable implements Callable<String> {

    private final OpsService opsService;

    private final S3Accessor s3Accessor;

    private List<TemplateModel> templateModelList;

    private String sender;

    public S3Callable(@NonNull final OpsService opsService,
                      @NonNull final S3Accessor s3Accessor,
                      @NonNull final List<TemplateModel> templateModelList,
                      @NonNull final String sender) {
        this.opsService = opsService;
        this.s3Accessor = s3Accessor;
        this.templateModelList = templateModelList;
        this.sender = sender;
    }

    @Override
    public String call() throws Exception {
        log.debug("call method execution");
//        uploadIntoS3(templateModelList);
        opsService.deleteSenderTemplateMap(templateModelList);
        opsService.deleteTemplates(templateModelList);
        return "200-OK";
    }

    private void uploadIntoS3(@NonNull final List<TemplateModel> templates) {
        log.info("Uploading Data to S3 with smsInfo size is : {}", templates.size());
        final Long start = System.currentTimeMillis();
        UUID uuid = UUID.randomUUID();
        final Date dates = new Date(System.currentTimeMillis());
        final Calendar calendar = GregorianCalendar.getInstance();
        calendar.setTime(dates);
        final int year = calendar.get(Calendar.YEAR);
        final int month = calendar.get(Calendar.MONTH) + 1;
        final int date = calendar.get(Calendar.DATE);
        final int hour = calendar.get(Calendar.HOUR_OF_DAY);
        final String bucketKey = "untrained-dump" + "/" + sender + "/" + year + "/" + month + "/" + date + "/" + hour + "/" + dates + uuid + ".txt";
        log.info("Saving Data for key {}", bucketKey);
        s3Accessor.saveData(bucketKey, JsonUtil.writeValueAsString(templates).getBytes());
        final Long end = System.currentTimeMillis();
        log.info("Time taken to upload the records {}, ", end - start);
    }
}
