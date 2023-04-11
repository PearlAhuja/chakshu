package com.freecharge.smsprofilerservice.service.impl;

import com.freecharge.smsprofilerservice.constant.CacheName;
import com.freecharge.smsprofilerservice.constant.ErrorCodeAndMessage;
import com.freecharge.smsprofilerservice.constant.ModelUpdateMethodEnum;
import com.freecharge.smsprofilerservice.constant.RedisKeyName;
import com.freecharge.smsprofilerservice.dao.cache.CacheService;
import com.freecharge.smsprofilerservice.dao.cache.RedisCacheService;
import com.freecharge.smsprofilerservice.dao.mysql.mapper.*;
import com.freecharge.smsprofilerservice.dao.mysql.model.*;
import com.freecharge.smsprofilerservice.dao.mysql.service.*;
import com.freecharge.smsprofilerservice.engine.template.enums.SanitizerVariety;
import com.freecharge.smsprofilerservice.exception.BadRequestException;
import com.freecharge.smsprofilerservice.exception.EntityNotFoundException;
import com.freecharge.smsprofilerservice.exception.InternalError;
import com.freecharge.smsprofilerservice.filter.SendersFilter;
import com.freecharge.smsprofilerservice.request.*;
import com.freecharge.smsprofilerservice.response.*;
import com.freecharge.smsprofilerservice.service.OpsService;
import com.freecharge.smsprofilerservice.service.impl.nlp.ModelRunnerService;
import com.freecharge.smsprofilerservice.service.impl.train.NameFinderModelTrainer;
import com.freecharge.smsprofilerservice.utils.JsonUtil;
import com.freecharge.smsprofilerservice.validator.DataTypeValidator;
import com.freecharge.smsprofilerservice.validator.TrainedMessageValidator;
import com.google.common.base.Predicates;
import lombok.NonNull;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import opennlp.tools.ml.EventTrainer;
import opennlp.tools.namefind.NameFinderME;
import opennlp.tools.namefind.NameSampleDataStream;
import opennlp.tools.namefind.TokenNameFinderModel;
import opennlp.tools.util.ObjectStreamUtils;
import opennlp.tools.util.TrainingParameters;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.utils.CollectionUtils;

import java.io.IOException;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Component
@Slf4j
public class OpsServiceImpl implements OpsService {

    @Value("${min.token.template.need}")
    @Setter
    private int minVariableNeedToProceed;

    @Setter
    @Value("${shortcode.count.fetch.record:10}")
    private Integer shortcodeFetchLimit;

    private TransactionCategoryServiceDao transactionCategoryDao;

    private TokenServiceDao tokenServiceDao;

    private SenderServiceDao senderDao;

    private TemplateServiceDao templateServiceDao;

    private DomainServiceDao domainServiceDao;

    private ModelRunnerService modelRunnerService;

    private SenderTemplateMapDao senderTemplateMapDao;

    private NameFinderModelTrainer modelTrainer;

//    private JwtUtil jwtUtil;

    private RedisCacheService redisCacheService;

    private ShortcodeCounterServiceDao counterServiceDao;

    private SendersFilter sendersFilter;

    private CacheService cacheService;

    @Autowired
    public OpsServiceImpl(TransactionCategoryServiceDao transactionCategoryDao,
                          TokenServiceDao tokenServiceDao, SenderServiceDao senderDao,
                          TemplateServiceDao templateServiceDao, DomainServiceDao domainServiceDao,
                          ModelRunnerService modelRunnerService, SenderTemplateMapDao senderTemplateMapDao,
                          NameFinderModelTrainer modelTrainer, /*JwtUtil jwtUtil,*/
                          @NonNull final RedisCacheService redisCacheService, ShortcodeCounterServiceDao counterServiceDao, SendersFilter sendersFilter, CacheService cacheService) {
        this.transactionCategoryDao = transactionCategoryDao;
        this.tokenServiceDao = tokenServiceDao;
        this.senderDao = senderDao;
        this.templateServiceDao = templateServiceDao;
        this.domainServiceDao = domainServiceDao;
        this.modelRunnerService = modelRunnerService;
        this.modelTrainer = modelTrainer;
//        this.jwtUtil = jwtUtil;
        this.senderTemplateMapDao = senderTemplateMapDao;
        this.redisCacheService = redisCacheService;
        this.counterServiceDao = counterServiceDao;
        this.sendersFilter = sendersFilter;
        this.cacheService = cacheService;
    }

    @Override
    public List<TemplateResponse> getTemplatesPaginated(int page, int noOfEntries, Boolean trainedFlag) {
        Long start = System.currentTimeMillis();
        log.info("getTemplatesPaginated in service started at {}", start);
        int offset = page * noOfEntries;
        List<TemplateModel> templateModels = templateServiceDao.getAllTemplatesPaginated(offset, noOfEntries, trainedFlag);
         Long end = System.currentTimeMillis();
        log.info("getTemplatesPaginated in service ended at {} and total time taken is {}", end, (end - start));
        return templateModels.stream().map(TemplateMapper::toReponse).collect(Collectors.toList());
    }

