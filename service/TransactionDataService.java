package com.freecharge.smsprofilerservice.service;

import com.freecharge.smsprofilerservice.dao.dynamodb.model.TransactionalDataModel;
import com.freecharge.smsprofilerservice.request.RecategorizeRequest;
import com.freecharge.smsprofilerservice.request.TtlRequest;
import com.freecharge.smsprofilerservice.response.TransactionalDataResponse;

import java.time.LocalDate;
import java.util.Date;
import java.util.List;
import java.util.Map;

public interface TransactionDataService {

    Double getResponse(String imsId, String transactionCategory, Map<String, String> txnCatMap);

    Double getRangedResponse(String imsId, String transactionCategory, Map<String, String> txnCatMap, LocalDate fromDate, LocalDate toDate);

    List<TransactionalDataResponse> getRangedRecords(String imsId, String transactionCategory, LocalDate fromDate, LocalDate toDate);

    void recategorize(RecategorizeRequest recategorizeRequest);

    void insertMessage(List<TransactionalDataModel> transactionalDataModels);

    void updateTtl(TtlRequest ttlRequest);

    Map<String, Object> getTemplateData(String hashcode, Integer noOfRecords, Date fromDate, Date toDate);
}
