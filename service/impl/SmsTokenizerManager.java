package com.freecharge.smsprofilerservice.service.impl;


import com.freecharge.smsprofilerservice.constant.Constants;
import com.freecharge.smsprofilerservice.constant.ErrorCodeAndMessage;
import com.freecharge.smsprofilerservice.constant.ModelUpdateMethodEnum;
import com.freecharge.smsprofilerservice.dao.dynamodb.mapper.TransactionalDataMapper;
import com.freecharge.smsprofilerservice.dao.dynamodb.model.TransactionalDataModel;
import com.freecharge.smsprofilerservice.dao.dynamodb.service.TransactionalDataServiceDao;
import com.freecharge.smsprofilerservice.dao.mysql.entity.Template;
import com.freecharge.smsprofilerservice.dao.mysql.mapper.TemplateMapper;
import com.freecharge.smsprofilerservice.dao.mysql.model.*;
import com.freecharge.smsprofilerservice.dao.mysql.service.*;
import com.freecharge.smsprofilerservice.engine.template.context.TemplateContext;
import com.freecharge.smsprofilerservice.engine.template.enums.SanitizerVariety;
import com.freecharge.smsprofilerservice.engine.template.service.SenderTemplating;
import com.freecharge.smsprofilerservice.engine.template.service.TemplateCacheService;
import com.freecharge.smsprofilerservice.engine.template.service.TemplateEngine;
import com.freecharge.smsprofilerservice.exception.EntityNotFoundException;
import com.freecharge.smsprofilerservice.model.SmsInfo;
import com.freecharge.smsprofilerservice.service.datasanitize.ValueByDataTypeSanitize;
import com.freecharge.smsprofilerservice.service.impl.nlp.ModelRunnerService;
import com.freecharge.smsprofilerservice.service.impl.train.NameFinderModelTrainer;
import com.freecharge.smsprofilerservice.sheet.RegexResponse;
import com.freecharge.smsprofilerservice.utils.DateUtil;
import com.freecharge.smsprofilerservice.utils.HashGenerator;
import com.freecharge.smsprofilerservice.utils.JsonUtil;
import com.github.jfasttext.JFastText;
import com.google.common.collect.Lists;
import lombok.AccessLevel;
import lombok.NonNull;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import opennlp.tools.ml.EventTrainer;
import opennlp.tools.namefind.NameFinderME;
import opennlp.tools.namefind.NameSampleDataStream;
import opennlp.tools.namefind.TokenNameFinderModel;
import opennlp.tools.parser.Cons;
import opennlp.tools.util.ObjectStreamUtils;
import opennlp.tools.util.TrainingParameters;
import org.apache.commons.lang3.CharUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Null;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


/**
 * Manages Template Journey
 */
@Service
@Slf4j
public class SmsTokenizerManager {

    @Setter(value = AccessLevel.PACKAGE)
    @Value("${dynamo.db.transactional.data.ttl.days}")
    private int transactionDataTableTtlDays;


    @Setter(value = AccessLevel.PACKAGE)
    @Value("${min.token.template.need}")
    private int minVariableNeedToProceed;

    @Setter(value = AccessLevel.PACKAGE)
    @Value("${promotion.probability.percentage}")
    private double promotionProbabilityPercentage;

    @Setter(value = AccessLevel.PACKAGE)
    @Value("${min.promotional.percentage}")
    private double minPromotionalPercentage;

    @Autowired
    private TemplateEngine templateEngine;
    @Autowired
    private TemplateServiceDao templateServiceDao;
    @Autowired
    private TemplateCacheService templateCacheService;
    @Autowired
    private TokenServiceDao tokenServiceDao;
    @Autowired
    private TransactionalDataServiceDao transactionalDataServiceDao;
    @Autowired
    private ModelRunnerService modelRunnerService;

    @Autowired
    UtilityRegexManager utilityRegexManager;

    @Autowired
    private CreditCardRegexManager creditCardRegexManager;

    @Autowired
    private SenderServiceDao senderServiceDao;
    @Autowired
    private SenderTemplateMapDao senderTemplateMapDao;
    @Autowired
    private TemplateContext<AtomicInteger> templateContext;