    @Override
    public List<TemplateResponse> getInactiveTemplatesPaginated(int page, int noOfEntries) {
        Long start = System.currentTimeMillis();
        log.info("getInactiveTemplatesPaginated in service started at {}", start);
        int offset = page * noOfEntries;
        List<TemplateModel> templateModels = templateServiceDao.getAllInactiveTemplatesPaginated(offset, noOfEntries);
         Long end = System.currentTimeMillis();
        log.info("getInactiveTemplatesPaginated ended at {} and total time taken is {}", end, (end - start));
        return templateModels.stream().map(TemplateMapper::toReponse).collect(Collectors.toList());
    }

    @Override
    public List<TemplateResponse> getSimilarityTemplatesPaginated(int page, int noOfEntries) {
        Long start = System.currentTimeMillis();
        log.info("getSimilarityTemplatesPaginated in service started at {}", start);
        int offset = page * noOfEntries;
        List<TemplateModel> templateModels = templateServiceDao.getAllSimilarityTemplatesPaginated(offset, noOfEntries);
         Long end = System.currentTimeMillis();
        log.info("getSimilarityTemplatesPaginated in service ended at {} and total time taken is {}", end, (end-start));
        return templateModels.stream().map(TemplateMapper::toReponse).collect(Collectors.toList());
    }

    @Override
    public void updateTemplate(@NonNull TemplateUpdateRequest templateUpdateRequest) {
        Long start = System.currentTimeMillis();
        log.info("updateTemplate in service started at {}", start);
        TemplateModel templateModel = templateServiceDao.getTemplate(templateUpdateRequest.getHashCode());
        if (Objects.isNull(templateModel)) {
            throw new EntityNotFoundException(ErrorCodeAndMessage.ENTITY_NOT_FOUND);
        }
        log.info("Original Message : {}", templateModel.getOriginalMessage());
        if((templateModel.getOriginalMessage().contains("\n"))||(templateModel.getOriginalMessage().contains("\r"))||(templateModel.getOriginalMessage().contains("\n\r")))
        {
            String originalMessage = templateModel.getOriginalMessage().replace("\n", " ").replace("\r", " " ).replace("\n\r"," ");
            templateModel.setOriginalMessage(originalMessage);
        }
        templateModel.setModelUpdatedMethod(ModelUpdateMethodEnum.TrainingData);
        log.info("Original Message after space removal : {}", templateModel.getOriginalMessage());
        if (Objects.nonNull(templateUpdateRequest.getDomainName())) {
            DomainModel domainModel = domainServiceDao.getDomainByDomainName(templateUpdateRequest.getDomainName());
            if (Objects.isNull(domainModel)) {
                throw new BadRequestException(ErrorCodeAndMessage.INVALID_DOMAIN);
            }
            templateModel.setDomainId(domainModel);
        }
        if (Objects.nonNull(templateUpdateRequest.getCategory()) && Objects.nonNull(templateUpdateRequest.getSubcategory())) {
            TransactionCategoryModel transactionCategoryModel = transactionCategoryDao.getTransactionCategoryBySubCategory(templateUpdateRequest.getCategory(), templateUpdateRequest.getSubcategory());
            if (Objects.isNull(transactionCategoryModel)) {
                throw new BadRequestException(ErrorCodeAndMessage.INVALID_CAT_SUBCAT);
            }
            templateModel.setTransactionCategoryId(transactionCategoryModel);
        }
        if (Objects.nonNull(templateUpdateRequest.getIsActive())) {
            templateModel.setActive(templateUpdateRequest.getIsActive());
        }
        if (Objects.nonNull(templateUpdateRequest.getTrainedMessage())) {
            if (Objects.isNull(templateUpdateRequest.getDomainName()) && Objects.isNull(templateModel.getDomainId())) {
                throw new BadRequestException(ErrorCodeAndMessage.DOMAIN_NOT_FOUND);
            }
            String domainName = templateUpdateRequest.getDomainName() == null ? templateModel.getDomainId().getDomainName() : templateUpdateRequest.getDomainName();
            List<TokenModel> tokenModels = tokenServiceDao.getAllActiveTokensByDomain(domainName);
            if (!TrainedMessageValidator.validateTrainedMessage(templateUpdateRequest.getTrainedMessage(), tokenModels, minVariableNeedToProceed, templateModel.getOriginalMessage())) {
                throw new BadRequestException(ErrorCodeAndMessage.INVALID_TRAINED_MESSAGE);
            }

            templateModel.setTrainedMessage(templateUpdateRequest.getTrainedMessage());
        }
//        String username = jwtUtil.getUsernameFromToken(token);
//        templateModel.setUpdatedBy(username);
        try {

            templateServiceDao.save(templateModel);
            redisCacheService.evictSingleCacheValue(RedisKeyName.TEMPLATE_DATA_KEY, templateModel.getHashcode());
        } catch (Exception e) {
            log.error("Error occurred while updating template with exception : {}", e.toString());
            throw new InternalError(ErrorCodeAndMessage.ENTITY_SAVING_ERROR);
        }
         Long end = System.currentTimeMillis();
        log.info("updateTemplate in service ended at {} and total time taken is {}", end, (end - start));
    }

