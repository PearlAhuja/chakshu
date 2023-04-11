package com.freecharge.smsprofilerservice.service;

import com.freecharge.fctoken.context.AuthorizationContext;
import com.freecharge.smsprofilerservice.dao.mysql.model.IncomeModel;
import com.freecharge.smsprofilerservice.model.IncomeSmsInfoModel;
import com.freecharge.smsprofilerservice.model.SmsInfo;
import com.freecharge.smsprofilerservice.request.DataRequiredRequest;
import com.freecharge.smsprofilerservice.request.IncomeSmsRequest;
import com.freecharge.smsprofilerservice.request.IncomeSmsRequestV2;
import com.freecharge.smsprofilerservice.response.DataRequiredResponse;

import javax.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;

public interface IncomeService {
    IncomeSmsInfoModel uploadDataAndFilterMessages(IncomeSmsRequest incomeSmsRequest);

    IncomeSmsInfoModel getFilteredSmsInfos(IncomeSmsRequest request);

    IncomeSmsInfoModel uploadDataAndFilterMessagesV2(IncomeSmsRequestV2 request);

    IncomeSmsInfoModel getFilteredSmsInfosV2(IncomeSmsRequestV2 request);

    void prepareIncomeModelAndSaveInDb(String imsId, Double computedIncome, String model, String modelInfo, String state);

    void updateStateAndIncomeInDb(double income, String state,  String model, String modelInfo, String ims_id,String updatedBy);

    void computeAndUpdateIncomeFromMsgs(List<SmsInfo> smsInfoList, IncomeSmsRequest incomeSmsRequest);

    void computeAndUpdateIncomeFromMsgsV2(List<SmsInfo> smsInfoList, IncomeSmsRequestV2 incomeSmsRequestV2);

    DataRequiredResponse getDataRequiredResponse(DataRequiredRequest request);

    String readDataFromS3(IncomeModel incomeModel);

    IncomeSmsRequest getIncomeSmsRequest(HttpServletRequest request, AuthorizationContext tokenizerAuthorizationContext);
    IncomeSmsRequestV2 getIncomeSmsRequestV2(HttpServletRequest request, AuthorizationContext tokenizerAuthorizationContext);

    Map<String, String> getTokenFromNameFinder(String trainedMsg, String testMsg);
}