    @Autowired
    private RegexTokenizerManager regexTokenizerManager;

    @Autowired
    private DomainServiceDao domainServiceDao;

    @Autowired
    private TransactionCategoryServiceDao transactionCategoryServiceDao;

    @Autowired
    private SenderTemplating senderTemplating;

    private final BeanFactory beanFactory;
    private static final String SERVICE_NAME_SUFFIX = "DataTypeSanitize";

    @Value("${message.filter.alphanumeric.ratio}")
    @Setter
    private double alphaNumericRatio;


    @Setter
    @Value("${j.fast.text.model.file.path}")
    private static String jFastTextPath;

//    @Setter
//    @Value("${j.fast.text.model.file.path}")
//    private static String jFastTextPath;

    @Value("${message.backlog.count.limit}")
    @Setter
    private double countLimit;

    private JFastText jFastText;

    @Autowired
    private UntrainedBacklogTemplateServiceDao untrainedBacklogTemplateServiceDao;


    private NameFinderModelTrainer modelTrainer;


    public SmsTokenizerManager(TemplateEngine templateEngine, @NonNull final BeanFactory beanFactory, NameFinderModelTrainer modelTrainer) {
        this.templateEngine = templateEngine;
        this.beanFactory = beanFactory;
        this.modelTrainer = modelTrainer;
    }


    /**
     * Step 1: Templatize smsBody
     * Step 2: Generate hash code for template
     * Step 3: get template from db or cache where it is found
     * Step 4: there can be two case if found or not.
     *
     * @param smsInfo
     */


    @Transactional
    public void process(@NonNull SmsInfo smsInfo) {
        log.info("SmsTokenizerManager process calling for imsId {}",smsInfo.getImsId());
        try {

            if (StringUtils.isBlank(smsInfo.getMsg()) || checkIfInValidMessage(smsInfo.getMsg())) {
                log.info("Invalid Message Structure  Ims Id : {}, Time {}, and message is {}", smsInfo.getImsId(),
                        System.currentTimeMillis(), smsInfo.getMsg());
                return;
            }

            generateTemplate(smsInfo);
            if (StringUtils.isBlank(smsInfo.getHashcode())) {
                return;
            }
            Boolean isActive = true;
            handleTemplate(smsInfo, isActive);

        } catch (Exception e) {
            log.error("SmsTokenizerManager Process Exception {} while processing message {}",
                    JsonUtil.writeValueAsString(e), smsInfo);
            throw e;
        }
    }

    public void generateTemplate(@NonNull SmsInfo smsInfo)
    {
        String msg = smsInfo.getMsg();
        if ((msg.contains("\n")) || (msg.contains("\r")) || (msg.contains("\n\r"))) {
            msg = msg.replace("\n", " ").replace("\r", " ").replace("\n\r", " ");
        }
        msg= msg.replaceAll(SanitizerVariety.SPACE_REMOVER.getRegex(), SanitizerVariety.SPACE_REMOVER.getReplaceBy());
        smsInfo.setMsg(msg);
        SenderModel senderModel = senderServiceDao.getSenderByShortcodeByCache(smsInfo.getSender());
        final String template = templateEngine.templating(senderTemplating.senderWiseTemplating(senderModel.getSenderName(), msg));
        final String hashCode = getTemplateHashCode(msg, template);
        smsInfo.setTemplate(template);
        smsInfo.setHashcode(hashCode);
    }