    @Override
    public List<DomainResponse> getDomains() {
        Long start = System.currentTimeMillis();
        log.info("getDomains in service started at {}", start);
        List<DomainResponse> domainResponseList = new ArrayList<>();
        List<DomainModel> domains = domainServiceDao.getDomains();
        if (CollectionUtils.isNullOrEmpty(domains)) {
            return domainResponseList;
        }
        domains.stream().forEach(domainModel -> {
            domainResponseList.add(DomainMapper.toResponse(domainModel));
        });
        Long end = System.currentTimeMillis();
        log.info("getDomains in service ended at {} and total time taken is {}", end, (end- start));
        return domainResponseList;
    }

    @Override
    public void addDomain(@NonNull DomainRequest domainRequest) {
        Long start = System.currentTimeMillis();
        log.info("addDomain in service started at {}", start);
        DomainModel domainModel = new DomainModel();
        domainModel.setDomainName(domainRequest.getDomainName().toUpperCase());
        domainModel.setDescription(domainRequest.getDescription());
        domainModel.setActive(domainRequest.getIsActive());
        try {
            domainServiceDao.save(domainModel);
        } catch (Exception e) {
            log.error("Error occurred while adding domain with exception : {}", e.toString());
            throw new InternalError(ErrorCodeAndMessage.ENTITY_SAVING_ERROR);
        }
         Long end = System.currentTimeMillis();
        log.info("addDomain in service ended at {} and total time taken is {}", end, (end - start));
    }

    @Override
    public void updateDomain(@NonNull DomainRequest domainRequest) {
        Long start = System.currentTimeMillis();
        log.info("updateDomain in service started at {}", start);
        DomainModel domainModel = domainServiceDao.getDomainByDomainName(domainRequest.getDomainName());
        if (Objects.isNull(domainModel)) {
            throw new EntityNotFoundException(ErrorCodeAndMessage.ENTITY_NOT_FOUND);
        }
        if (Objects.nonNull(domainRequest.getDescription())) {
            domainModel.setDescription(domainRequest.getDescription());
        }
        if (Objects.nonNull(domainRequest.getIsActive())) {
            domainModel.setActive(domainRequest.getIsActive());
        }
        try {
            domainServiceDao.save(domainModel);
        } catch (Exception e) {
            log.error("Error occurred while updating domain with exception : {}", e.toString());
            throw new InternalError(ErrorCodeAndMessage.ENTITY_SAVING_ERROR);
        }
         Long end = System.currentTimeMillis();
        log.info("updateDomain in service ended at {} and total time taken is {}", end, (end - start));
    }

    @Override
    public void updateSender(@NonNull SenderRequest senderRequest) {
        Long start = System.currentTimeMillis();
        log.info("updateSender in service started at {}", start);
        SenderModel senderModel = senderDao.getSenderByShortcode(senderRequest.getShortcode());
        if (Objects.isNull(senderModel)) {
            throw new EntityNotFoundException(ErrorCodeAndMessage.ENTITY_NOT_FOUND);
        }
        if (Objects.nonNull(senderRequest.getActive())) {
            senderModel.setActive(senderRequest.getActive());
        }
        if (Objects.nonNull(senderRequest.getDescription())) {
            senderModel.setDescription(senderRequest.getDescription());
        }
        if (Objects.nonNull(senderRequest.getSenderName())) {
            senderModel.setSenderName(senderRequest.getSenderName());
        }
        try {
            cacheService.evictAllCacheByName(CacheName.ACTIVE_SENDERS);
            senderDao.save(senderModel);
        } catch (Exception e) {
            log.error("Error occurred while updating sender with exception : {}", e.toString());
            throw new InternalError(ErrorCodeAndMessage.ENTITY_SAVING_ERROR);
        }
         Long end = System.currentTimeMillis();
        log.info("updateSender in service ended at {}and total time taken is {}", end, (end - start));

    }

    @Override
    public void addSender(@NonNull SenderRequest senderRequest) {
        Long start = System.currentTimeMillis();
        log.info("addSender in service started at {}",start);
        SenderModel senderModel = new SenderModel();
        senderModel.setSenderName(senderRequest.getSenderName());
        senderModel.setShortcode(senderRequest.getShortcode());
        senderModel.setActive(senderRequest.getActive());
        senderModel.setDescription(senderRequest.getDescription());
        try {
            cacheService.evictAllCacheByName(CacheName.ACTIVE_SENDERS);
            senderDao.save(senderModel);
        } catch (Exception e) {
            log.error("Error occurred while adding sender with exception : {}", e.toString());
            throw new InternalError(ErrorCodeAndMessage.ENTITY_SAVING_ERROR);
        }
         Long end = System.currentTimeMillis();
        log.info("addSender in service ended at {} and total time taken is {}", end, (end - start));
    }

