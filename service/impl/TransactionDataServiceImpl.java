package com.freecharge.smsprofilerservice.service.impl;


import com.freecharge.fccreditvidyaservice.request.MedhasBaseRequest;
import com.freecharge.fccreditvidyaservice.response.passbook.Sms;
import com.freecharge.smsprofilerservice.constant.ErrorCodeAndMessage;
import com.freecharge.smsprofilerservice.dao.dynamodb.mapper.TransactionalDataMapper;
import com.freecharge.smsprofilerservice.dao.dynamodb.model.TransactionalDataModel;
import com.freecharge.smsprofilerservice.dao.dynamodb.service.TransactionalDataServiceDao;
import com.freecharge.smsprofilerservice.dao.mysql.service.TemplateServiceDao;
import com.freecharge.smsprofilerservice.exception.EntityNotFoundException;
import com.freecharge.smsprofilerservice.exception.InternalError;
import com.freecharge.smsprofilerservice.request.RecategorizeRequest;
import com.freecharge.smsprofilerservice.request.TtlRequest;
import com.freecharge.smsprofilerservice.response.TransactionalDataResponse;
import com.freecharge.smsprofilerservice.rest.impl.CreditVidyaRestImpl;
import com.freecharge.smsprofilerservice.rest.response.CvRealTimeMsgResponse;
import com.freecharge.smsprofilerservice.service.TransactionDataService;
import com.freecharge.smsprofilerservice.utils.DateUtil;
import com.freecharge.vault.PropertiesConfig;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.utils.CollectionUtils;
import software.amazon.awssdk.utils.StringUtils;

import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

@Slf4j
@Component
public class TransactionDataServiceImpl implements TransactionDataService {

    private final TransactionalDataServiceDao transactionalDataServiceDao;

    private final TemplateServiceDao templateServiceDao;

    private final SmsTokenizerManager smsTokenizerManager;

    private String globalSecondaryIndexName;

    private final CreditVidyaRestImpl creditVidyaRestAPI;

    @Autowired
    public TransactionDataServiceImpl(@NonNull final TransactionalDataServiceDao transactionalDataServiceDao,
                                      @NonNull final SmsTokenizerManager smsTokenizerManager,
                                      @NonNull final TemplateServiceDao templateServiceDao,
                                      @NonNull final CreditVidyaRestImpl creditVidyaRestAPI,
                                      @Qualifier("applicationProperties") final PropertiesConfig applicationProperties) {
        this.transactionalDataServiceDao = transactionalDataServiceDao;
        this.smsTokenizerManager = smsTokenizerManager;
        this.templateServiceDao = templateServiceDao;
        this.creditVidyaRestAPI = creditVidyaRestAPI;
        final Map<String, Object> awsProperties = applicationProperties.getProperties();
        this.globalSecondaryIndexName = (String) awsProperties.get("aws.dynamo.db.secondaryIndex.Name");
    }

    @Override
    public Double getResponse(String imsId, String transactionCategory, Map<String, String> txnCatMap) {
        LocalDate fromDate = LocalDate.now().plusMonths(-6);
        LocalDate toDate = LocalDate.now();
        List<TransactionalDataModel> model = transactionalDataServiceDao.getTransactionalDataBetween(imsId, DateUtil.startOfDayInMilliSecond(fromDate), DateUtil.endOfDayInMilliSecond(toDate));
        if (Objects.isNull(model) || model.isEmpty()) {
            throw new EntityNotFoundException(ErrorCodeAndMessage.ENTITY_NOT_FOUND);
        }
        try {
            List<TransactionalDataModel> filteredModel = model.stream().filter(e -> e.getTransactionCategory().equals(transactionCategory)).collect(Collectors.toList());
            long days = diffBetweenOldestAndLatestDate(filteredModel);
            AtomicReference<Double> response = new AtomicReference<>(0.0);
            if (filteredModel != null) {
                filteredModel.stream().forEach(e -> {
                    if (e.getDomain() != null && txnCatMap.get(e.getDomain()) != null && e.getTokens().get(txnCatMap.get(e.getDomain())) != null) {
                        response.updateAndGet(v -> v + Double.parseDouble(e.getTokens().get(txnCatMap.get(e.getDomain()))));
                    }
                });
            }
            return days == 0 ? response.get() : response.get() / days;
        } catch (Exception e) {
            log.error("Error occurred while calculating response for : {} with exception : {}", transactionCategory, e);
            throw new InternalError(ErrorCodeAndMessage.INTERNAL_SERVER_ERROR);
        }
    }