    public void handleTemplate(@NonNull final SmsInfo smsInfo, final Boolean isActive) {
        log.info("handle template for imsId {} ",smsInfo.getImsId() );
        Optional<TemplateModel> templateModelOptional = templateServiceDao.getTemplateByHashCode(smsInfo.getHashcode());
        //existing template
        if (templateModelOptional.isPresent()) {
            handleIfFound(templateModelOptional.get(), smsInfo);
        }
        //new template
        else {

            String domain = regexTokenizerManager.getDomain(smsInfo.getMsg(), smsInfo.getSender());
            smsInfo.setMsg( epfoPreProcess(smsInfo.getMsg(), domain));
            RegexResponse regexResponse = handleRegex(smsInfo.getMsg(), smsInfo);
            templateModelOptional = createTemplate( smsInfo, isActive,domain);
            if (!domain.equalsIgnoreCase(Constants.NOT_CATEGORIZED)) {
//            if required tokens not extracted through regex then only save template
                if (!requiredTokensExtracted(regexResponse)) {
                    //save template
                    saveTemplateInDb(templateModelOptional.get(), smsInfo.getSender());
                }

            }
            //save in dynamo
            saveInDynamo(templateModelOptional.get(), smsInfo, regexResponse.getTokens(), "_R");
            //other domain for utility
            if (domain.equalsIgnoreCase(Constants.UTILITY)) {
                if (!Objects.isNull(regexResponse.getTokens().get(Constants.BILL_PAID)) && !Objects.isNull(regexResponse.getTokens().get(Constants.PAYMENT_DATE))) {
                    handleIfFoundForUtility(templateModelOptional.get(), smsInfo);
                }
            }
        }
    }

    public static String epfoPreProcess(String originalMessage, String domain) {
        if (domain.equalsIgnoreCase(Constants.EPFO)) {
            Pattern pattern = Pattern.compile("(.*?)Dear(.*?)[\\.,] your+");
            Matcher m = pattern.matcher(originalMessage);
            if (m.find()) {
                originalMessage = originalMessage.replaceFirst(" your", "your");
            }
        }
        return originalMessage;
    }


    public boolean checkIfInValidMessage(final String message) {
        long alphanumericCount = 0;
        long questionCharCount = 0;
        for (Character ch : Lists.charactersOf(message)) {
            if (CharUtils.isAsciiAlphanumeric(ch))
            alphanumericCount++;
            if ('?' == ch)
                questionCharCount++;

        }
        return questionCharCount > 10 || alphanumericCount < (message.length() * alphaNumericRatio);
    }



    public Map<String, String> getTokensFromMessage(@NonNull String message) {
        Map<String, String> result = new HashMap<>();
        final String template = templateEngine.templating(message);
        if ((message.contains("\n")) || (message.contains("\r")) || (message.contains("\n\r"))) {
            message = message.replace("\n", " ").replace("\r", " ").replace("\n\r", " ");
        }
        final String hashCode = getTemplateHashCode(message, template);
        if (StringUtils.isBlank(hashCode)) {
            result.put("ERROR", "Template Does not Exist");
        }
        final Optional<TemplateModel> templateModelOptional = templateServiceDao.getTemplateByHashCode(hashCode);
        if (!templateModelOptional.isPresent() || Objects.isNull(templateModelOptional.get())) {
            result.put("ERROR", "Template Does not Exist");
        } else {
            result = modelRunnerService.getTokens(message, templateModelOptional.get()
                    .getModelId(), templateModelOptional.get().getHashcode());
            sanitizeTokenValue(result, templateModelOptional.get().getDomainId().getDomainName());
        }
        log.debug("GetTokensFromMessage response {} : ", result);
        return result;
    }

    String getTemplateHashCode(@NonNull final String message, @NonNull final String template) {
        int variableCount = templateContext.getIntValue();
        templateContext.removeLocalThreadContext();
        if (variableCount < minVariableNeedToProceed) {
            log.debug("token found : {} for sms : {}, but required minimum is : {}. Hence returning.", variableCount,
                    message, minVariableNeedToProceed);
            return StringUtils.EMPTY;
        }
        return HashGenerator.getSHA256HashCode(template);
    }


    private Optional<TemplateModel> createTemplate( SmsInfo smsInfo, Boolean isActive,String domain) {
        String originalMessage = smsInfo.getMsg();
        TemplateModel templateModel = TemplateMapper.getTemplateModelFrom(smsInfo.getHashcode(), smsInfo.getTemplate(), originalMessage, isActive);
        DomainModel model_domain = domainServiceDao.getDomainByDomainName(domain);
        templateModel.setDomainId(model_domain);

        List<String> categoryNSub = regexTokenizerManager.getCategory(originalMessage, domain);
        if (StringUtils.isNoneBlank(categoryNSub.get(0)) && StringUtils.isNoneBlank(categoryNSub.get(1))) {
            templateModel.setTransactionCategoryId(transactionCategoryServiceDao
                    .getTransactionCategoryBySubCategory(categoryNSub.get(0), categoryNSub.get(1)));
        }
        return Optional.of(templateModel);
    }