    @Override
    public List<SenderResponse> getAllSendersPaginated(int page, int noOfEntries) {
        Long start = System.currentTimeMillis();
        log.info("getSendersPaginated in service at {}", start);
        int offset = page * noOfEntries;
        List<SenderModel> senderModels = senderDao.getSenderPaginated(offset, noOfEntries);
         Long end = System.currentTimeMillis();
        log.info("getSendersPaginated in service ended at {} and total time taken is {}", end, (end - start));
        return senderModels.stream().map(SenderMapper::toResponse).collect(Collectors.toList());
    }

    @Override
    public List<TransactionCategoryResponse> getTransactionCategory() {
        Long start = System.currentTimeMillis();
        log.info("getTransactionCategory in service started at {}", start);
        List<TransactionCategoryResponse> transactionCategoryResponses = new ArrayList<>();
        List<TransactionCategoryModel> transactionCategoryModels = transactionCategoryDao.getAllTransactionCategory();
        transactionCategoryModels.stream().forEach(e -> {
            transactionCategoryResponses.add(TransactionCategoryMapper.toResponse(e));
        });
         Long end = System.currentTimeMillis();
        log.info("getTransactionCategory in service ended at {} and total time taken is {}", end, (end - start));
        return transactionCategoryResponses;
    }

    @Override
    public void addTransactionCategory(@NonNull TransactionCategoryRequest transactionCategoryRequest) {
        Long start = System.currentTimeMillis();
        log.info("addTransactionCategory in service started at {}", start);
        TransactionCategoryModel transactionCategoryModel = new TransactionCategoryModel();
        transactionCategoryModel.setCategory(transactionCategoryRequest.getCategory().toUpperCase());
        transactionCategoryModel.setSubcategory(transactionCategoryRequest.getSubcategory().toUpperCase());
        transactionCategoryModel.setActive(transactionCategoryRequest.getIsActive());
        try {
            transactionCategoryDao.save(transactionCategoryModel);
        } catch (Exception e) {
            log.error("Error occurred while adding transaction category with exception : {}", e.toString());
            throw new InternalError(ErrorCodeAndMessage.ENTITY_SAVING_ERROR);
        }
         Long end = System.currentTimeMillis();
        log.info("addTransactionCategory in service ended at {} and total time taken is {}", end, (end - start));
    }

    @Override
    public void updateTransactionCategory(@NonNull TransactionCategoryRequest transactionCategoryRequest) {
        Long start = System.currentTimeMillis();
        log.info("updateTransactionCategory in service started at {}", start);
        TransactionCategoryModel transactionCategoryModel = transactionCategoryDao.getTransactionCategoryBySubCategory(transactionCategoryRequest.getCategory(), transactionCategoryRequest.getSubcategory());
        if (Objects.isNull(transactionCategoryModel)) {
            throw new EntityNotFoundException(ErrorCodeAndMessage.ENTITY_NOT_FOUND);
        }
        if (Objects.nonNull(transactionCategoryRequest.getIsActive())) {
            transactionCategoryModel.setActive(transactionCategoryRequest.getIsActive());
        }
        try {
            transactionCategoryDao.save(transactionCategoryModel);
        } catch (Exception e) {
            log.error("Error occurred while updating transaction category with exception : {}", e.toString());
            throw new InternalError(ErrorCodeAndMessage.ENTITY_SAVING_ERROR);
        }
         Long end = System.currentTimeMillis();
        log.info("updateTransactionCategory in service ended at {} and total time taken is {}", end, (end - start));
    }

    @Override
    public List<TokenResponse> getTokens(String domain) {
        Long start = System.currentTimeMillis();
        log.info("getTokens in service started at {}", start);
        List<TokenModel> tokens = domain.equals("all") ? tokenServiceDao.getAllTokens() : tokenServiceDao.getAllTokensByDomain(domain);
        if (Objects.isNull(tokens)) {
            return Collections.emptyList();
        }
        List<TokenResponse> tokenResponses = new ArrayList<>();
        tokens.stream().forEach(e -> {
            tokenResponses.add(TokenMapper.toResponse(e));
        });
         Long end = System.currentTimeMillis();
        log.info("getTokens in service ended at {} and total time taken is {}", end, (end - start));
        return tokenResponses;
    }

