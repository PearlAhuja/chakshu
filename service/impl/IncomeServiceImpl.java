package com.freecharge.smsprofilerservice.service.impl;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fc.attribute.response.FCTransactionalData;
import com.freecharge.fctoken.context.AuthorizationContext;
import com.freecharge.fctoken.model.UserIdentifier;
import com.freecharge.smsprofilerservice.aws.accessor.FcAttributeServiceImpl;
import com.freecharge.smsprofilerservice.aws.accessor.ParsingServiceAccessor;
import com.freecharge.smsprofilerservice.aws.accessor.S3Accessor;
import com.freecharge.smsprofilerservice.aws.constants.StateEnum;
import com.freecharge.smsprofilerservice.client.ImsClient;
import com.freecharge.smsprofilerservice.constant.AppConstants;
import com.freecharge.smsprofilerservice.constant.CacheName;
import com.freecharge.smsprofilerservice.constant.Constants;
import com.freecharge.smsprofilerservice.constant.IncomeCalculationStateEnum;
import com.freecharge.smsprofilerservice.constant.ModelUpdateMethodEnum;
import com.freecharge.smsprofilerservice.dao.cache.CacheService;
import com.freecharge.smsprofilerservice.dao.mysql.entity.Income;
import com.freecharge.smsprofilerservice.dao.mysql.mapper.IncomeMapper;
import com.freecharge.smsprofilerservice.dao.mysql.model.IncomeModel;
import com.freecharge.smsprofilerservice.dao.mysql.model.SenderModel;
import com.freecharge.smsprofilerservice.dao.mysql.model.TemplateModel;
import com.freecharge.smsprofilerservice.dao.mysql.repository.IncomeRepository;
import com.freecharge.smsprofilerservice.dao.mysql.service.SenderServiceDao;
import com.freecharge.smsprofilerservice.dao.mysql.service.TemplateServiceDao;
import com.freecharge.smsprofilerservice.engine.template.enums.SanitizerVariety;
import com.freecharge.smsprofilerservice.engine.template.service.SenderTemplating;
import com.freecharge.smsprofilerservice.engine.template.service.TemplateEngine;
import com.freecharge.smsprofilerservice.filter.SendersFilter;
import com.freecharge.smsprofilerservice.model.AMBResponse;
import com.freecharge.smsprofilerservice.model.IncomeSmsInfoModel;
import com.freecharge.smsprofilerservice.model.SmsDetail;
import com.freecharge.smsprofilerservice.model.SmsInfo;
import com.freecharge.smsprofilerservice.request.AbstractAppRequest;
import com.freecharge.smsprofilerservice.request.DataRequiredRequest;
import com.freecharge.smsprofilerservice.request.IncomeSmsRequest;
import com.freecharge.smsprofilerservice.request.IncomeSmsRequestV2;
import com.freecharge.smsprofilerservice.response.DataRequiredResponse;
import com.freecharge.smsprofilerservice.service.IncomeService;
import com.freecharge.smsprofilerservice.service.impl.nlp.ModelRunnerService;
import com.freecharge.smsprofilerservice.service.impl.train.NameFinderModelTrainer;
import com.freecharge.smsprofilerservice.sheet.RegexResponse;
import com.freecharge.smsprofilerservice.utils.*;
import com.snapdeal.ims.dto.UserDetailsDTO;
import com.snapdeal.ims.exception.ServiceException;
import com.snapdeal.ims.response.GetUserResponse;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import opennlp.tools.namefind.NameFinderME;
import opennlp.tools.namefind.NameSampleDataStream;
import opennlp.tools.namefind.TokenNameFinderModel;
import opennlp.tools.parser.Cons;
import opennlp.tools.util.ObjectStreamUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.DateUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class IncomeServiceImpl implements IncomeService {


    private final SenderServiceDao senderDao;
    private final SendersFilter sendersFilter;
    private final CacheService cacheService;
    private final SmsTokenizerManager smsTokenizerManager;
    private final RegexTokenizerManager regexTokenizerManager;
    private final SenderServiceDao senderServiceDao;
    private final TemplateEngine templateEngine;
    private final SenderTemplating senderTemplating;
    private final TemplateServiceDao templateServiceDao;
    private final NameFinderModelTrainer modelTrainer;
    private final ModelRunnerService modelRunnerService;
    private final S3Accessor s3Accessor;
    private final IncomeRepository incomeRepository;
    private final ParsingServiceAccessor parsingServiceAccessor;
    private final FcAttributeServiceImpl fcAttributeServiceimpl;
    private final ImsClient imsClient;

    private static ObjectMapper mapper = new ObjectMapper();
    private String seed;
    private String SALT = "Salt";

    @Value("${fc.smsml.save.income.enable}")
    private boolean ifSaveIncomeEnable;

    @Value("#{'${fc.smsml.keywords.to.filter}'.split(',')}")
    private List<String> keywordsToFilter;

    @Value("${fc.smsml.income.computed.days}")
    private Long noOfDays;


    static {
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }


    private static BiFunction<BigDecimal, BigDecimal, Boolean> isLessThen = (val1, val2) -> val1.compareTo(val2) < 0 ? true : false;
    private static Predicate<IncomeModel> isIncomeNullOrZero = (incomeModel -> Objects.isNull(incomeModel) || (incomeModel.getIncome() == 0d && incomeModel.getState().equals(IncomeCalculationStateEnum.INITIATED)));
    private static BiFunction<Date, Date, Long> daysBetweenTwoDates = (startDate, endDate) -> ChronoUnit.DAYS.between(startDate.toInstant(), endDate.toInstant());

    @Override
    public IncomeSmsInfoModel uploadDataAndFilterMessages(IncomeSmsRequest request) {
        uploadIntoS3(request, request.getUserId());
        updateStateAndIncomeInDb(0d, StateEnum.IN_PROGRESS.name(), null, null, request.getUserId(), request.getUpdatedBy());
        return getFilteredSmsInfos(request);
    }

    @Override
    public IncomeSmsInfoModel getFilteredSmsInfos(IncomeSmsRequest request) {
        IncomeSmsRequest incomeSmsRequest = filterActiveSendersFromSmsRequest(request);
        log.info("Count after Filtering on Active Sender for userId: {} is {}", request.getUserId(), incomeSmsRequest.getSmsDetails().size());
        return new IncomeSmsInfoModel()
                .setSmsInfoList(filterValidTransactionalMsg(request))
                .setIncomeSmsRequest(incomeSmsRequest);
    }

    @Override
    public IncomeSmsInfoModel uploadDataAndFilterMessagesV2(IncomeSmsRequestV2 request) {
        uploadIntoS3(request, request.getUserId());
        updateStateAndIncomeInDb(0d, StateEnum.IN_PROGRESS.name(), null, null, request.getUserId(), request.getUpdatedBy());
        return getFilteredSmsInfosV2(request);
    }

    @Override
    public IncomeSmsInfoModel getFilteredSmsInfosV2(IncomeSmsRequestV2 request) {
        filterActiveSendersFromSmsRequestV2(request);
        log.info("Count after Filtering on Active Sender for userId: {} is {}", request.getUserId(), request.getSmsInfoList().size());
        return new IncomeSmsInfoModel()
                .setSmsInfoList(filterValidTransactionalMsgV2(request))
                .setIncomeSmsRequestv2(request);
    }

    //    @Async
    @Override
    public void prepareIncomeModelAndSaveInDb(String imsId, Double computedIncome, String model, String modelInfo, String state) {
        Income income = new Income();
        income.setImsId(imsId);
        income.setIncome(computedIncome);
        income.setModel(model);
        income.setModelInfo(modelInfo);
        income.setState(state);
        income.setCreatedAt(new Date());
        income.setUpdatedAt(new Date());
        income.setUpdatedBy(AppConstants.MAIN_THREAD);
        log.info("Saving IncomeModel in DB :: {}", income);
        incomeRepository.save(income);
    }


    @Override
    public void updateStateAndIncomeInDb(double income, String state, String model, String modelInfo, String ims_id, String updatedBy) {
        log.info("Updating IncomeModel in DB for user id :{} state {},income{} , model {},model info {} updatedBy {}", ims_id, state, income, model, modelInfo, updatedBy);
        incomeRepository.updateStateAndIncome(income, state, model, modelInfo, updatedBy, ims_id);
    }


    @Override
    public void computeAndUpdateIncomeFromMsgs(List<SmsInfo> smsInfoList, IncomeSmsRequest incomeSmsRequest) {
        double computedIncome = 0d;
        String incomeState = StateEnum.IN_PROGRESS.name();
        try {
            List<SmsInfo> salaryMessageList = getSalaryMessage(smsInfoList);
            int flag = 1;
            double consolidatedSalary = 0D;
            String latestDate = "";
            String latestMonth = "";

            log.info("Calculating income using salary model for imsid: {}", incomeSmsRequest.getUserId());
            for (SmsInfo salaryMessage : salaryMessageList) {
                Map<String, String> tokens = getTokens(salaryMessage);
                if (flag == 1 && tokens != null && !tokens.isEmpty()) {
                    latestDate = tokens.get(Constants.DATE);
                    latestMonth = latestDate.substring(0, 7);

                }


                while (tokens != null && !tokens.isEmpty() && tokens.get(Constants.DATE).contains(latestMonth)) {

                    if (tokens.containsKey(Constants.CREDIT)) {
                        log.info("salaries of same month: {}", tokens.get(Constants.CREDIT));
                        log.info("dates for salaries: {}", tokens.get("DATE"));
                        double salaryCredit = Double.parseDouble(!tokens.get(Constants.CREDIT).isEmpty() ? tokens.get(Constants.CREDIT) : "0");
                        consolidatedSalary += salaryCredit;
                    }
                    if (consolidatedSalary != 0)
                        flag = 0;

                    break;
                }

                if (flag == 0 && !tokens.get(Constants.DATE).contains(latestMonth)) {
                    break;
                }
            }

            if (consolidatedSalary != 0) {
                computedIncome = consolidatedSalary;
                incomeState = StateEnum.COMPLETED.name();
                log.info("computed income: {}", computedIncome);
                log.info("latest date: {}", latestDate);
                updateStateAndIncomeInDb(computedIncome, incomeState, "SALARY", latestDate, smsInfoList.get(0).getImsId(), incomeSmsRequest.getUpdatedBy());
                if (ifSaveIncomeEnable) {
                    fcAttributeServiceimpl.saveIncomeInFCAttributeDB(incomeSmsRequest.getUserId(), String.valueOf(computedIncome), incomeSmsRequest.getMobileNumber());
                }
                return;
            }

            //epfo
            List<SmsInfo> epfoMessageList = getEPFOMessage(smsInfoList);
            log.info("Calculating income using EPFO model for imsid: {}", incomeSmsRequest.getUserId());
            ArrayList<Double> epfoList = new ArrayList<>();
            Map<String, String> tokens = new HashMap<>();
            Double monthlyPFContribution = 0D;
            Double finalContribution = 0D;
            for (SmsInfo epfoMessage : epfoMessageList) {
                tokens = getTokens(epfoMessage);
                if (tokens != null && !tokens.isEmpty()) {
                    monthlyPFContribution = Double.parseDouble(StringUtils.isNotEmpty(tokens.get(Constants.MONTHLY_PF_CONTRIBUTION)) ? tokens.get(Constants.MONTHLY_PF_CONTRIBUTION) : "0");
                    epfoList.add(monthlyPFContribution);
                    if (epfoList.size() == 3) {
                        break;
                    }
                }
            }

            finalContribution = checkEpfoMaxOrNot(epfoList);
            if (finalContribution != 0) {
                log.info("Final Contribution : {}", finalContribution);
                computedIncome = computeIncomeForEPFO(finalContribution);
                incomeState = StateEnum.COMPLETED.name();
                updateStateAndIncomeInDb(computedIncome, incomeState, "EPFO", tokens.get(Constants.PF_MONTH), smsInfoList.get(0).getImsId(), incomeSmsRequest.getUpdatedBy());
                if (ifSaveIncomeEnable) {
                    fcAttributeServiceimpl.saveIncomeInFCAttributeDB(incomeSmsRequest.getUserId(), String.valueOf(computedIncome), incomeSmsRequest.getMobileNumber());
                }
                return;
            }

            //AMB or AQB
            log.info("Calculating income using AMB/AQB model for imsid: {}", incomeSmsRequest.getUserId());
            Collections.sort(smsInfoList);
            AMBResponse ambResponse = getAmb(prepareDataForAMB(smsInfoList));
            log.info("Response for AMB for userId : {} is {}", incomeSmsRequest.getUserId(), ambResponse);
            if (ambResponse != null) {
                computedIncome = ambResponse.getAmb();
                incomeState = StateEnum.COMPLETED.name();
                if (ambResponse.getDaysConsidered() < 150) {
                    updateStateAndIncomeInDb(computedIncome, incomeState, "AMB", ambResponse.getDaysConsidered().toString(), incomeSmsRequest.getUserId(), incomeSmsRequest.getUpdatedBy());
                } else {
                    computedIncome = ifAfterComputationRequired(computedIncome);
                    updateStateAndIncomeInDb(computedIncome, incomeState, "AQB", ambResponse.getDaysConsidered().toString(), incomeSmsRequest.getUserId(), incomeSmsRequest.getUpdatedBy());
                }
                if (ifSaveIncomeEnable) {
                    fcAttributeServiceimpl.saveIncomeInFCAttributeDB(incomeSmsRequest.getUserId(), String.valueOf(computedIncome), incomeSmsRequest.getMobileNumber());
                }
                return;
            }

            log.info("Unable to compute income for userId : {}", incomeSmsRequest.getUserId());
            updateStateAndIncomeInDb(computedIncome, StateEnum.COMPLETED.name(), null, null, incomeSmsRequest.getUserId(), incomeSmsRequest.getUpdatedBy());
        } catch (Exception e) {
            updateStateAndIncomeInDb(0d, StateEnum.EXCEPTION.name(), null, e.getMessage().substring(0, Math.min(50, e.getMessage().length())), incomeSmsRequest.getUserId(), incomeSmsRequest.getUpdatedBy());
        }
    }

    public Double checkEpfoMaxOrNot(ArrayList<Double> epfoList) {
        Double finalContribution = 0D;
        if (epfoList.size() == 3) {
            finalContribution = Math.max(Math.max(epfoList.get(0), epfoList.get(1)), epfoList.get(2));
        } else if (epfoList.size() == 2) {
            finalContribution = Math.max(epfoList.get(0), epfoList.get(1));
        } else if (epfoList.size() == 1) {
            finalContribution = epfoList.get(0);
        }
        return finalContribution;

    }

    @Override
    public void computeAndUpdateIncomeFromMsgsV2(List<SmsInfo> smsInfoList, IncomeSmsRequestV2 incomeSmsRequestV2) {
        double computedIncome = 0d;
        String incomeState = StateEnum.IN_PROGRESS.name();
        try {
            List<SmsInfo> salaryMessageList = getSalaryMessage(smsInfoList);
            int flag = 1;
            double consolidatedSalary = 0D;
            String latestDate = "";
            String latestMonth = "";

            log.info("Calculating income using salary model for imsid: {}", incomeSmsRequestV2.getUserId());
            for (SmsInfo salaryMessage : salaryMessageList) {

                Map<String, String> tokens = getTokens(salaryMessage);
                if (flag == 1 && tokens != null && !tokens.isEmpty()) {
                    latestDate = tokens.get(Constants.DATE);
                    latestMonth = latestDate.substring(0, 7);

                }

                log.info("latest date :: {} and latest month ::  {}",latestDate,latestMonth);
                while (tokens != null && !tokens.isEmpty() && tokens.get(Constants.DATE).contains(latestMonth)) {

                    if (tokens.containsKey("CREDIT")) ;
                    {
                        log.info("dates for salaries: {}", tokens.get("DATE"));
                        double salaryCredit = Double.parseDouble(!tokens.get(Constants.CREDIT).isEmpty() ? tokens.get(Constants.CREDIT) : "0");
                        log.info("salaryCredit :: {}",salaryCredit);
                        consolidatedSalary += salaryCredit;
                    }
                    log.info("Consolidated Salary :: {}",consolidatedSalary);
                    if (consolidatedSalary != 0)
                        flag = 0;

                    break;
                }

                if (flag == 0 && !tokens.get(Constants.DATE).contains(latestMonth)) {
                    break;
                }
            }

            if (consolidatedSalary != 0) {

                computedIncome = consolidatedSalary;
                incomeState = StateEnum.COMPLETED.name();
                log.info("computed income: {}", computedIncome);
                log.info("latest date: {}", latestDate);

                updateStateAndIncomeInDb(computedIncome, incomeState, "SALARY", latestDate, incomeSmsRequestV2.getUserId(), incomeSmsRequestV2.getUpdatedBy());
                if (ifSaveIncomeEnable) {
                    fcAttributeServiceimpl.saveIncomeInFCAttributeDB(incomeSmsRequestV2.getUserId(), String.valueOf(computedIncome), incomeSmsRequestV2.getMobileNumber());
                }
                return;
            }

            //epfo

            List<SmsInfo> epfoMessageList = getEPFOMessage(smsInfoList);
            log.info("Calculating income using EPFO model for imsid: {}", incomeSmsRequestV2.getUserId());
            ArrayList<Double> epfoList = new ArrayList<>();
            Map<String, String> tokens = new HashMap<>();
            Double monthlyPFContribution = 0D;
            Double finalContribution = 0D;
            for (SmsInfo epfoMessage : epfoMessageList) {

                tokens = getTokens(epfoMessage);
                if (tokens != null && !tokens.isEmpty()) {

                    monthlyPFContribution = Double.parseDouble(StringUtils.isNotEmpty(tokens.get(Constants.MONTHLY_PF_CONTRIBUTION)) ? tokens.get(Constants.MONTHLY_PF_CONTRIBUTION) : "0");
                    epfoList.add(monthlyPFContribution);
                    if (epfoList.size() == 3) {
                        break;
                    }
                }
            }

            finalContribution = checkEpfoMaxOrNot(epfoList);
            if (finalContribution != 0) {
                log.info("Final Contribution : {}", finalContribution);
                computedIncome = computeIncomeForEPFO(finalContribution);
                incomeState = StateEnum.COMPLETED.name();
                updateStateAndIncomeInDb(computedIncome, incomeState, "EPFO", tokens.get(Constants.PF_MONTH), incomeSmsRequestV2.getUserId(), incomeSmsRequestV2.getUpdatedBy());
                if (ifSaveIncomeEnable) {
                    fcAttributeServiceimpl.saveIncomeInFCAttributeDB(incomeSmsRequestV2.getUserId(), String.valueOf(computedIncome), incomeSmsRequestV2.getMobileNumber());
                }
                return;
            }

            //AMB or AQB
            log.info("Calculating income using AMB/AQB model for imsid: {}", incomeSmsRequestV2.getUserId());
            Collections.sort(smsInfoList);
            AMBResponse ambResponse = getAmb(prepareDataForAMB(smsInfoList));
            log.info("Response for AMB for userId : {} is {}", incomeSmsRequestV2.getUserId(), ambResponse);
            if (ambResponse != null) {
                computedIncome = ambResponse.getAmb();
                incomeState = StateEnum.COMPLETED.name();
                if (ambResponse.getDaysConsidered() < 150) {
                    updateStateAndIncomeInDb(computedIncome, incomeState, "AMB", ambResponse.getDaysConsidered().toString(), incomeSmsRequestV2.getUserId(), incomeSmsRequestV2.getUpdatedBy());
                } else {
                    computedIncome = ifAfterComputationRequired(computedIncome);
                    updateStateAndIncomeInDb(computedIncome, incomeState, "AQB", ambResponse.getDaysConsidered().toString(), incomeSmsRequestV2.getUserId(), incomeSmsRequestV2.getUpdatedBy());
                }
                if (ifSaveIncomeEnable) {
                    fcAttributeServiceimpl.saveIncomeInFCAttributeDB(incomeSmsRequestV2.getUserId(), String.valueOf(computedIncome), incomeSmsRequestV2.getMobileNumber());
                }
                return;
            }

            log.info("Unable to compute income for userId : {}", incomeSmsRequestV2.getUserId());
            updateStateAndIncomeInDb(computedIncome, StateEnum.COMPLETED.name(), null, null, incomeSmsRequestV2.getUserId(), incomeSmsRequestV2.getUpdatedBy());
        } catch (Exception e) {
            updateStateAndIncomeInDb(0d, StateEnum.EXCEPTION.name(), null, e.getMessage().substring(0, Math.min(50, e.getMessage().length())), incomeSmsRequestV2.getUserId(), incomeSmsRequestV2.getUpdatedBy());
        }


    }

    @Override
    public DataRequiredResponse getDataRequiredResponse(DataRequiredRequest request) {
        DataRequiredResponse response = new DataRequiredResponse()
                .setBlockedSenderList(getBlackListedSenderList())
                .setKeywordsToFilter(keywordsToFilter)
                .setDuration(180);
        if (isBlackListedUser(request.getUserId())) {
            return response.setDataRequired(false);
        }
        if (StringUtils.isEmpty(request.getProductType())) {
            response.setDataRequired(isNutsUser(request.getUserId()));
        } else {

            IncomeModel incomeModel = incomeRepository.findById(request.getUserId()).map(IncomeMapper::toModel).orElse(null);
            Date endDate = new Date();

            // If data is present in income table
            if (incomeModel != null && daysBetweenTwoDates.apply(incomeModel.getCreatedAt(), endDate) > noOfDays) {
                response.setDataRequired(true);
            } else if (incomeModel != null && daysBetweenTwoDates.apply(incomeModel.getCreatedAt(), endDate) < noOfDays) {
                if (incomeModel.getState().equalsIgnoreCase(StateEnum.COMPLETED.name()) && incomeModel.getIncome() != 0d) {
                    response.setDataRequired(false);
                } else {
                    response.setDataRequired(true);
                }
            } else { // Data is not available in income table so checking income from fc attribute
                log.info("Fetching data from fc attribute for user :: {}", request.getUserId());
                FCTransactionalData fcAttributes = fcAttributeServiceimpl.fetchDataFromFcAttributes(request.getMobileNumber());
                log.info("Data from fc attribute :: {}", fcAttributes);
                if (fcAttributes == null || fcAttributes.getRnd1() == 0) {
                    response.setDataRequired(true);
                }
            }
        }
        return response;
    }


    private boolean isNutsUser(String userId) {
        try {
            return Optional.ofNullable(imsClient.getUserDetailsByUserId(userId))
                    .map(GetUserResponse::getUserDetails)
                    .filter(this::checkCreatedAtIsSameAsToday)
                    .map(detail -> !detail.getAccountState().equalsIgnoreCase("temp"))
                    .orElseGet(() -> false);
        } catch (ServiceException e) {
            log.error("Failed to fetch info through ims with exception :: {}", e);
            return true;
        }
    }

    private boolean checkCreatedAtIsSameAsToday(UserDetailsDTO details) {
        Calendar instance = Calendar.getInstance();
        instance.setTimeInMillis(details.getCreatedTime().getTime());
        return DateUtils.isSameDay(instance, Calendar.getInstance());
    }

    private List<String> getBlackListedSenderList() {
        List<String> blockedSenderList = new ArrayList<>();
        try {
            blockedSenderList = parsingServiceAccessor.getBlackListedSenders();
            log.info("blockedSenderList :: {}", blockedSenderList);
        } catch (Exception e) {
            log.error("Failed to fetch black listed senders with exception :: {}", e);
        }
        return blockedSenderList;
    }

    private boolean isBlackListedUser(String userId) {
        try {
            return parsingServiceAccessor.isBlackListedUser(userId);
        } catch (Exception e) {
            log.error("Failed to fetch black listed senders with exception :: {}", e);
        }
        return false;
    }


    private Double computeFinalIncomeForAQB(Double averageMonthlyBalance) {
        Double finalIncome = 0D;
        if (averageMonthlyBalance <= 10000D) {
            finalIncome = 0D;
        } else if (averageMonthlyBalance <= 15000D) {
            finalIncome = Math.max(2 * averageMonthlyBalance, 20000);
        } else if (averageMonthlyBalance <= 35000D) {
            finalIncome = Math.max(1.2 * averageMonthlyBalance, 20000D);
        } else if (averageMonthlyBalance <= 75000D) {
            finalIncome = Math.max(0.7 * averageMonthlyBalance, 42000D);
        } else if (averageMonthlyBalance <= 100000D) {
            finalIncome = Math.max(0.4 * averageMonthlyBalance, 52500D);
            //Missing Range from 100000 to 150000
        } else if (averageMonthlyBalance >= 150000D && averageMonthlyBalance <= 300000D) {
            finalIncome = Math.max(0.25 * averageMonthlyBalance, 60000D);
        } else {
            finalIncome = Math.max(0.15 * averageMonthlyBalance, 75000);
        }
        return (finalIncome / 2);
    }

    private Double computeEstimatedSalaryForAQB(Double incomeEst) {
        Double finalEstIncome = 0D;
        if (incomeEst <= 10000)
            finalEstIncome = 0D;
        else if (incomeEst <= 20000)
            finalEstIncome = 10000D;
        else if (incomeEst <= 30000)
            finalEstIncome = 20000D;
        else if (incomeEst <= 40000)
            finalEstIncome = 30000D;
        else if (incomeEst <= 50000)
            finalEstIncome = 40000D;
        else if (incomeEst <= 60000)
            finalEstIncome = 50000D;
        else if (incomeEst <= 70000)
            finalEstIncome = 60000D;
        else if (incomeEst <= 80000)
            finalEstIncome = 70000D;
        else if (incomeEst <= 90000)
            finalEstIncome = 80000D;
        else if (incomeEst <= 100000)
            finalEstIncome = 90000D;
        else finalEstIncome = 100000D;

        return finalEstIncome;
    }

    private Double ifAfterComputationRequired(Double averageMonthlyBalance) {
        Double AQBEstimatedSalary = 0D;
        Double finalEstIncome = computeEstimatedSalaryForAQB(computeFinalIncomeForAQB(averageMonthlyBalance));
        if (finalEstIncome >= 100000) {
            AQBEstimatedSalary = afterComputeEstimatedSalaryForAQB(afterComputeFinalIncomeForAQB(averageMonthlyBalance));
        } else
            AQBEstimatedSalary = finalEstIncome;
        return AQBEstimatedSalary;
    }

    private Double afterComputeFinalIncomeForAQB(Double averageMonthlyBalance) {
        Double finalIncome = 0D;

        if (averageMonthlyBalance <= 7500) {
            finalIncome = 0D;
        } else if (averageMonthlyBalance <= 15000) {
            finalIncome = Math.max(2 * averageMonthlyBalance, 20000);
        } else if (averageMonthlyBalance <= 35000) {
            finalIncome = Math.max(1.2 * averageMonthlyBalance, 30000);
        } else if (averageMonthlyBalance <= 75000) {
            finalIncome = Math.max(0.7 * averageMonthlyBalance, 42000);
        } else if (averageMonthlyBalance <= 150000) {
            finalIncome = Math.max(0.4 * averageMonthlyBalance, 52500);
        } else if (averageMonthlyBalance <= 300000) {
            finalIncome = Math.max(0.25 * averageMonthlyBalance, 60000);
        } else {
            finalIncome = Math.max(0.15 * averageMonthlyBalance, 75000);
        }
        return finalIncome;
    }


    private Double afterComputeEstimatedSalaryForAQB(Double incomeEst) {
        Double finalEstIncome = 0D;

        if (incomeEst < 10000)
            finalEstIncome = 0D;
        else if (incomeEst < 20000)
            finalEstIncome = 10000D;
        else if (incomeEst < 30000)
            finalEstIncome = 20000D;
        else if (incomeEst < 40000)
            finalEstIncome = 30000D;
        else if (incomeEst < 50000)
            finalEstIncome = 40000D;
        else if (incomeEst < 60000)
            finalEstIncome = 50000D;
        else if (incomeEst < 70000)
            finalEstIncome = 60000D;
        else if (incomeEst < 80000)
            finalEstIncome = 65000D;
        else if (incomeEst < 90000)
            finalEstIncome = 67000D;
        else if (incomeEst < 100000)
            finalEstIncome = 70000D;
        else if (incomeEst < 150000)
            finalEstIncome = 80000D;
        else if (incomeEst < 200000)
            finalEstIncome = 85000D;
        else if (incomeEst < 300000)
            finalEstIncome = 90000D;
        else if (incomeEst < 400000)
            finalEstIncome = 95000D;
        else
            finalEstIncome = 100000D;
        return finalEstIncome;
    }


    private Double computeIncomeForEPFO(Double monthlyPFContribution) {
        BigDecimal income = BigDecimal.ZERO;
        BigDecimal basicIncome = BigDecimal.valueOf(monthlyPFContribution).divide(BigDecimal.valueOf(0.18), 0);
        BigDecimal estimatedIncome = basicIncome.divide(BigDecimal.valueOf(0.5), 0);
        log.info("Estimated Income from EPF :: {}", estimatedIncome);

        if (isLessThen.apply(estimatedIncome, BigDecimal.valueOf(10000))) {
            income = basicIncome;
        } else if (isLessThen.apply(estimatedIncome, BigDecimal.valueOf(20000))) {
            income = BigDecimal.valueOf(5000);
        } else if (isLessThen.apply(estimatedIncome, BigDecimal.valueOf(30000))) {
            income = BigDecimal.valueOf(10000);
        } else if (isLessThen.apply(estimatedIncome, BigDecimal.valueOf(40000))) {
            income = BigDecimal.valueOf(20000);
        } else if (isLessThen.apply(estimatedIncome, BigDecimal.valueOf(50000))) {
            income = BigDecimal.valueOf(30000);
        } else if (isLessThen.apply(estimatedIncome, BigDecimal.valueOf(60000))) {
            income = BigDecimal.valueOf(40000);
        } else if (isLessThen.apply(estimatedIncome, BigDecimal.valueOf(70000))) {
            income = BigDecimal.valueOf(50000);
        } else if (isLessThen.apply(estimatedIncome, BigDecimal.valueOf(80000))) {
            income = BigDecimal.valueOf(60000);
        } else if (isLessThen.apply(estimatedIncome, BigDecimal.valueOf(90000))) {
            income = BigDecimal.valueOf(65000);
        } else if (isLessThen.apply(estimatedIncome, BigDecimal.valueOf(100000))) {
            income = BigDecimal.valueOf(90000);
        } else {
            income = BigDecimal.valueOf(100000);
        }
        return income.doubleValue();
    }

    private Map<String, Map<String, String>> prepareDataForAMB(List<SmsInfo> smsInfoList) {
        /*return smsInfoList.stream()
                .map(this::getTokens)
                .filter(tokensMap -> StringUtils.isNotEmpty(tokensMap.get("BALANCE")))
                .collect(groupingBy(tokensMap -> tokensMap.get("ACCOUNTNUMBER"), toMap(tokensMap -> tokensMap.get("DATE"), tokensMap -> tokensMap.get("BALANCE"))));*/
        Map<String, Map<String, String>> mapOfMap = new HashMap<>();
        int count = 0;
        for (SmsInfo smsInfo : smsInfoList) {
            String msgDomain = regexTokenizerManager.getDomain(smsInfo.getMsg(), smsInfo.getSender());
            if(!msgDomain.equals(Constants.CREDIT_CARD) && !msgDomain.equals(Constants.WALLET) && !msgDomain.equals(Constants.UTILITY)) {
                Map<String, String> tokensMap = getTokens(smsInfo);
                if (tokensMap != null && StringUtils.isNotEmpty(tokensMap.get(Constants.ACCOUNT_NUMBER))) {
                    Map<String, String> map1 = mapOfMap.getOrDefault(tokensMap.get(Constants.ACCOUNT_NUMBER), new LinkedHashMap<>());
                    if (StringUtils.isNotEmpty(tokensMap.get(Constants.BALANCE))) {
                        count++;
                        map1.put(tokensMap.get(Constants.DATE), tokensMap.get(Constants.BALANCE));
                        mapOfMap.put(tokensMap.get(Constants.ACCOUNT_NUMBER), map1);
                    }
                }
            }
        }
        log.info("Count of tokenized message to compute income for userId : {} is {}", smsInfoList.size() == 0 ? 0 : smsInfoList.get(0).getImsId(), count);
        log.info("Data prepared for Amb for userId : {} is {}", smsInfoList.size() == 0 ? 0 : smsInfoList.get(0).getImsId(), mapOfMap);
        return mapOfMap;
    }

    private List<SmsInfo> filterValidTransactionalMsg(IncomeSmsRequest request) {
        List<SmsInfo> smsInfoList = covertSmsRequest(request);
        ListIterator<SmsInfo> smsInfoListIterator = smsInfoList.listIterator();
        while (smsInfoListIterator.hasNext()) {
            SmsInfo smsInfo = smsInfoListIterator.next();
            String message = smsInfo.getMsg();
            if (StringUtils.isBlank(message) || smsTokenizerManager.checkIfInValidMessage(message)) {
                log.info("Invalid Message Structure Ims Id : {}, Time {}, and message is {}", request.getUserId(),
                        System.currentTimeMillis(), message);
                smsInfoListIterator.remove();
            }
           /* else if (!smsTokenizerManager.isSmsTransactional(smsInfo.getMsg())) {
                log.info("Promotional Message {}:", smsInfo.getMsg());
                smsInfoList.remove(smsInfo);
                return;
            }*/
        }
        Collections.sort(smsInfoList, Collections.reverseOrder());
        return smsInfoList;
    }

    private List<SmsInfo> filterValidTransactionalMsgV2(IncomeSmsRequestV2 request) {
        ListIterator<SmsInfo> smsInfoListIterator = request.getSmsInfoList().listIterator();
        while (smsInfoListIterator.hasNext()) {
            SmsInfo smsInfo = smsInfoListIterator.next();
            String message = smsInfo.getMsg();
            if (StringUtils.isBlank(message) || smsTokenizerManager.checkIfInValidMessage(message)) {
                log.info("Invalid Message Structure Due to ? Ims Id : {}, Time {}, and message is {}", request.getUserId(),
                        System.currentTimeMillis(), message);
                smsInfoListIterator.remove();
            }
        }
        Collections.sort(request.getSmsInfoList(), Collections.reverseOrder());
        return request.getSmsInfoList();
    }

    public IncomeSmsRequest filterActiveSendersFromSmsRequest(IncomeSmsRequest smsRequest) {
        Set<String> activeSenders = senderDao.getActiveSendersForIncome();
        List<SmsDetail> smsDetails = new ArrayList<>();
        for (SmsDetail e : smsRequest.getSmsDetails()) {
            Pattern specialCharacter = Pattern.compile("[^a-z0-9-]", Pattern.CASE_INSENSITIVE);
            Matcher scCheck = specialCharacter.matcher(e.getSender().toLowerCase());
            boolean res = scCheck.find();
            if (res) {
                continue;
            }
            if (activeSenders.contains(e.getSender().toLowerCase())) {
                if (e.getSender().toLowerCase().contains("epfo")) {
                    log.info("Epfo Message Filtered {}", e);
                }
                smsDetails.add(e);
            } else {
                boolean noActiveSender = true;
                List<String> originalSenders = Arrays.asList("acb", "ace", "airbnk", "alb", "anb", "apn", "aubank", "axb", "axis", "bmb", "bndnhl", "bob", "boi", "bom", "cbi", "citiba", "cnb", "crb", "csbbnk", "ctsukt", "dcb", "dnb", "epfo", "fastdv", "fbl", "fchrge", "fedbnk", "fgucbk", "fmcluc", "fncare", "fngwbk", "fromsc", "fsloct", "gmnwii", "gramen", "gsc", "hastib", "hcb", "hdf", "hle", "hrmscc", "iapprv", "icb", "ici", "icmtrg", "idb", "idfcfb", "iib", "imahyd", "inb", "indbnk", "indusb", "ing", "iob", "ipibbi", "isecld", "isrvce", "itc", "itdbbk", "itr", "its", "jbs", "jgr", "jkb", "jkg", "jpcbnk", "jsb", "karbnk", "kbankt", "kblbnk", "kbsavs", "kcbank", "kcc", "kchrry", "kcm", "kcubnk", "kebank", "khladm", "kmb", "kotakb", "krnbnk", "krtbnk", "ktb", "ktk", "kvb", "lcpcko", "lctscs", "liccrd", "mabhyd", "mahabk", "mansab", "mcapex", "mcubnk", "mgccbl", "mgsbbk", "milnia", "mimtmx", "misdep", "mobank", "mobtgb", "mrb", "muc", "nbb", "nbc", "ncb", "ngb", "nim", "nlr", "nmb", "nob", "nrb", "ntb", "nvb", "oac", "obc", "ojs", "pav", "pay", "pmc", "pnb", "psb", "rbl", "sbh", "sbi", "sbj", "sbm", "sbp", "sbt", "scbank", "sib", "slceit", "src", "syb", "synbnk", "tmb", "tmkhtabk", "ubi", "uco", "ujjivn", "unicrd", "unionb", "uob", "utkbnk", "vjb", "ybl", "yesbnk");
                ListIterator<String> activeSendersIterator = originalSenders.listIterator();
                while (activeSendersIterator.hasNext()) {
                    String s = activeSendersIterator.next();
                    try {
                        if (RegexPatternMatchUtil.isMatching(e.getSender().toLowerCase(), s)) {
                            if (e.getSender().toLowerCase().contains("epfo")) {
                                log.info("Epfo Message Filtered {}", e);
                            }
                            sendersFilter.insertNewSender(s, e.getSender());
                            smsDetails.add(e);
                            cacheService.evictAllCacheByName(CacheName.ACTIVE_SENDERS);
                            noActiveSender = false;
                            break;
                        }
                    } catch (Exception err) {
                        log.error("Exception while filtering the senders {} for sender {} and active sender {} data {}",
                                JsonUtil.writeValueAsString(err.getMessage()), e.getSender(), s, e);
                        throw err;
                    }
                }
                if (e.getSender().toLowerCase().contains("epfo") && noActiveSender) {
                    log.info("Epfo Message not Filtered for userId {} is {}", smsRequest.getUserId(), e);
                }
            }

        }
        log.info("smsDetails :: ", smsDetails);
        smsRequest.setSmsDetails(smsDetails);
        return smsRequest;
    }

    public IncomeSmsRequestV2 filterActiveSendersFromSmsRequestV2(IncomeSmsRequestV2 smsRequest) {
        Set<String> activeSenders = senderDao.getActiveSendersForIncome();
        ListIterator<SmsInfo> iterator = smsRequest.getSmsInfoList().listIterator();
        while (iterator.hasNext()) {
            SmsInfo e = iterator.next();
            Pattern specialCharacter = Pattern.compile("[^a-z0-9-]", Pattern.CASE_INSENSITIVE);
            Matcher scCheck = specialCharacter.matcher(e.getSender().toLowerCase());
            boolean res = scCheck.find();
            if (res) {
                continue;
            }
            if (activeSenders.contains(e.getSender().toLowerCase())) {
                continue;
            } else {
                boolean noActiveSender = true;
                List<String> originalSenders = Arrays.asList("acb", "ace", "airbnk", "alb", "anb", "apn", "aubank", "axb", "axis", "bmb", "bndnhl", "bob", "boi", "bom", "cbi", "citiba", "cnb", "crb", "csbbnk", "ctsukt", "dcb", "dnb", "epfo", "fastdv", "fbl", "fchrge", "fedbnk", "fgucbk", "fmcluc", "fncare", "fngwbk", "fromsc", "fsloct", "gmnwii", "gramen", "gsc", "hastib", "hcb", "hdf", "hle", "hrmscc", "iapprv", "icb", "ici", "icmtrg", "idb", "idfcfb", "iib", "imahyd", "inb", "indbnk", "indusb", "ing", "iob", "ipibbi", "isecld", "isrvce", "itc", "itdbbk", "itr", "its", "jbs", "jgr", "jkb", "jkg", "jpcbnk", "jsb", "karbnk", "kbankt", "kblbnk", "kbsavs", "kcbank", "kcc", "kchrry", "kcm", "kcubnk", "kebank", "khladm", "kmb", "kotakb", "krnbnk", "krtbnk", "ktb", "ktk", "kvb", "lcpcko", "lctscs", "liccrd", "mabhyd", "mahabk", "mansab", "mcapex", "mcubnk", "mgccbl", "mgsbbk", "milnia", "mimtmx", "misdep", "mobank", "mobtgb", "mrb", "muc", "nbb", "nbc", "ncb", "ngb", "nim", "nlr", "nmb", "nob", "nrb", "ntb", "nvb", "oac", "obc", "ojs", "pav", "pay", "pmc", "pnb", "psb", "rbl", "sbh", "sbi", "sbj", "sbm", "sbp", "sbt", "scbank", "sib", "slceit", "src", "syb", "synbnk", "tmb", "tmkhtabk", "ubi", "uco", "ujjivn", "unicrd", "unionb", "uob", "utkbnk", "vjb", "ybl", "yesbnk");
                ListIterator<String> activeSendersIterator = originalSenders.listIterator();
                while (activeSendersIterator.hasNext()) {
                    String s = activeSendersIterator.next();
                    try {
                        if (RegexPatternMatchUtil.isMatching(e.getSender().toLowerCase(), s)) {
                            sendersFilter.insertNewSender(s, e.getSender());
                            cacheService.evictAllCacheByName(CacheName.ACTIVE_SENDERS);
                            noActiveSender = false;
                            break;
                        }
                    } catch (Exception err) {
                        log.error("Exception while filtering the senders {} for sender {} and active sender {} data {}",
                                JsonUtil.writeValueAsString(err.getMessage()), e.getSender(), s, e);
                        throw err;
                    }
                }
                if (noActiveSender) {
                    iterator.remove();
                }
            }
        }
        return smsRequest;
    }

    public List<SmsInfo> covertSmsRequest(IncomeSmsRequest request) {
        List<SmsInfo> smsInfos = new ArrayList<>();
        request.getSmsDetails().stream().forEach(s -> {
            SmsInfo smsInfo = new SmsInfo();
            smsInfo.setImsId(request.getUserId());
            smsInfo.setMsg(s.getMsg());
            smsInfo.setSender(s.getSender());
            smsInfo.setMsgTime(s.getDate());
            smsInfos.add(smsInfo);
        });
        return smsInfos;
    }

    private Map<String, String> getTokens(SmsInfo smsInfo) {
        Map<String, String> tokens = new HashMap<>();
        try {
            String domain = null;
            smsTokenizerManager.generateTemplate(smsInfo);
            if (StringUtils.isBlank(smsInfo.getHashcode())) {
                return tokens;
            }
            final Optional<TemplateModel> templateModelOptional = templateServiceDao.getTemplateByHashCode(smsInfo.getHashcode());

            if (templateModelOptional.isPresent() && StringUtils.isNotEmpty(templateModelOptional.get().getTrainedMessage())) {

                if (!templateModelOptional.get().getActive()) {
                    return tokens;
                }
                domain = templateModelOptional.get().getDomainId().getDomainName();
                smsInfo.setMsg(SmsTokenizerManager.epfoPreProcess(smsInfo.getMsg(), domain));
                try {
                    TokenNameFinderModel model1 = (TokenNameFinderModel) modelTrainer.getModel(smsTokenizerManager.getTrainingParameters(), new NameSampleDataStream((ObjectStreamUtils.createObjectStream(templateModelOptional.get().getTrainedMessage()))));
                    tokens = modelRunnerService.getTokensByModel(new NameFinderME(model1), smsInfo.getMsg());

                } catch (IOException e) {
                    log.info("Error with get token for imsid {} ", e, smsInfo.getImsId());
                }
                smsTokenizerManager.sanitizeTokenValue(tokens, domain);
            } else {
                log.info("before Regex response for imsid {} ", smsInfo.getImsId());
                RegexResponse regexResponse = smsTokenizerManager.handleRegex(smsInfo.getMsg(), smsInfo);
                log.info("Regex response {} for imsid {} ", regexResponse, smsInfo.getImsId());
                domain = regexResponse.getDomain();
                tokens = regexResponse.getTokens();
            }
            smsTokenizerManager.postProcessing(smsInfo, tokens, domain);
            log.info("Tokens :: {} for imsid {}", tokens, smsInfo.getImsId());
        } catch (Exception e) {
            log.info("Exception {} for imsId {}", e.getMessage(), smsInfo.getImsId());
        }
        return tokens;
    }

    List<SmsInfo> getSalaryMessage(List<SmsInfo> smsList) {
        List<SmsInfo> newSmsList = new ArrayList<>();
        smsList.stream().forEach(smsInfo -> {
            if (!smsInfo.getMsg().toLowerCase().contains("earlysalary") && !smsInfo.getMsg().toLowerCase().contains("consolidated charges") && smsInfo.getMsg().toLowerCase().contains("salary") && (smsInfo.getMsg().toLowerCase().contains("credit") || smsInfo.getMsg().toLowerCase().contains("deposit"))) {                newSmsList.add(smsInfo);

            }
        });
        return newSmsList;
    }

    List<SmsInfo> getEPFOMessage(List<SmsInfo> smsList) {
        List<SmsInfo> newSmsList = new ArrayList<>();
        smsList.stream().forEach(smsInfo -> {
            if (smsInfo.getSender().toLowerCase().contains("epf")) {
                newSmsList.add(smsInfo);
            }
        });
        return newSmsList;
    }


    /////////////////   Compute AMB    /////////////////
    public static String getMaxDate(Map<String, Map<String, String>> inputMap) {
        Boolean isFirstDate = true;
        String maxDate = "";

        for (Map.Entry<String, Map<String, String>> entry : inputMap.entrySet()) {
            Map<String, String> currentInnerMap;
            currentInnerMap = entry.getValue();

            Iterator<Map.Entry<String, String>> itr = currentInnerMap.entrySet().iterator();

            while (itr.hasNext()) {

                Map.Entry<String, String> currentElement = itr.next();
                String currentDate = currentElement.getKey();
                if (isFirstDate) {
                    maxDate = currentElement.getKey();
                    isFirstDate = false;
                }

                try {
                    SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd");
                    Date date1 = format.parse(currentDate);
                    Date date2 = format.parse(maxDate);

                    if (date1.compareTo(date2) >= 0) {
                        maxDate = currentDate;
                    }
                } catch (ParseException e) {
                    log.info("Error in parsing date :: {}", e);
                }

            }
        }
        return maxDate;
    }

    public static int getDaysBetweenDate(String startDate, String endDate) {
        SimpleDateFormat myFormat = new SimpleDateFormat("yyyy-MM-dd");

        try {
            Date date1 = myFormat.parse(startDate);
            Date date2 = myFormat.parse(endDate);
            long diff = date2.getTime() - date1.getTime();
            return (int) (TimeUnit.DAYS.convert(diff, TimeUnit.MILLISECONDS));
        } catch (ParseException e) {
            log.info("Incorrect Date Format Passed, Exception Occurred :: {}", e);
        }
        return 0;
    }

    public static AMBResponse getAmb(Map<String, Map<String, String>> inputMap) {
        if (inputMap.size() == 0) {
            return null;
        }
        float totalBalanceOfAllAccounts = 0;
        int daysConsidered = 0;
        String startDate = "";
        String maxDate = getMaxDate(inputMap);

        for (Map.Entry<String, Map<String, String>> entry : inputMap.entrySet()) {
            Boolean isFirstDate = true;
            Map<String, String> currentInnerMap;
            currentInnerMap = entry.getValue();

            Iterator<Map.Entry<String, String>> itr = currentInnerMap.entrySet().iterator();
            Iterator<Map.Entry<String, String>> nextItr = currentInnerMap.entrySet().iterator();
            nextItr.next();

            while (itr.hasNext()) {

                Map.Entry<String, String> currentElement = itr.next();
                if (isFirstDate) {
                    isFirstDate = false;
                    startDate = currentElement.getKey();
                    daysConsidered = Math.max(daysConsidered, getDaysBetweenDate(startDate, maxDate) + 1);
                }
                String value = currentElement.getValue() == null ? "0" : currentElement.getValue();
                if (!itr.hasNext()) {
                    totalBalanceOfAllAccounts += Float.parseFloat(value) * (getDaysBetweenDate(currentElement.getKey(), maxDate) + 1);
                    break;
                }
                Map.Entry<String, String> elementNextToCurrentElement = nextItr.next();

                long numberOfDays = getDaysBetweenDate(currentElement.getKey(), elementNextToCurrentElement.getKey());
                totalBalanceOfAllAccounts += numberOfDays * Float.parseFloat(value);
            }
        }
        long amb = totalBalanceOfAllAccounts == 0 ? 0 : (long) (totalBalanceOfAllAccounts / daysConsidered);
        AMBResponse response = new AMBResponse();
        response.setAmb(Double.valueOf(amb));
        response.setWeightedBalance(Double.valueOf(totalBalanceOfAllAccounts));
        response.setDaysConsidered(daysConsidered);
        return response;
    }

    ///////////// Upload Data To S3 ////////////////

    private void uploadIntoS3(final AbstractAppRequest request, String imsId) {
        final Long start = System.currentTimeMillis();
        final Date dates = new Date(System.currentTimeMillis());
        final Calendar calendar = GregorianCalendar.getInstance();
        calendar.setTime(dates);
        final int year = calendar.get(Calendar.YEAR);
        final int month = calendar.get(Calendar.MONTH) + 1;
        final int date = calendar.get(Calendar.DATE);
        final String bucketKey = "sms-parsing/new-users" + "/" + year + "/" + month + "/" + date + "/" + imsId + ".json.gz";        log.info("Saving Data for key {}", bucketKey);
        try {
            s3Accessor.saveData(bucketKey, HttpRequestUtils.compressGzip(JsonUtil.writeValueAsString(request)));
        } catch (IOException e) {
            throw new RuntimeException("Failed to upload data to S3 with exception", e);
        }
        final Long end = System.currentTimeMillis();
        log.info("Time taken to upload the records {}, ", end - start);
    }

    @Override
    public String readDataFromS3(IncomeModel incomeModel) {
        log.info("Income Model :: {}" + incomeModel);
        final Calendar calendar = GregorianCalendar.getInstance();
        calendar.setTime(incomeModel.getCreatedAt());
        final int year = calendar.get(Calendar.YEAR);
        final int month = calendar.get(Calendar.MONTH) + 1;
        final int date = calendar.get(Calendar.DATE);
        final String bucketKey = "sms-parsing/new-users" + "/" + year + "/" + month + "/" + date + "/";
        log.info("bucket key :: {}", bucketKey);
        return s3Accessor.getData(bucketKey, incomeModel.getImsId());
    }

    @Override
    @SneakyThrows
    public IncomeSmsRequest getIncomeSmsRequest(HttpServletRequest request, AuthorizationContext tokenizerAuthorizationContext) {
        IncomeSmsRequest incomeSmsRequest;
        String url = request.getRequestURL().toString();
        log.info("Intercepting http request in DecryptionInterceptor with url: " + url);
        byte[] encodedRequestBodyAsBytes = request.getReader().lines().collect(Collectors.joining(System.lineSeparator())).getBytes(StandardCharsets.UTF_8);
        ;
        String decryptedEncoded = SMSEncryptionUtil.getDefault("WmZq4t7w!z%C*F-JaNdRfUjXn2r5u8x/", "*@!FreeFc@007#$&", new byte[12]).decryptOrNull(new String(encodedRequestBodyAsBytes));
        String requestBodyAsString = HttpRequestUtils
                .decompressGzip(HttpRequestUtils.decodeBase64(decryptedEncoded.getBytes()));
        requestBodyAsString = requestBodyAsString.replace("\0", "");
        request.setAttribute(HttpAttributeNames.REQUEST_BODY, new String(requestBodyAsString));
        incomeSmsRequest = mapper.readValue(requestBodyAsString, IncomeSmsRequest.class);
        incomeSmsRequest.setUserId(tokenizerAuthorizationContext.get(UserIdentifier.USERID.name()));
        incomeSmsRequest.setMobileNumber(tokenizerAuthorizationContext.get(UserIdentifier.MOBILE.name()));
        return incomeSmsRequest.setUpdatedBy(AppConstants.MAIN_THREAD);
    }

    @Override
    @SneakyThrows
    public IncomeSmsRequestV2 getIncomeSmsRequestV2(HttpServletRequest request, AuthorizationContext tokenizerAuthorizationContext) {
        IncomeSmsRequestV2 incomeSmsRequestV2;
        String url = request.getRequestURL().toString();
        log.info("Intercepting http request in DecryptionInterceptor with url: " + url);
        byte[] encodedRequestBodyAsBytes = request.getReader().lines().collect(Collectors.joining(System.lineSeparator())).getBytes(StandardCharsets.UTF_8);
        ;
        String decryptedEncoded = SMSEncryptionUtil.getDefault("WmZq4t7w!z%C*F-JaNdRfUjXn2r5u8x/", "*@!FreeFc@007#$&", new byte[12]).decryptOrNull(new String(encodedRequestBodyAsBytes));
        String requestBodyAsString = HttpRequestUtils
                .decompressGzip(HttpRequestUtils.decodeBase64(decryptedEncoded.getBytes()));
        requestBodyAsString = requestBodyAsString.replace("\0", "");
        request.setAttribute(HttpAttributeNames.REQUEST_BODY, new String(requestBodyAsString));
        incomeSmsRequestV2 = mapper.readValue(requestBodyAsString, IncomeSmsRequestV2.class);
        incomeSmsRequestV2.setUserId(tokenizerAuthorizationContext.get(UserIdentifier.USERID.name()));
        incomeSmsRequestV2.setMobileNumber(tokenizerAuthorizationContext.get(UserIdentifier.MOBILE.name()));
        return incomeSmsRequestV2.setUpdatedBy(AppConstants.MAIN_THREAD);
    }


    @Override
    public Map<String, String> getTokenFromNameFinder(String trainedMsg, String testMsg) {
        Map<String, String> tokens = new HashMap<>();
        try {
            TokenNameFinderModel model1 = (TokenNameFinderModel) modelTrainer.getModel(smsTokenizerManager.getTrainingParameters(), new NameSampleDataStream((ObjectStreamUtils.createObjectStream(trainedMsg))));
            tokens = modelRunnerService.getTokensByModel(new NameFinderME(model1), testMsg);
        } catch (Exception e) {
            log.info("Unable to get model : {} ", e);
        }
        return tokens;
    }

    ;
}