    public void saveTemplateInDb(TemplateModel templateModel, String sender) {

        final Template template = templateServiceDao.save(templateModel);
        if (template != null) {
            templateModel.setId(template.getId());
            final SenderModel senderModel = senderServiceDao.getSenderByShortcode(sender);
            if (senderModel == null) {
                throw new EntityNotFoundException(ErrorCodeAndMessage.ENTITY_NOT_FOUND);
            }
            final SenderTemplateMapModel senderTemplateMapModel = SenderTemplateMapModel.builder()
                    .senderId(senderModel)
                    .templateId(templateModel)
                    .build();
            senderTemplateMapDao.save(senderTemplateMapModel);
            //templateCacheService.incrementHashCodeInCache(templateModel.getHashcode());
        }
    }
        /**
         * If Template found in redis :
         * Step 1: if model id is not found then just increment hashcode occurrence by one
         * Step 2: if model id is present then pass sms to trained nlp model to get tokens.
         * Step 3 : save step 2 result in dynamodb.
         *
         * @param templateModel
         * @param smsInfo
         */
        private void handleIfFound ( @NonNull final TemplateModel templateModel, @NonNull final SmsInfo smsInfo){
            log.info("Old template : {} found with hashcode : {} for imsid {} ", templateModel.getTemplateMessage(), templateModel.getHashcode(), smsInfo.getImsId());
            templateCacheService.incrementHashCodeInCache(templateModel.getHashcode());

            if (!templateModel.getActive()) {
                log.info("Inactive Template Match found {} for imsid {}", templateModel, smsInfo.getImsId());
                return;
            }
            if (templateModel.getDomainId() == null) {
                DomainModel model_domain = domainServiceDao.getDomainByDomainName(regexTokenizerManager.getDomain(smsInfo.getMsg(), smsInfo.getSender()));
                log.info("Domain Model  handle if found : {} for imsId {} ", model_domain, smsInfo.getImsId());
                templateModel.setDomainId(model_domain);
            }

            String originalMessage = smsInfo.getMsg();
            Map<String, String> tokens = new HashMap<>();
            String domain = null;
            String suffix = "";
            if (StringUtils.isNotEmpty(templateModel.getTrainedMessage())) {
                domain = templateModel.getDomainId().getDomainName();
                originalMessage = epfoPreProcess(originalMessage, domain);
                try {
                    TokenNameFinderModel model1 = (TokenNameFinderModel) modelTrainer.getModel(getTrainingParameters(), new NameSampleDataStream((ObjectStreamUtils.createObjectStream(templateModel.getTrainedMessage()))));
                    tokens = modelRunnerService.getTokensByModel(new NameFinderME(model1), originalMessage);
                } catch (IOException e) {
                    log.info("Error with get token for imsid {}", e, smsInfo.getImsId());
                }
                log.info("Before sanitization tokens for hashcode {} are {} for imsId {}", templateModel.getHashcode(), tokens, smsInfo.getImsId());
                sanitizeTokenValue(tokens, domain);
                log.info("trained template approach for  message for imsid {} , tokens are {} ", smsInfo.getImsId(), tokens);
                log.info("After sanitization tokens for hashcode {} are {} for imsId", templateModel.getHashcode(), tokens, smsInfo.getImsId());
                suffix = "_T";
            }
            //regex approach
            else {
                RegexResponse regexResponse = handleRegex(originalMessage, smsInfo);
                tokens = regexResponse.getTokens();
                domain = regexResponse.getDomain();
                templateModel.setDomainId(domainServiceDao.getDomainByDomainName(domain));
                templateModel.setTransactionCategoryId(transactionCategoryServiceDao.getTransactionCategoryBySubCategory(regexResponse.getTransactionCategory(), regexResponse.getTransactionSubcategory()));
                log.info("Regex approach for  message for imsid {} , tokens are {} ", smsInfo.getImsId(), regexResponse.getTokens());
                suffix = "_R";
            }
            postProcessing(smsInfo, tokens,domain);
            //save in dynamo
            saveInDynamo(templateModel, smsInfo, tokens, suffix);
            if (templateModel.getDomainId().getDomainName().equalsIgnoreCase(Constants.UTILITY)) {
                if (!Objects.isNull(tokens.get(Constants.BILL_PAID)) && !Objects.isNull(tokens.get(Constants.PAYMENT_DATE))) {
                    handleIfFoundForUtility(templateModel, smsInfo);
                }

            }

        }