    @Override
    public void addToken(@NonNull TokenRequest tokenRequest) {
        Long start = System.currentTimeMillis();
        log.info("addToken in service started at {}", start);
        if (Objects.nonNull(tokenRequest.getDataType()) && !DataTypeValidator.validateDataType(tokenRequest.getDataType())) {
            throw new BadRequestException(ErrorCodeAndMessage.INVALID_DATA_TYPE);
        }
        DomainModel domainModel = domainServiceDao.getDomainByDomainName(tokenRequest.getDomainName());
        if (Objects.isNull(domainModel)) {
            throw new BadRequestException(ErrorCodeAndMessage.INVALID_DOMAIN);
        }
        TokenModel tokenModel = new TokenModel();
        tokenModel.setTokenName(tokenRequest.getTokenName().toUpperCase());
        tokenModel.setDomainId(domainModel);
        tokenModel.setDataType(tokenRequest.getDataType());
        tokenModel.setDescription(tokenRequest.getDescription());
        tokenModel.setActive(tokenRequest.getIsActive());
        try {
            tokenServiceDao.save(tokenModel);
        } catch (Exception e) {
            log.error("Error occurred while adding token with exception : {}", e.toString());
            throw new InternalError(ErrorCodeAndMessage.ENTITY_SAVING_ERROR);
        }
         Long end = System.currentTimeMillis();
        log.info("addToken in service ended at {} and total time taken is {}", end, (end - start));
    }

    @Override
    public void updateToken(@NonNull TokenRequest tokenRequest) {
        Long start = System.currentTimeMillis();
        log.info("updateToken in service started at {}", start);
        if (Objects.nonNull(tokenRequest.getDataType()) && !DataTypeValidator.validateDataType(tokenRequest.getDataType())) {
            throw new BadRequestException(ErrorCodeAndMessage.INVALID_DATA_TYPE);
        }
        DomainModel domainModel = domainServiceDao.getDomainByDomainName(tokenRequest.getDomainName());
        if (Objects.isNull(domainModel)) {
            throw new BadRequestException(ErrorCodeAndMessage.INVALID_DOMAIN);
        }
        TokenModel tokenModel = tokenServiceDao.getToken(tokenRequest.getTokenName(), domainModel);
        if (Objects.isNull(tokenModel)) {
            throw new EntityNotFoundException(ErrorCodeAndMessage.ENTITY_NOT_FOUND);
        }
        if (Objects.nonNull(tokenRequest.getIsActive())) {
            tokenModel.setActive(tokenRequest.getIsActive());
        }
        if (Objects.nonNull(tokenRequest.getDataType())) {
            tokenModel.setDataType(tokenRequest.getDataType());
        }
        if (Objects.nonNull(tokenRequest.getDescription())) {
            tokenModel.setDescription(tokenRequest.getDescription());
        }
        try {
            tokenServiceDao.save(tokenModel);
        } catch (Exception e) {
            log.error("Error occurred while updating token with exception : {}", e.toString());
            throw new InternalError(ErrorCodeAndMessage.ENTITY_SAVING_ERROR);
        }
         Long end = System.currentTimeMillis();
        log.info("updateToken in service ended at {} and total time taken is {}", end, (end - start));
    }

    @Override
    public void deleteToken(@NonNull String domainName,@NonNull String tokenName) {
        DomainModel domainModel = domainServiceDao.getDomainByDomainName(domainName);
        if (Objects.isNull(domainModel)) {
            throw new BadRequestException(ErrorCodeAndMessage.INVALID_DOMAIN);
        }
        try{
            TokenModel tokenModel = tokenServiceDao.getToken(tokenName, domainModel);
            tokenServiceDao.deleteToken(TokenMapper.fromModel(tokenModel));
        } catch (Exception e){
            log.info("Token deletion failed with exception : {}", e.getMessage());
            throw new InternalError(ErrorCodeAndMessage.TOKEN_DELETION_FAILED);
        }

    }

