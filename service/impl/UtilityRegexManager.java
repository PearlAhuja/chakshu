package com.freecharge.smsprofilerservice.service.impl;

import com.freecharge.smsprofilerservice.constant.Constants;
import com.freecharge.smsprofilerservice.model.SmsInfo;
import com.freecharge.smsprofilerservice.service.impl.nlp.ModelRunnerService;
import com.freecharge.smsprofilerservice.sheet.RegexResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@Slf4j
public class UtilityRegexManager {


    @Autowired
    ModelRunnerService modelRunnerService;

    public List<String> getCategory(String originalMessage) {
        List<String> utilityList = new ArrayList<>();

            Pattern dump = Pattern.compile("(swiggy|amazon|Now pay your bills for|full power|powered by|Dear client, shop|superpower|fully executed order| Dear customer, your Loan|POWERMECH|Due to planned maintenance of systems|Sad demise|drawing power|empower|fuel|member Your Electri.city power close|Worried about bill| ENERGIE Credit Card|powered merchant outlet|E-Voucher|PAYBACK Points|trading|order:buy|order:sell|have been reversed )",Pattern.CASE_INSENSITIVE);
        Matcher dumpMatcher = dump.matcher(originalMessage);
        if(dumpMatcher.find())
        {
            utilityList.add(Constants.OTHER);
            utilityList.add(Constants.OTHER);
            return utilityList;
        }

        Pattern electricity = Pattern.compile("(JDVVNL|TSSPDCL|MSEDCL|BESCOM|TNEB|JBVNL|AVVNL|BSES|JVVNL|HPCL|UHBVN|power|bpcl|UPPCL|nigam|pow|energy|electricity|garuda power private limited|eastern power distribution|jvvnl|adani electricity|ajmer vidyut vitran nigam limited|apcpdcl|apdcl|apspdc|apspdcl|assam power distribution|avvnl|b.e.s.t mumbai electricity|bangalore electricity|bescom|bharatpur electricity services ltd. (besl)|bihar power distribution|bijli vitran nigam|bikaner electricity supply|bses|bses rajdhan|bses rajdhani|bses yamuna|calcutta electric supply corporation (cesc)|cesc electricity|cesc kolkata|cess sircilla|chamundeshwari|chamundeshwari electricity|chattisgarh state power|chhattisgarh state electricity|chhattisgarh state power distribution|coll a c west bengal state electricity distribution company limited|cspdcl|dakshin haryana bijli vitran nigam|dhbvn|eastern power distribution company limited|electricity board|electricity dpt puduch|goa electricity department|goaelectricitydepartment|government of puducherry electricity department|gulbarga|himachal pradesh state electricity board|hpcl|hubli electricity|jaipur vidyut nigam|jammu and kashmir power development department|jbvnl|jdvvnl|kanpur electricity supply company|kedl|kerala state electricity board|kesco|kota electricity distribution limited (kedl)|maharashtra state electricity|mangalore electricity supply|meghalaya power dist corp ltd|mp paschim kshetra electricity|mp poorv kshetra electricity|mpez|mppkvvcl|msedcl|nbpdcl|ndmc electricity|nesco, odisha|new delhi municipal council electricity|north bihar power|power and electricity department - mizoram|pspcl|sbpdcl|south bihar power|southco|southco, odisha|southern power|st electricity distribu|tamil nadu electricity|tata power|tneb|torrent power|tp ajmer distribution ltd|tp central odisha distribution|tpcodl|tpddl|tpnodl|tripura electricity|trivandrum-power house rd|tsnpdcl|uhbvn|upcl|uppcl|uppcl-rural|uppcl-urban electricity|uttar haryana bijli|uttar pradesh power corp ltd|wbsedcl|wesco|west bengal state electricity|Dakshin Haryana Bijli Vitran|JHARKHAND BIJLI VITRAN)", Pattern.CASE_INSENSITIVE);
        Matcher electricityMatcher = electricity.matcher(originalMessage);
        if (electricityMatcher.find()) {
            utilityList.add(Constants.ELECTRICITY);
            utilityList.add(Constants.POST_PAID);
            return utilityList;
        }

        Pattern water = Pattern.compile("([\\s\\.\\,\\:\\-]water[\\s\\.\\,\\:\\-]|jal|Uttarakhand Jal Sansthan|Delhi Jal Board|[\\s\\.\\,\\:\\-]phed[\\s\\.\\,\\:\\-]|Bangalore Water Supply and Sewerage Board |Hyderabad Metropolitan Water Supply and Sewerage Board|Kerala Water Authority|KWA|MCGM |MCGM Water Department|Municipal Corporation of Gurugram|MCG|New Delhi Municipal Council|NDMC|PHED|Umesh water supply|Uttarakhand Jal Sansthan |WATER SUPPLY MUMBAI)", Pattern.CASE_INSENSITIVE);
        Matcher waterMatcher = water.matcher(originalMessage);
        if (waterMatcher.find()) {
            utilityList.add(Constants.WATER);
            utilityList.add(Constants.POST_PAID);
            return utilityList;
        }

        Pattern fasTag = Pattern.compile("(fastag)", Pattern.CASE_INSENSITIVE);
        Matcher fastTagMatcher = fasTag.matcher(originalMessage);
        if (fastTagMatcher.find()) {
            utilityList.add(Constants.FASTAG);
            utilityList.add(Constants.REMINDER);
            return utilityList;
        }

        Pattern gas = Pattern.compile("(gas|lpg)", Pattern.CASE_INSENSITIVE);
        Matcher gasMatcher = gas.matcher(originalMessage);
        if (gasMatcher.find()) {
            utilityList.add(Constants.GAS);

            //check for prepaid and postpaid
            Pattern postpaid = Pattern.compile("(postpaid|due|bill)", Pattern.CASE_INSENSITIVE);
            Matcher postpaidMatcher = postpaid.matcher(originalMessage);
            if (postpaidMatcher.find()) {
                utilityList.add(Constants.POST_PAID);
                return utilityList;
            }

            Pattern prepaid = Pattern.compile("(prepaid|cylinder)", Pattern.CASE_INSENSITIVE);
            Matcher prepaidMatcher = prepaid.matcher(originalMessage);
            if (prepaidMatcher.find()) {
                utilityList.add(Constants.PRE_PAID);
                return utilityList;
            }

            //In case prepaid or postpaid not matched set its default to other
            utilityList.add(Constants.OTHER);
            return utilityList;
        }

        Pattern broadband = Pattern.compile("(broadband)", Pattern.CASE_INSENSITIVE);
        Matcher broadbandMatcher = broadband.matcher(originalMessage);
        if (broadbandMatcher.find()) {
            //check for prepaid and postpaid
            utilityList.add(Constants.BROADBAND);

            Pattern postpaid = Pattern.compile("(postpaid|due|bill)", Pattern.CASE_INSENSITIVE);
            Matcher postpaidMatcher = postpaid.matcher(originalMessage);
            if (postpaidMatcher.find()) {
                utilityList.add(Constants.POST_PAID);
                return utilityList;
            }

            Pattern prepaid = Pattern.compile("(prepaid)", Pattern.CASE_INSENSITIVE);
            Matcher prepaidMatcher = prepaid.matcher(originalMessage);
            if (prepaidMatcher.find()) {
                utilityList.add(Constants.PRE_PAID);
                return utilityList;
            }

            utilityList.add(Constants.OTHER);
            return utilityList;
        }

        Pattern telecom = Pattern.compile("(landline| airtel| jio|mtnl | vi | bsnl)", Pattern.CASE_INSENSITIVE);
        Matcher telecomMatcher = telecom.matcher(originalMessage);
        if (telecomMatcher.find()) {
            utilityList.add(Constants.TELECOM);

            //finding subcategory
            Pattern postpaid = Pattern.compile("(postpaid)", Pattern.CASE_INSENSITIVE);
            Matcher postpaidMatcher = postpaid.matcher(originalMessage);
            if (postpaidMatcher.find()) {
                utilityList.add(Constants.POST_PAID);
                return utilityList;
            }

            Pattern prepaid = Pattern.compile("(prepaid)", Pattern.CASE_INSENSITIVE);
            Matcher prepaidMatcher = prepaid.matcher(originalMessage);
            if (prepaidMatcher.find()) {
                utilityList.add(Constants.PRE_PAID);
                return utilityList;
            }

            Pattern landline = Pattern.compile("(landline)", Pattern.CASE_INSENSITIVE);
            Matcher landlineMatcher = landline.matcher(originalMessage);
            if (landlineMatcher.find()) {
                utilityList.add(Constants.LANDLINE);
                return utilityList;
            }

            utilityList.add(Constants.OTHER);
            return utilityList;
        }

        //In case no match found
        utilityList.add(Constants.OTHER);
        utilityList.add(Constants.OTHER);
        return utilityList;
    }