        public  void postProcessing (SmsInfo smsInfo, Map < String, String > tokens,String domain){
            if(tokens != null) {
                String bankName;
                if (!tokens.containsKey(Constants.DATE) || StringUtils.isEmpty(tokens.get(Constants.DATE))) {
                    tokens.put("DATE", new SimpleDateFormat("yyyy-MM-dd").format(smsInfo.getMsgTime()));
                }

                SenderModel senderModel = senderServiceDao.getSenderByShortcodeByCache(smsInfo.getSender());
                bankName = senderServiceDao.getSenderNameBySenderId(senderModel.getId());

                if (!tokens.containsKey(Constants.BANK_NAME) || StringUtils.isEmpty(tokens.get(Constants.BANK_NAME))) {
                    tokens.put(Constants.BANK_NAME, bankName);
                    log.info("BANKNAME TOKEN FETCHED :{}", bankName);
                }

                if(domain.equalsIgnoreCase(Constants.EPFO))
                {
                    if (!tokens.containsKey(Constants.PF_MONTH) || StringUtils.isEmpty(tokens.get(Constants.PF_MONTH)))
                        tokens.put(Constants.PF_MONTH, new SimpleDateFormat("MMyyyy").format(smsInfo.getMsgTime()));
                }
            }
        }

    private void saveInDynamo(TemplateModel templateModel, SmsInfo smsInfo, Map<String, String> tokens, String
            suffix) {
        String dateHourPartition;
        Date date = new Date(System.currentTimeMillis());
        int min = (date.getMinutes() / 15) + 1;
        String dynamoHashcode = HashGenerator.getSHA256HashCode(smsInfo.getImsId() + smsInfo.getMsgTime() + templateModel.getHashcode() + templateModel.getDomainId().getDomainName());
        long ttl = DateUtil.addDay(new Date(), transactionDataTableTtlDays).getTime();
        if (checkIfReplayData(System.currentTimeMillis(), smsInfo.getIngestionTime())) {
            dateHourPartition = new SimpleDateFormat("yyyy_MM_dd_HH").format(date) + "_" + min + suffix + "_r";
            log.info("TransactionalDataModel to save in Dynamo for hashcode after Replay");
        } else {
            dateHourPartition = new SimpleDateFormat("yyyy_MM_dd_HH").format(date) + "_" + min + suffix;
        }
        TransactionalDataModel model = TransactionalDataMapper.toModelFrom(templateModel, smsInfo, tokens, ttl, date, dynamoHashcode, dateHourPartition);
        log.info("TransactionalDataModel to save in Dynamo {} for hashcode {}", model, templateModel.getHashcode());
        transactionalDataServiceDao.save(model);
    }

    private boolean checkIfReplayData(Long replayTime, Long receivedTimeStr) {
        Date c = new Date(replayTime);
        Date d = new Date(receivedTimeStr);
        long diff = (c.getTime() - d.getTime()) / (60 * 60 * 1000);
        return diff > 6;
    }

        public RegexResponse handleRegex ( @NonNull final String originalMessage, @NonNull final SmsInfo smsInfo){
            RegexResponse regexResponse = setRegexResponse(originalMessage, smsInfo);
            log.info("Regex approach for  message for imsId {} , regexReponse are {} ", smsInfo.getImsId(), regexResponse);
            return regexResponse;
        }