    @Override
    public TrainMessageEvaluationResponse evaluateTrainedMessage(TrainMessageEvaluationRequest trainMessageEvaluationRequest) {
        Long start = System.currentTimeMillis();
        trainMessageEvaluationRequest.setOriginalMessage(trainMessageEvaluationRequest.getOriginalMessage().replaceAll(SanitizerVariety.SPACE_REMOVER.getRegex(),SanitizerVariety.SPACE_REMOVER.getReplaceBy()));
        if(trainMessageEvaluationRequest.getOriginalMessage().contains("\n")) {
            String originalMessage = trainMessageEvaluationRequest.getOriginalMessage().replace("\n"," ");
            trainMessageEvaluationRequest.setOriginalMessage(originalMessage);
        }
        log.info("evaluateTrainedMessage in service started at {}", start);
        List<TokenModel> tokenModels = tokenServiceDao.getAllActiveTokensByDomain(trainMessageEvaluationRequest.getDomainName());
        if(!TrainedMessageValidator.validateStartEndTag(trainMessageEvaluationRequest.getTrainMessage())){
            throw new BadRequestException(ErrorCodeAndMessage.START_END_TAG_MISMATCH);
        }
        if(!TrainedMessageValidator.validateTokens(trainMessageEvaluationRequest.getTrainMessage(), tokenModels, minVariableNeedToProceed)){
            throw new BadRequestException(ErrorCodeAndMessage.TOKENS_MISMATCH);
        }
        if(!TrainedMessageValidator.validateTrainedAndOriginalWords(trainMessageEvaluationRequest.getTrainMessage(), trainMessageEvaluationRequest.getOriginalMessage())){
            throw new BadRequestException(ErrorCodeAndMessage.WORD_COUNT_MISMATCH);
        }
        TrainMessageEvaluationResponse response = new TrainMessageEvaluationResponse();
        try {
            log.info("Evaluate msg for trained msg {}", trainMessageEvaluationRequest.getTrainMessage());
            TokenNameFinderModel model = (TokenNameFinderModel) modelTrainer.getModel(getTrainingParameters(), new NameSampleDataStream((ObjectStreamUtils.createObjectStream(trainMessageEvaluationRequest.getTrainMessage()))));
            response.setTokens(JsonUtil.writeValueAsString(modelRunnerService.getTokensByModel(new NameFinderME(model), trainMessageEvaluationRequest.getOriginalMessage())));
        } catch (IOException e) {
            log.error("Error creating token name ", e);
            return null;
        }
         Long end = System.currentTimeMillis();
        log.info("evaluateTrainedMessage in service ended at {} and total time taken is {}", end, (end - start));
        return response;
    }


    @Override
    public Integer untrainedMessageCount() {
        Long start = System.currentTimeMillis();
        log.info("untrainedMessageCount in service started at {}", start);
        Integer untrainedMessageCount = templateServiceDao.untrainedMessageCount();
         Long end = System.currentTimeMillis();
        log.info("untrainedMessageCount in service ended at {} and total time taken is {}", end, (end - start));
        return untrainedMessageCount;
    }

    @Override
    public Integer trainedMessageCount() {
        Long start = System.currentTimeMillis();
        log.info("trainedMessageCount in service started at {}", start);
        Integer trainedMessageCount = templateServiceDao.trainedMessageCount();
         Long end = System.currentTimeMillis();
        log.info("trainedMessageCount in service ended at {} and total time taken is {}", end, (end - start));
        return trainedMessageCount;
    }

    @Override
    public Integer stringSimilarityMessageCount() {
        Long start = System.currentTimeMillis();
        log.info("stringSimilarityMessageCount in service started at {}", start);
        Integer stringSimilarityMessageCount = templateServiceDao.stringSimilarityMessageCount();
         Long end = System.currentTimeMillis();
        log.info("stringSimilarityMessageCount in service ended at {} and total time taken is {}", end, (end - start));
        return stringSimilarityMessageCount;
    }

    @Override
    @Transactional
    public void deleteTemplateByMessage(String templateMessage) {
        Long start = System.currentTimeMillis();
        log.info("deleteTemplateByMessage in service started at {}", start);
        Optional<TemplateModel> templateModel = templateServiceDao.getTemplateByMessage(templateMessage);
        if (!templateModel.isPresent()) {
            throw new EntityNotFoundException(ErrorCodeAndMessage.ENTITY_NOT_FOUND);
        }
        if (templateModel.isPresent()) {
            try {
                senderTemplateMapDao.deleteByTemplate(TemplateMapper.fromModel(templateModel.get()));
            } catch (Exception e) {
                log.error("Deletion by message from senderTemplateMap table failed with exception : {}", e.getMessage());
                throw new InternalError(ErrorCodeAndMessage.DELETION_FAILED);
            }
            try {
                templateServiceDao.deleteByTemplateId(templateModel.get().getId());
            } catch (Exception e) {
                log.error("Deletion by message from template table failed with exception : {}", e.getMessage());
                throw new InternalError(ErrorCodeAndMessage.DELETION_FAILED);
            }
        }
         Long end = System.currentTimeMillis();
        log.info("deleteTemplateByMessage in service ended at {} and total time taken is {}", end, (end - start));
    }

    @Override
    @Transactional
    public void deleteTemplateBySenderName(String senderName) {
        Long start = System.currentTimeMillis();
        log.info("deleteTemplateBySenderName in service started at {}", start);
        List<SenderModel> senderModels = senderDao.getAllSenderBySenderName(senderName);
        if (senderModels == null || senderModels.isEmpty()) {
            throw new EntityNotFoundException(ErrorCodeAndMessage.ENTITY_NOT_FOUND);
        }
        senderModels.stream().forEach(e -> {
            Optional<List<TemplateModel>> templateModels = senderTemplateMapDao.getTemplateBySender(e.getId());
            if (templateModels.isPresent()) {
                templateModels.get().stream().forEach(s -> {
                    try {
                        if (s.getTrainedMessage() == null) {
                            Optional<TemplateModel> templateModel = templateServiceDao.getTemplateByById(s.getId());
                            if (templateModel.isPresent()) {
                                senderTemplateMapDao.deleteByTemplate(TemplateMapper.fromModel(templateModel.get()));
                                templateServiceDao.deleteByTemplateId(s.getId());
                            }
                        }
                    } catch (Exception ex) {
                        log.error("message deletion failed of message : {} for sender : {} with exception : {}", s.getOriginalMessage(), e.getSenderName(), ex.getMessage());
                    }
                });
            }
        });
         Long end = System.currentTimeMillis();
        log.info("deleteTemplateBySenderName in service ended at {} and total time taken is {}", end, (end - start));
    }