    public static String fetchPaymentOrDueDate(String originalMessage) {
        Pattern fetchPaymentOrDueDate = Pattern.compile("((on|by|before)\\s?([0-9]{1,4}(-|\\/)?\\s*[a-zA-Z0-9]{2,3}(-|\\/)?\\s*[A-z0-9]{0,4}\\s?[0-9]{0,4}))", Pattern.CASE_INSENSITIVE);
        Matcher m = fetchPaymentOrDueDate.matcher(originalMessage);
        if (m.find()) {
            return m.group();
        }
        Pattern date2 = Pattern.compile("([0-9]{1,4}(-|\\/)?\\s*[a-zA-Z0-9]{2,3}(-|\\/)?\\s*[A-z0-9]{0,4}\\s?[0-9]{0,4})\\s?(is the last day)",Pattern.CASE_INSENSITIVE);
        Matcher m2 = date2.matcher(originalMessage);
        if(m2.find())
        {
            return(m2.group());
        }
        return "";
    }

    public static String fetchPaidOrDueAmount(String originalMessage) {
        Pattern fetchPaidOrDueAmount = Pattern.compile("(?:Rs|INR|amount|Bal)\\s*([.,:-])?\\s*\\d+(?:[.,]\\d+)*", Pattern.CASE_INSENSITIVE);
        Matcher m = fetchPaidOrDueAmount.matcher(originalMessage);
        if (m.find()) {
            return m.group(0);
        }
        return "EMPTY";
    }