        private boolean requiredTokensExtracted (RegexResponse regexResponse){
            Map<String, String> tokens = regexResponse.getTokens();
            String domain = regexResponse.getDomain();

            boolean requiredTokensExtracted = false;
            switch (domain) {
                case Constants.DEBIT_CARD:
                case Constants.IMPS:
                case Constants.DEPOSIT:
                    requiredTokensExtracted = StringUtils.isNotEmpty(tokens.get("ACCOUNTNUMBER")) || StringUtils.isNotEmpty(tokens.get("CREDIT")) || StringUtils.isNotEmpty(tokens.get("DEBIT") ) || StringUtils.isNotEmpty(tokens.get("BALANCE"));
                    break;
                case Constants.UPI:
                    requiredTokensExtracted = StringUtils.isNotEmpty(tokens.get("CREDIT")) || StringUtils.isNotEmpty(tokens.get("DEBIT")) || StringUtils.isNotEmpty(tokens.get("BALANCE"));
                    break;
                case Constants.WALLET:
                    requiredTokensExtracted = StringUtils.isNotEmpty(tokens.get("CREDIT")) || StringUtils.isNotEmpty(tokens.get("DEBIT"));
                    break;
                case Constants.EPFO:
                    requiredTokensExtracted = StringUtils.isNotEmpty(tokens.get("MONTHLYPFCONTRIBUTION"));
                    break;
                case Constants.CREDIT_CARD:
                    requiredTokensExtracted = StringUtils.isNotEmpty(tokens.get("DATE")) || StringUtils.isNotEmpty(tokens.get("CARDNUMBER")) || StringUtils.isNotEmpty(tokens.get("DEBIT")) || StringUtils.isNotEmpty(tokens.get("PAYMENTRECEVIVED")) ||   StringUtils.isNotEmpty(tokens.get("AVAILABLECREDITLIMIT")) || StringUtils.isNotEmpty(tokens.get("CURRENTOUTSTANDING") );
                    break;
                case Constants.UTILITY:
                    requiredTokensExtracted = StringUtils.isNotEmpty(tokens.get("BILLAMOUNT")) || StringUtils.isNotEmpty(tokens.get("DUEDATE")) || StringUtils.isNotEmpty(tokens.get("PAYMENTDATE")) || StringUtils.isNotEmpty(tokens.get("BILLPAID"));
                    break;
            }
            log.info("In requiredTokensExtracted: {} ",requiredTokensExtracted);
            return requiredTokensExtracted;

        }

        private void handleIfFoundForUtility (TemplateModel templateModel, SmsInfo smsInfo){
            log.info("imsID in handleIfFoundForUtility :{}",smsInfo.getImsId());
            String originalMessage = smsInfo.getMsg();
            Map<String, String> tokens;
            RegexResponse regexResponse =setRegexResponseForPaidUtility(originalMessage, smsInfo);
            log.info("imsID {}  in handleIfFoundForUtility regexx {} ",regexResponse);
            tokens = regexResponse.getTokens();
            templateModel.setDomainId(domainServiceDao.getDomainByDomainName(regexResponse.getDomain()));
            templateModel.setTransactionCategoryId(transactionCategoryServiceDao.getTransactionCategoryBySubCategory(regexResponse.getTransactionCategory(), regexResponse.getTransactionSubcategory()));
            postProcessing(smsInfo,tokens,regexResponse.getDomain());
            saveInDynamo(templateModel, smsInfo, tokens, "_R");

        }