    @Override
    @Transactional
    public void deleteTemplateBySenderNameAndModelUpdateMethod(@NonNull final String senderName,
                                                               @NonNull final ModelUpdateMethodEnum modelUpdateMethodEnum) {
        Long start = System.currentTimeMillis();
        log.info("deleteTemplateBySenderNameAndModelUpdateMethod in service started at {}", start);
        List<SenderModel> senderModels = senderDao.getAllSenderBySenderName(senderName);
        if (senderModels == null || senderModels.isEmpty()) {
            throw new EntityNotFoundException(ErrorCodeAndMessage.ENTITY_NOT_FOUND);
        }
        senderModels.stream().forEach(e -> {
            Optional<List<TemplateModel>> templateModels = senderTemplateMapDao.getTemplateBySender(e.getId());
            if (templateModels.isPresent()) {
                templateModels.get().stream()
                        .filter(s -> (Predicates.notNull().apply(s.getModelId())
                                && Predicates.notNull().apply(s.getTrainedMessage())
                                && modelUpdateMethodEnum.equals(s.getModelUpdatedMethod())))
                        .forEach(s -> {
                    try {
                            Optional<TemplateModel> templateModel = templateServiceDao.getTemplateByById(s.getId());
                            if (templateModel.isPresent()) {
                                senderTemplateMapDao.deleteByTemplate(TemplateMapper.fromModel(templateModel.get()));
                                templateServiceDao.deleteByTemplateId(s.getId());
                            }
                    } catch (Exception ex) {
                        log.error("message deletion failed of message : {} for sender : {} with exception : {}", s.getOriginalMessage(), e.getSenderName(), ex.getMessage());
                    }
                });
            }
        });
         Long end = System.currentTimeMillis();
        log.info("deleteTemplateBySenderNameAndModelUpdateMethod in service ended at {} and total time taken is {}", end, (end - start));
    }

    @Override
    public List<ShortcodeCounterResult> getShortcodeInRange(LocalDate fromDate, LocalDate toDate) {
        Long start = System.currentTimeMillis();
        log.info("getShortcodeInRange in service started at {}", start);
        try{
           final List<ShortcodeCounterResult> resultList = counterServiceDao.getAllRecordsInDateRange(fromDate, toDate);
           if(CollectionUtils.isNullOrEmpty(resultList)) {
               return Collections.emptyList();
           }
           final List<ShortcodeCounterResult> filteredList = sendersFilter.filterOutActiveShortcodes(resultList, shortcodeFetchLimit);
           if(CollectionUtils.isNullOrEmpty(filteredList)){
               return Collections.emptyList();
           }
             Long end = System.currentTimeMillis();
            log.info("getShortcodeInRange in service ended at {} and total time taken is {}", end, (end - start));
            return filteredList;
        }catch (Exception e){
            log.error("Fetching shortcode in for date range {} and {} failed with exception : {}",fromDate, toDate, e.getMessage());
            throw new InternalError(ErrorCodeAndMessage.INTERNAL_SERVER_ERROR);
        }
    }

    private TrainingParameters getTrainingParameters() {
        Long start = System.currentTimeMillis();
        log.info("getTrainingParameters in service started at {}", start);
        TrainingParameters params = TrainingParameters.defaultParams();
        params.put(TrainingParameters.ALGORITHM_PARAM, "MAXENT");
        params.put(TrainingParameters.TRAINER_TYPE_PARAM, EventTrainer.EVENT_VALUE);
        params.put(TrainingParameters.ITERATIONS_PARAM, 100);
        params.put(TrainingParameters.CUTOFF_PARAM, 1);
         Long end = System.currentTimeMillis();
        log.info("getTrainingParameters in service ended at {} and total time taken is {}", end, (end - start));
        return params;
    }

    @Override
    public List<TemplateResponse> getAllTrainedMsg() {
        Long start = System.currentTimeMillis();
        log.info("getAllTrainedMsg in service started at {}", start);
        final List<TemplateModel> msg = templateServiceDao.getAllTrainedMsg();
        if(CollectionUtils.isNullOrEmpty(msg)){
            return Collections.EMPTY_LIST;
        }
         Long end = System.currentTimeMillis();
        log.info("getAllTrainedMsg in service ended at {} and total time taken is {}", end, (end - start));
        return msg.stream().map(TemplateMapper::toReponse).collect(Collectors.toList());
    }