    public String fetchUtilityAccount(String originalMessage) {
        switch (getCategory(originalMessage).get(0)) {
            case Constants.ELECTRICITY:
                Pattern fetchElectricityAccount = Pattern.compile("(?:[^Ref |^Reference] number|Con Id|Con no|[^via| ^bank] a\\/c|[^Bank ]Account|Limit-|KNO|\\) -| -|\\)| \\(generated for|[\\d\\.\\,]+ for|Bill for|Consumer No|Consumer Number|bill-|Account No|CA No| account|Limited|Ltd|Customer ID|for|[^Ref|^Reference ]No|with|against|RAPDR|Electricity|for No|for BU:|Account ID|Yamuna|URBAN|Power|Mumbai|Com\\-?)(?:\\s?\\.?\\:?\\s?)([A-z0-9]{0,5}[0-9]{2,15}\\-?[A-z0-9]{0,10})(?:.|\\s)", Pattern.CASE_INSENSITIVE);
                Matcher m1 = fetchElectricityAccount.matcher(originalMessage);

                if (m1.find()) {
                    return m1.group(1);
                }
                break;


            case Constants.TELECOM:

                if (getCategory(originalMessage).get(1).equals("LANDLINE")) {
                    Pattern fetchLandlineAccount = Pattern.compile("(?:for|Customer|Consumer ID|Consumer No|Sub Code|USER ID|Username|Landline|Individual|account no|account|\\(|relationship number|STD code|bill|a\\/c|\\(|against|with)(?:\\s?\\-?\\.?\\:?\\s?)([A-z]{0,1}[0-9]{8,15})(?:.|\\s)", Pattern.CASE_INSENSITIVE);
                    Matcher m4 = fetchLandlineAccount.matcher(originalMessage);
                    if (m4.find()) {
                        return m4.group(1);
                    }

                    Pattern fetchPostpaidAccount = Pattern.compile("(?:for|Customer|Consumer ID|Consumer No|Sub Code|USER ID|Username|Account Number|Landline|Individual|account no|account|\\(|relationship number|STD code|bill|a\\/c|Postpaid\\s?\\-?|\\)|Number|No|#|against|Postpaid|Mobile Number|account for Bill Payment)(?:\\s?\\.?\\:?\\s?)([A-z]{0,1}[0-9]{8,15})(?:.|\\s|\\()", Pattern.CASE_INSENSITIVE);
                    Matcher m2 = fetchPostpaidAccount.matcher(originalMessage);
                    if (m2.find()) {
                        return m2.group(1);
                    }
                    break;
                }
            case Constants.GAS:
                Pattern fetchGasAccount = Pattern.compile("(?:[^Ref |^Reference ]number|for a\\/c|for account|Limit-|KNO|\\) -| -|\\)| \\(|for|Consumer No|bill-|account no|CA No| account|Limited|[^Ref |^Reference ]No|Mumbai|Gas|Ltd|Customer ID|Connection No|with|against)(?:\\s?\\-?\\.?\\:?\\s?)([A-z]{0,2}[0-9]{0,5}[A-z]{0,4}[0-9]{4,15})(.|\\s)", Pattern.CASE_INSENSITIVE);
                Matcher m3 = fetchGasAccount.matcher(originalMessage);
                if (m3.find()) {
                    return m3.group(1);
                }
                break;

            case Constants.BROADBAND:
                Pattern fetchBroadbandAccount = Pattern.compile("(?:for|Broadband|Customer|Consumer ID|Consumer No|Sub Code|USER ID|Username|Subscriber|account no|[^bank ]account|\\(|relationship number|\\(|\\)|with|against|Ltd)(?:\\s?\\-?\\.?\\:?\\s?)([A-z]{0,15}-?[0-9]{3,15})(?:.|\\s\\))", Pattern.CASE_INSENSITIVE);
                Matcher m5 = fetchBroadbandAccount.matcher(originalMessage);
                if (m5.find()) {
                    return m5.group(1);
                }
                break;

            case Constants.WATER:
                Pattern fetchWaterAccount = Pattern.compile("(?:[^Ref |^Reference ]number|for a\\/c|for account|Limit-|KNO|\\) -| -|\\)| \\(|Bill for|for|Consumer No|bill-|account no|CA No| account|Limited|RR No|Board|Ltd|Customer ID|[^Ref |^Reference |Chq]no|number|with)(?:\\s?\\-?\\.?\\:?\\s?)([A-z]{0,6}@?-?[0-9]{5,15})(.|\\s)", Pattern.CASE_INSENSITIVE);
                Matcher m6 = fetchWaterAccount.matcher(originalMessage);
                if (m6.find()) {
                    return m6.group(1);
                }
                //   case "EDUCATION":
                //       Pattern.compile("(?:Dear)(\\s?[A-z]{0,10}[0-9]{2,4}(?:\\/|-)?[0-9]{1,4})(\\s|.)", Pattern.CASE_INSENSITIVE);

        }
        return "";
    }