    /**
     * Need to sanitize token got from NLP model
     * based on datatype assigned to each token in db.
     *
     * @param tokens
     * @param domainName
     */
    public void sanitizeTokenValue(Map<String, String> tokens, String domainName) {
        Map<String, TokenModel> tokenModelMap = tokenServiceDao.getTokenFromDomainName(domainName);
        tokens.entrySet().stream().forEach(token -> {
            TokenModel tokenModel = tokenModelMap.get(token.getKey());
            if (tokenModel == null || tokenModel.getDataType() == null) {
                return;
            } else if (token.getKey().equalsIgnoreCase(Constants.DATE)) {
                String date = modelRunnerService.dateFormatter(token.getValue());
                log.info("DATE: {}", date);
                token.setValue(date);
            } else if (token.getKey().equalsIgnoreCase(Constants.BALANCE)) {
                String balance = regexTokenizerManager.balanceFormatter(token.getValue());
                log.info("BALANCE: {}", balance);
                token.setValue((balance));
            } else if (token.getKey().equalsIgnoreCase(Constants.MONTHLY_PF_CONTRIBUTION)) {
                String pfContribution = regexTokenizerManager.sanitizeBalance(token.getValue());
                pfContribution = pfContribution.replace(",", "");
                log.info("MONTHLYPFCONTRIBUTION: {}", pfContribution);
                token.setValue((pfContribution));
            } else if (token.getKey().equalsIgnoreCase(Constants.BILL_AMOUNT) || token.getKey().equalsIgnoreCase(Constants.BILL_PAID)) {
                String billAmount = regexTokenizerManager.sanitizeBalance(token.getValue());
                billAmount = billAmount.replace(",", "");
                log.info("billAmount: {}", billAmount);
                token.setValue((billAmount));
            } else if (token.getKey().equalsIgnoreCase(Constants.CREDIT) || token.getKey().equalsIgnoreCase(Constants.DEBIT)) {
                String amount = regexTokenizerManager.sanitizeBalance(token.getValue());
                String amountAfterSanity = amount.replaceAll(",", "");
                token.setValue((amountAfterSanity));
            } else if (token.getKey().equalsIgnoreCase(Constants.DUE_DATE)) {
                String dueDate = modelRunnerService.dateFormatter(token.getValue());
                log.info("DUE DATE: {}", dueDate);
                token.setValue(dueDate);
            } else if (token.getKey().equalsIgnoreCase(Constants.PAYMENT_DATE)) {
                String paymentDate = modelRunnerService.dateFormatter(token.getValue());
                log.info("PAYMENT DATE: {}", paymentDate);
                token.setValue(paymentDate);
            } else if (token.getKey().equalsIgnoreCase(Constants.ACCOUNT_NUMBER)) {
                String accountNumber = modelRunnerService.sanitizeNumber(token.getValue());
                token.setValue(accountNumber);
            } else if (token.getKey().equalsIgnoreCase(Constants.PF_MONTH)) {
                ValueByDataTypeSanitize service = beanFactory.getBean(getSanitizeServiceBeanName(tokenModel.getDataType()),
                        ValueByDataTypeSanitize.class);
                String tokenVal = service.sanitizeData(token.getValue(), tokenModel.getDataType());
                String pfMonth = modelRunnerService.pfMonthFormatter(tokenVal);
                token.setValue(pfMonth);
            } else {
                ValueByDataTypeSanitize service = beanFactory.getBean(getSanitizeServiceBeanName(tokenModel.getDataType()),
                        ValueByDataTypeSanitize.class);
                String tokenVal_ = token.getValue().replaceAll(",", "");
                String tokenVal = service.sanitizeData(tokenVal_, tokenModel.getDataType());
                token.setValue(tokenVal);
            }
        });
    }

    private String getSanitizeServiceBeanName(String dataType) {
        if (dataType.equalsIgnoreCase("string")) {
            return "String" + SERVICE_NAME_SUFFIX;
        }
        return "Number" + SERVICE_NAME_SUFFIX;
    }