    @Override
    public List<TemplateResponse> getAllUnTrainedMsg() {
        Long start = System.currentTimeMillis();
        log.info("getAllUnTrainedMsg in service started at {}", start);
        final List<TemplateModel> msg = templateServiceDao.getAllUnTrainedMsg();
        if(CollectionUtils.isNullOrEmpty(msg)){
            return Collections.EMPTY_LIST;
        }
         Long end = System.currentTimeMillis();
        log.info("getAllUnTrainedMsg in service ended at {} and total time taken is {}", end, (end - start));
        return msg.stream().map(TemplateMapper::toReponse).collect(Collectors.toList());
    }

    @Override
    public List<TemplateResponse> getAllStringSimilarMsg() {
        Long start = System.currentTimeMillis();
        log.info("getAllStringSimilarMsg in service started at {}", start);
        final List<TemplateModel> msg = templateServiceDao.getAllStringSimilarMsg();
        if(CollectionUtils.isNullOrEmpty(msg)){
            return Collections.EMPTY_LIST;
        }
         Long end = System.currentTimeMillis();
        log.info("getAllStringSimilarMsg in service ended at {} and total time taken is {}", end, (end - start));
        return msg.stream().map(TemplateMapper::toReponse).collect(Collectors.toList());
    }



    @Override
    @Transactional
    public List<TemplateModel> getAllTemplatesByShortcode(String shortcode, int noOfEntries, Boolean trained) {
        Long start = System.currentTimeMillis();
        log.info("getAllTemplatesByShortcode in service started at {}", start);
        List<SenderModel> senderModels = senderDao.getAllSendersByShortcode(shortcode);
        if (senderModels == null || senderModels.isEmpty()) {
            throw new EntityNotFoundException(ErrorCodeAndMessage.ENTITY_NOT_FOUND);
        }
        List<TemplateModel> templateModelList = new ArrayList<>();
        senderModels.stream().forEach(e -> {
            Optional<List<TemplateModel>> templateModels = senderTemplateMapDao.getAllTemplateBySender(e.getId(), noOfEntries, trained);
            if (templateModels.isPresent()) {
                templateModels.get().forEach(templateModel->{
                    templateModelList.add(templateModel);
                });
            }
        });
         Long end = System.currentTimeMillis();
        log.info("getAllTemplatesByShortcode in service ended at {} and total time taken is {}", end, (end - start));
        return templateModelList;
    }

    @Override
    public List<TemplateModel> getAllTemplatesByShortcodeOnCount(String shortcode, int noOfEntries, int count) {
        Long start = System.currentTimeMillis();
        log.info("getAllTemplatesByShortcode in service started at {}", start);
        List<SenderModel> senderModels = senderDao.getAllSendersByShortcode(shortcode);
        if (senderModels == null || senderModels.isEmpty()) {
            throw new EntityNotFoundException(ErrorCodeAndMessage.ENTITY_NOT_FOUND);
        }
        List<TemplateModel> templateModelList = new ArrayList<>();
        senderModels.stream().forEach(e -> {
            Optional<List<TemplateModel>> templateModels = senderTemplateMapDao.getAllTemplateBySenderOnCount(e.getId(), noOfEntries, count);
            if (templateModels.isPresent()) {
                templateModels.get().forEach(templateModel->{
                    templateModelList.add(templateModel);
                });
            }
        });
         Long end = System.currentTimeMillis();
        log.info("getAllTemplatesByShortcode in service ended at {} and total time taken is {}", end, (end - start));
        return templateModelList;
    }

    @Override
    public TemplateModel getTemplateByTemplateMessage(String template) {
        Optional<TemplateModel> templateModel = templateServiceDao.getTemplateByMessage(template);
        if(templateModel.isPresent()){
            return templateModel.get();
        }
        return null;
    }

    @Override
    public void deleteSenderTemplateMap(List<TemplateModel> templateModelList){
        try {
            templateModelList.forEach(e->{
                log.debug(" UntrainedTemplateDumpCron deleting STM id {}",e.getId());
                senderTemplateMapDao.deleteByTemplate(TemplateMapper.fromModel(e));
            });
        } catch (Exception e){
            log.info("Deletion from senderTemplateMap failed with exception {}",e.getMessage());
            throw new InternalError(ErrorCodeAndMessage.INTERNAL_SERVER_ERROR);
        }
    }

    @Override
    public void deleteTemplates(List<TemplateModel> templateModelList) {
        try {
            templateServiceDao.deleteAllTemplates(templateModelList);
        } catch (Exception e) {
            log.info("Deletion from senderTemplateMap failed with exception {}", e.getMessage());
            throw new InternalError(ErrorCodeAndMessage.INTERNAL_SERVER_ERROR);
        }
    }
}