    public String fetchServiceName(String originalMessage) {


        switch (getCategory(originalMessage).get(0)) {
            case Constants.ELECTRICITY:

                Pattern electricityServiceMatcher = Pattern.compile("garuda power private limited|eastern power distribution|jvvnl|adani electricity|ajmer vidyut vitran nigam limited|apcpdcl|apdcl|apspdc|apspdcl|assam power distribution|avvnl|b.e.s.t mumbai electricity|bangalore electricity|bescom|bharatpur electricity services ltd. (besl)|bihar power distribution|bijli vitran nigam|bikaner electricity supply|bpcl|bses|bses rajdhan|bses rajdhani|bses yamuna|calcutta electric supply corporation (cesc)|cesc electricity|cesc kolkata|cess sircilla|chamundeshwari|chamundeshwari electricity|chattisgarh state power|chhattisgarh state electricity|chhattisgarh state power distribution|coll a c west bengal state electricity distribution company limited|cspdcl|dakshin haryana bijli vitran nigam|dhbvn|eastern power distribution company limited|electricity board|electricity dpt puduch|goa electricity department|goaelectricitydepartment|government of puducherry electricity department|gulbarga|himachal pradesh state electricity board|hpcl|hubli electricity|jaipur vidyut nigam|jammu and kashmir power development department|jbvnl|jdvvnl|kanpur electricity supply company|kedl|kerala state electricity board|kesco|kota electricity distribution limited (kedl)|maharashtra state electricity|mangalore electricity supply|meghalaya power dist corp ltd|mp paschim kshetra electricity|mp poorv kshetra electricity|mpez|mppkvvcl|msedcl|nbpdcl|ndmc electricity|nesco, odisha|new delhi municipal council electricity|north bihar power|power and electricity department - mizoram|pspcl|sbpdcl|south bihar power|southco|southco, odisha|southern power|st electricity distribu|tamil nadu electricity|tata power|tneb|torrent power|tp ajmer distribution ltd|tp central odisha distribution|tpcodl|tpddl|tpnodl|tripura electricity|trivandrum-power house rd|tsnpdcl|uhbvn|upcl|uppcl|uppcl-rural|uppcl-urban electricity|uttar haryana bijli|uttar pradesh power corp ltd|wbsedcl|wesco|west bengal state electricity|JHARKHAND BIJLI VITRAN|Dakshin Haryana Bijli Vitran", Pattern.CASE_INSENSITIVE);
                Matcher m1 = electricityServiceMatcher.matcher(originalMessage);
                if (m1.find()) {
                    return m1.group();
                }
                break;

            case Constants.WATER:
                Pattern waterServiceMatcher = Pattern.compile("Bangalore Water Supply and Sewerage Board |Hyderabad Metropolitan Water Supply and Sewerage Board|Kerala Water Authority|KWA|MCGM |MCGM Water Department|Municipal Corporation of Gurugram|MCG|New Delhi Municipal Council|NDMC|PHED|Umesh water supply|Uttarakhand Jal Sansthan |WATER SUPPLY MUMBAI", Pattern.CASE_INSENSITIVE);
                Matcher m2 = waterServiceMatcher.matcher(originalMessage);
                if (m2.find()) {
                    return m2.group();
                }
                break;


            case Constants.GAS:

                Pattern gasServiceMatcher = Pattern.compile("ADANI GAS|Adani Gas Ltd|Adani Total Gas Limited|Bhagyanagar Gas|Central U.P. Gas|Central U.P. Gas Limited|CHAROTAR GAS - GUJ|Charotar Gas Sahakari Mandali|Charotar Gas Sahakari Mandali Ltd|Gail Gas Limited |Gail Gas Limited Bill |Gujarat Gas Limited|Gujarat Gas Ltd Erstw|Indian Oil-Adani Gas Private|Indian Oil-Adani Gas Private Limited|Indraprastha Gas|Mahanagar Gas|Mahanagar Gas ltd|Maharashtra Natural Gas Limited (MNGL) |RAJASTHAN STATE GAS LI|Sabarmati Gas|Vadodara Gas|Vadodara Gas Limited", Pattern.CASE_INSENSITIVE);
                Matcher m3 = gasServiceMatcher.matcher(originalMessage);
                if (m3.find()) {
                    return m3.group();
                }
                break;


            case Constants.BROADBAND:
                Pattern broadBandMatcher = Pattern.compile("ACT BroadBand |Airtel Broadband |AirtelBroadband |Alliance Broadband |Apex Broadband Network Pvt.Ltd. |Asianet Broadband |Connect Broadband |DEN Broadband |Excell Broadband |GTPL KCBPL Broadband Pvt Ltd |Hathway Broadband |JETWAY BROADBAND INDIA |Kerala Vision Broadband Pvt Ltd |Netplus Broadband |Timbl Broadband", Pattern.CASE_INSENSITIVE);
                Matcher m5 = broadBandMatcher.matcher(originalMessage);
                if (m5.find()) {
                    return m5.group();
                }
                break;

            case Constants.TELECOM:

                Pattern mobPostMatcher = Pattern.compile("Airtel|BSNL|Jio|MTNL| Vi ", Pattern.CASE_INSENSITIVE);
                Matcher m4 = mobPostMatcher.matcher(originalMessage);
                if (m4.find()) {
                    return m4.group();
                }
        }
        return "";

    }


