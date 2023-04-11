package com.freecharge.smsprofilerservice.service;

import com.freecharge.smsprofilerservice.constant.ModelUpdateMethodEnum;
import com.freecharge.smsprofilerservice.dao.mysql.model.TemplateModel;
import com.freecharge.smsprofilerservice.request.*;
import com.freecharge.smsprofilerservice.response.*;
import lombok.NonNull;

import java.time.LocalDate;
import java.util.List;

public interface OpsService {

    List<TemplateResponse> getTemplatesPaginated(int page, int noOfEntries, Boolean trainedFlag);

    List<TemplateResponse> getInactiveTemplatesPaginated(int page, int noOfEntries);

    List<TemplateResponse> getSimilarityTemplatesPaginated(int page, int noOfEntries);

    void updateTemplate(@NonNull TemplateUpdateRequest templateUpdateRequest);

    List<DomainResponse> getDomains();

    void addDomain(@NonNull DomainRequest domainRequest);

    void updateDomain(@NonNull DomainRequest domainRequest);

    void updateSender(@NonNull SenderRequest senderRequest);

    void addSender(@NonNull SenderRequest senderRequest);

    List<SenderResponse> getAllSendersPaginated(int page, int noOfEntries);

    List<TransactionCategoryResponse> getTransactionCategory();

    void addTransactionCategory(@NonNull TransactionCategoryRequest transactionCategoryRequest);

    void updateTransactionCategory(@NonNull TransactionCategoryRequest transactionCategoryRequest);

    List<TokenResponse> getTokens(String domain);

    void addToken(@NonNull TokenRequest tokenRequest);

    void updateToken(@NonNull TokenRequest tokenRequest);

    void deleteToken(@NonNull String domainName,@NonNull String tokenName);

    TrainMessageEvaluationResponse evaluateTrainedMessage(TrainMessageEvaluationRequest trainMessageEvaluationRequest);

    Integer untrainedMessageCount();

    Integer trainedMessageCount();

    Integer stringSimilarityMessageCount();

    List<TemplateResponse> getAllTrainedMsg();

    List<TemplateResponse> getAllUnTrainedMsg();

    List<TemplateResponse> getAllStringSimilarMsg();
    
    void deleteTemplateByMessage(String message);

    void deleteTemplateBySenderName(String senderName);

    void deleteTemplateBySenderNameAndModelUpdateMethod(String senderName, ModelUpdateMethodEnum modelUpdateMethodEnum);

    List<ShortcodeCounterResult> getShortcodeInRange(LocalDate fromDate, LocalDate toDate);

    List<TemplateModel> getAllTemplatesByShortcode(String shortcode, int noOfEntries, Boolean trained);

    List<TemplateModel> getAllTemplatesByShortcodeOnCount(String shortcode, int noOfEntries, int count);

    TemplateModel getTemplateByTemplateMessage(String template);

    void deleteSenderTemplateMap(List<TemplateModel> templateModelList);

    void deleteTemplates(List<TemplateModel> templateModelList);

}