    public TrainingParameters getTrainingParameters() {
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

    public Map<String, String> extractTokenThroughRegex(String originalMessage, @NotNull SmsInfo smsInfo, String domain) {
        switch (domain) {
            case (Constants.UTILITY):
                Map<String, String> utilityTokens = utilityRegexManager.setUtilityTokens(originalMessage, smsInfo);
                return utilityTokens;
            case (Constants.CREDIT_CARD):
                Map<String, String> creditCardTokens = creditCardRegexManager.setCreditCardTokens(originalMessage, smsInfo);
                return creditCardTokens;
            case (Constants.IMPS):
            case (Constants.UPI):
            case (Constants.DEBIT_CARD):
            case (Constants.DEPOSIT):
            case (Constants.NOT_CATEGORIZED):
            case (Constants.WALLET):
                Map<String, String> commonTokens = commonTokens(originalMessage, smsInfo);
                return commonTokens;
            case (Constants.EPFO):
                Map<String, String> epfoMap = tokensForEpfo(originalMessage, smsInfo);
                return epfoMap;
        }
        return null;
    }

    private Map<String, String> commonTokens(String originalMessage, SmsInfo smsInfo) {
        Map<String, String> response = new HashMap<>();
        String accountNumber = regexTokenizerManager.getAccountNumber(originalMessage);
        List<String> amount = regexTokenizerManager.getAmounts(originalMessage);
        String balance = regexTokenizerManager.balanceFormatter(originalMessage);
        response.put("BALANCE", balance);
        String transactionAmount = "";
        List<String> amountList2 = regexTokenizerManager.sanitizeAmount(amount);
        amountList2.remove(balance);
        if (amountList2.size() != 0) {
            transactionAmount = amountList2.get(0);
            transactionAmount = transactionAmount.replaceAll(",", "");
        }
        String transcationCategory = regexTokenizerManager.getTransactionCategory(originalMessage);
        if (transcationCategory == "CREDIT") {
            response.put("CREDIT", transactionAmount);
        } else {
            response.put("DEBIT", transactionAmount);
        }
        response.put("ACCOUNTNUMBER", regexTokenizerManager.sanitizeAccountNumber(accountNumber));
        return response;
    }

    private Map<String, String> tokensForEpfo(String originalMessage, SmsInfo smsInfo) {
        Map<String, String> response = new HashMap<>();
        String contributionToken = regexTokenizerManager.getPFContribution(originalMessage);
        contributionToken = contributionToken.replaceAll("[,，]+", "");
        String contribution = regexTokenizerManager.sanitizeBalance(contributionToken);
        response.put("MONTHLYPFCONTRIBUTION", contribution);
        String pfMonth = regexTokenizerManager.getPFMonth(originalMessage);
        ValueByDataTypeSanitize service = beanFactory.getBean(getSanitizeServiceBeanName("string"),
                ValueByDataTypeSanitize.class);
        String pfMonthSanitised = service.sanitizeData(pfMonth, "STRING");
        response.put("PFMONTH", pfMonthSanitised);
        if (StringUtils.isEmpty(pfMonthSanitised)) {
            response.put("PFMONTH", new SimpleDateFormat("MMyyyy").format(smsInfo.getMsgTime()));
        }

        return response;
    }


    public RegexResponse setRegexResponse(String originalMessage, SmsInfo smsInfo) {
        String sender = smsInfo.getSender();
        String domain = regexTokenizerManager.getDomain(originalMessage, sender);
        RegexResponse regexResponse = new RegexResponse();
        regexResponse.setTokens(extractTokenThroughRegex(originalMessage, smsInfo, domain));
        log.info("After extractTokenThroughRegex for imsId {}  ", smsInfo.getImsId());
        regexResponse.setDomain(domain);
        regexResponse.setTransactionCategory(regexTokenizerManager.getCategory(originalMessage, domain).get(0));
        regexResponse.setTransactionSubcategory(regexTokenizerManager.getCategory(originalMessage, domain).get(1));
        log.info("After transaction category set for imsId {}  regex response {}  ", smsInfo.getImsId(), regexResponse);
        return regexResponse;

    }

    public RegexResponse setRegexResponseForPaidUtility(String originalMessage, SmsInfo smsInfo) {
        String sender = smsInfo.getSender();
        RegexResponse regexResponse = new RegexResponse();
        String domain = regexTokenizerManager.getDomainForPaidUtiliy(originalMessage, sender);
        regexResponse.setDomain(domain);
        regexResponse.setTokens(extractTokenThroughRegex(originalMessage, smsInfo,domain));
        regexResponse.setTransactionCategory(regexTokenizerManager.getCategory(originalMessage, domain).get(0));
        regexResponse.setTransactionSubcategory(regexTokenizerManager.getCategory(originalMessage, domain).get(1));
        return regexResponse;

    }


}