    @Override
    public Double getRangedResponse(String imsId, String transactionCategory, Map<String, String> txnCatMap, LocalDate fromDate, LocalDate toDate) {
        List<TransactionalDataModel> model = transactionalDataServiceDao.getTransactionalDataBetween(imsId, DateUtil.startOfDayInMilliSecond(fromDate), DateUtil.endOfDayInMilliSecond(toDate));
        if (Objects.isNull(model) || model.isEmpty()) {
            throw new EntityNotFoundException(ErrorCodeAndMessage.ENTITY_NOT_FOUND);
        }
        try {
            List<TransactionalDataModel> filteredModel = model.stream().filter(e -> e.getTransactionCategory().equals(transactionCategory)).collect(Collectors.toList());
            AtomicReference<Double> response = new AtomicReference<>(0.0);
            if (filteredModel != null) {
                filteredModel.stream().forEach(e -> {
                    if (e.getDomain() != null && txnCatMap.get(e.getDomain()) != null && e.getTokens().get(txnCatMap.get(e.getDomain())) != null) {
                        response.updateAndGet(v -> v + Double.parseDouble(e.getTokens().get(txnCatMap.get(e.getDomain()))));
                    }
                });
            }
            return response.get();
        } catch (Exception e) {
            log.error("Error occurred while calculating ranged response for : {} with exception : {}", transactionCategory, e.getMessage());
            throw new InternalError(ErrorCodeAndMessage.INTERNAL_SERVER_ERROR);
        }
    }

    @Override
    public List<TransactionalDataResponse> getRangedRecords(String imsId, String transactionCategory, LocalDate fromDate, LocalDate toDate) {
        try {
            List<TransactionalDataModel> messageModels = transactionalDataServiceDao.getTransactionalDataBetween(imsId, DateUtil.startOfDayInMilliSecond(fromDate), DateUtil.endOfDayInMilliSecond(toDate));
            List<TransactionalDataModel> filteredTransactionModel = messageModels.stream().filter(e -> e.getTransactionCategory().equals(transactionCategory)).collect(Collectors.toList());
            filteredTransactionModel.forEach(e -> smsTokenizerManager.sanitizeTokenValue(e.getTokens(), e.getDomain()));
            return filteredTransactionModel.stream().map(TransactionalDataMapper::toResponse).collect(Collectors.toList());
        } catch (Exception e) {
            log.error("Error occurred while getting ranged response for : {} with exception : {}", transactionCategory, e);
            throw new InternalError(ErrorCodeAndMessage.INTERNAL_SERVER_ERROR);
        }
    }

    @Override
    public void recategorize(RecategorizeRequest recategorizeRequest) {
        TransactionalDataModel transactionalDataModel = transactionalDataServiceDao.getTransactionalData(recategorizeRequest.getImsId(), recategorizeRequest.getDynamoHashcode());
        if (Objects.isNull(transactionalDataModel)) {
            throw new EntityNotFoundException(ErrorCodeAndMessage.ENTITY_NOT_FOUND);
        }
        if (Objects.nonNull(recategorizeRequest.getNewCategory())) {
            transactionalDataModel.setTransactionCategory(recategorizeRequest.getNewCategory().toUpperCase());
        }
        if (Objects.nonNull(recategorizeRequest.getNewSubcategory())) {
            transactionalDataModel.setTransactionSubcategory(recategorizeRequest.getNewSubcategory().toUpperCase());
        }
        try {
            transactionalDataServiceDao.save(transactionalDataModel);
        } catch (Exception e) {
            log.error("Error occurred while saving the record : {} with exception : {}", transactionalDataModel, e);
            throw new InternalError(ErrorCodeAndMessage.INTERNAL_SERVER_ERROR);
        }
    }