    public HashMap<String, String> setUtilityTokens(String originalMessage, SmsInfo smsInfo) {

        HashMap<String, String> utilityMap = new HashMap<>();

        String serviceName = fetchServiceName(originalMessage);
        utilityMap.put(Constants.SERVICE_NAME, serviceName);

        String utilityAccount = fetchUtilityAccount(originalMessage);
        log.info("Account : {} for user : {}", utilityAccount, smsInfo.getImsId());
        utilityMap.put(Constants.ACCOUNT_NUMBER, utilityAccount);

        String yearToken="";
        Pattern year = Pattern.compile("(?:[A-Z]{2,4}[\\,\\'\\-])([0-9]{2,4})",Pattern.CASE_INSENSITIVE);
        Matcher yearMatcher = year.matcher(originalMessage);

        log.info("year token before sanitization : {} for userId : {}", yearToken, smsInfo.getImsId());

        String dueDateToken = fetchPaymentOrDueDate(originalMessage);
        String billDue = fetchPaidOrDueAmount(originalMessage);
        Pattern dueDate = Pattern.compile("(due|reminder|last day|scheduled|has been generated|Please pay atleast 3 days before)", Pattern.CASE_INSENSITIVE);
        Matcher dueDateMatcher = dueDate.matcher(originalMessage);
        if (dueDateMatcher.find()) {
            log.info("date token before sanitization : {} for userId : {}", dueDateToken, smsInfo.getImsId());
            String dueDateTokenVal=modelRunnerService.dateFormatter(dueDateToken);

                if (StringUtils.isEmpty(dueDateTokenVal))
                {
                    if (yearMatcher.find())
                    {
                        utilityMap.put(Constants.DUE_DATE, modelRunnerService.yearFormatter(dueDateToken, yearMatcher.group()));
                    }
                    else
                    {     utilityMap.put(Constants.DUE_DATE, "");
                    }
                }else
                {
                    utilityMap.put(Constants.DUE_DATE, dueDateTokenVal);
                }


            if (utilityMap.get(Constants.DUE_DATE).isEmpty()) {
                if (originalMessage.contains("today")) {
                    utilityMap.put(Constants.DUE_DATE, new SimpleDateFormat("yyyy-MM-dd").format(smsInfo.getMsgTime()));
                } else if (originalMessage.contains("tomorrow")) {
                    //Incrementing date by one day
                    utilityMap.put(Constants.DUE_DATE, new SimpleDateFormat("yyyy-MM-dd").format(smsInfo.getMsgTime() + 86400000));
                }
            }
            log.info("date token after sanitization : {} for userId : {}", modelRunnerService.dateFormatter(dueDateToken), smsInfo.getImsId());
            utilityMap.put(Constants.BILL_AMOUNT, sanitizeAmount(billDue));
            log.info("Amount token before sanitization : {} for userId : {}", billDue, smsInfo.getImsId());
            log.info("amount token after sanitization : {} for userId : {}", sanitizeAmount(billDue), smsInfo.getImsId());
            return utilityMap;
        }

        String paymentDate = fetchPaymentOrDueDate(originalMessage);
        String billAmount = fetchPaidOrDueAmount(originalMessage);
        Pattern payDate = Pattern.compile("(paid|received|successful bill processesd|successful|Thank you for payment|Thank you for the bill payment|debited)", Pattern.CASE_INSENSITIVE);
        Matcher paymentDateMatcher = payDate.matcher(originalMessage);
        if (paymentDateMatcher.find()) {
            log.info("date token before sanitization : {} for userId : {}", paymentDate, smsInfo.getImsId());
            utilityMap.put(Constants.PAYMENT_DATE, modelRunnerService.dateFormatter(paymentDate));
            log.info("date token after sanitization : {} for userId : {}", modelRunnerService.dateFormatter(paymentDate), smsInfo.getImsId());
            utilityMap.put(Constants.BILL_PAID, sanitizeAmount(billAmount));
            log.info("Amount token before sanitization : {} for userId : {}", billAmount, smsInfo.getImsId());
            log.info("amount token after sanitization : {} for userId : {}", sanitizeAmount(billAmount), smsInfo.getImsId());
            return utilityMap;
        }

        return utilityMap;
    }

    public String sanitizeAmount(String billAmount) {
        Pattern amount = Pattern.compile("([0-9,]+([.]?)([0-9,]?)+)");
        Matcher m = amount.matcher(billAmount);
        if (m.find()) {
            return m.group().replaceAll(",","");
        }
        return "";
    }
}