    @Override
    public void insertMessage(List<TransactionalDataModel> transactionalDataModel) {
        try {
            transactionalDataModel.forEach(transactionalDataServiceDao::save);
        } catch (Exception e) {
            log.error("Error occurred while saving the record : {} with exception : {}", transactionalDataModel, e);
            throw new InternalError(ErrorCodeAndMessage.ENTITY_SAVING_ERROR);
        }
    }

    @Override
    public void updateTtl(TtlRequest ttlRequest) {
        try {
            transactionalDataServiceDao.updateTimeToLive(ttlRequest.getToActivateTtl(), ttlRequest.getTableName(), ttlRequest.getAttributeName());
        } catch (Exception e) {
            log.error("Error occurred while updating ttl for table: {} , attribute: {} with activate flag : {}", ttlRequest.getTableName(), ttlRequest.getAttributeName(), ttlRequest.getToActivateTtl());
            throw new InternalError(ErrorCodeAndMessage.INTERNAL_SERVER_ERROR);
        }
    }


    private Long getOldestDate(List<TransactionalDataModel> model) {
        return model.stream().min(Comparator.comparingLong(e -> e.getMsgTime())).get().getMsgTime();
    }

    private Long getLatestDate(List<TransactionalDataModel> model) {
        return model.stream().max(Comparator.comparingLong(e -> e.getMsgTime())).get().getMsgTime();
    }

    private long diffBetweenOldestAndLatestDate(List<TransactionalDataModel> model) {
        if (Objects.isNull(model) || model.isEmpty()) {
            return 0;
        }
        Long oldestDate = getOldestDate(model);
        Long latestDate = getLatestDate(model);
        return DateUtil.getDaysBetweenTwoDates(DateUtil.getDateFromMillis(oldestDate), DateUtil.getDateFromMillis(latestDate));
    }

    @Override
    public Map<String, Object> getTemplateData(@NonNull final String template,
                                               @NonNull final Integer noOfRecords,
                                               @NonNull final Date fromDate,
                                               @NonNull final Date toDate) {
        Map<String, Object> response = new HashMap<>();
        final String hashcode = templateServiceDao.getHashcodeByTemplate(template);
        if (StringUtils.isBlank(hashcode)) {
            return response;
        }
        final List<TransactionalDataModel> transactionalDataModels =
                transactionalDataServiceDao.queryDynamoBasedOnSecondaryIndex(globalSecondaryIndexName,
                        hashcode, noOfRecords, fromDate.getTime(), toDate.getTime());
        if (CollectionUtils.isNullOrEmpty(transactionalDataModels)) {
            return response;
        }
        MedhasBaseRequest medhasBaseRequest = new MedhasBaseRequest();
        medhasBaseRequest.setImsId(transactionalDataModels.get(0).getImsId());
        medhasBaseRequest.setSmsList(transactionalDataModels.stream().map(model -> {
            Sms sms = new Sms();
            sms.setAddress(model.getSender());
            sms.setBody(model.getMsg());
            sms.setTime(model.getMsgTime());
            return sms;
        }).collect(Collectors.toList()));
        CvRealTimeMsgResponse realTimeMessageResponse = creditVidyaRestAPI.getRealTimeMessageResponse(medhasBaseRequest);
        List<TransactionalDataResponse> transactionalDataResponses = transactionalDataModels.stream().map(TransactionalDataMapper::toResponse).collect(Collectors.toList());
        response.put("cv-response", realTimeMessageResponse != null ? realTimeMessageResponse.getResult() : realTimeMessageResponse);
        response.put("ml-response", transactionalDataResponses);
        return response;
    }
}
