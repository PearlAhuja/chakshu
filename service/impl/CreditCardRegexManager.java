package com.freecharge.smsprofilerservice.service.impl;


import com.freecharge.smsprofilerservice.constant.Constants;
import com.freecharge.smsprofilerservice.model.SmsInfo;
import com.freecharge.smsprofilerservice.service.impl.nlp.ModelRunnerService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


@Slf4j
@Component
public class CreditCardRegexManager {
    @Autowired
    ModelRunnerService modelRunnerService;

    @Autowired
    RegexTokenizerManager regexTokenizerManager;


    public Map<String, String> setCreditCardTokens(String originalMessage, SmsInfo smsInfo) {
        log.info("Credit card domain for user id {}", smsInfo.getImsId());
        HashMap<String, String> creditCardMap = new HashMap<>();
        String cardNumber = fetchCardNumber(originalMessage);
        creditCardMap.put(Constants.CARD_NUMBER, regexTokenizerManager.sanitizeAccountNumber(cardNumber));
        log.info("Card number {} for userid {}",regexTokenizerManager.sanitizeAccountNumber(cardNumber) , smsInfo.getImsId());

        String accountNumber = fetchAccountNumber(originalMessage);
        creditCardMap.put(Constants.ACCOUNT_NUMBER, regexTokenizerManager.sanitizeAccountNumber(accountNumber));
        log.info("ACCOUNTNUMBER {} for userid {}", regexTokenizerManager.sanitizeAccountNumber(accountNumber), smsInfo.getImsId());

        String availableCreditLimit = fetchAvailableCreditLimit((originalMessage));
        creditCardMap.put(Constants.AVAILABLE_CREDITLIMIT,sanitizeBalance(availableCreditLimit));
        log.info("availableCreditLimit {} for userid {}", sanitizeBalance(availableCreditLimit), smsInfo.getImsId());

        String balance = fetchBalance((originalMessage));
        creditCardMap.put(Constants.BALANCE, sanitizeBalance(balance));
        log.info("balance {} for userid {}",sanitizeBalance(balance),smsInfo.getImsId());

        String currentOutstandingBalance = fetchCurrentOutstanding(originalMessage);
        creditCardMap.put(Constants.CURRENT_OUTSTANDING, sanitizeBalance(currentOutstandingBalance));
        log.info("currentOutstandingBalance {} for userid {}", sanitizeBalance(currentOutstandingBalance), smsInfo.getImsId());

        String debit = fetchAmount(originalMessage);
        String date = fetchDate(originalMessage);

        Pattern transaction = Pattern.compile("([^alreadyif] paid|withdraw|spent|debit|Transaction [A-z\\s0-9\\.]{0,15} has been made)", Pattern.CASE_INSENSITIVE);
        Matcher transactionMatcher = transaction.matcher(originalMessage);
        if (transactionMatcher.find()) {
            creditCardMap.put(Constants.DATE, modelRunnerService.dateFormatter(date));
            log.info("date {} for userid {}", modelRunnerService.dateFormatter(date), smsInfo.getImsId());
            creditCardMap.put(Constants.DEBIT,sanitizeBalance(debit));
            log.info("debit {} for userid {}", sanitizeBalance(debit), smsInfo.getImsId());
            return creditCardMap;

        }

        String credit = fetchAmount(originalMessage);
        Pattern paymentReceive = Pattern.compile("credited|reversal|credited|reversal|reversed|received|successful bill processesd|Thank you for payment|Thank you for the bill payment", Pattern.CASE_INSENSITIVE);
        Matcher creditMatcher = paymentReceive.matcher(originalMessage);
        if (creditMatcher.find()) {
            creditCardMap.put(Constants.DATE, modelRunnerService.dateFormatter(date));
            log.info("date {} for userid {}", modelRunnerService.dateFormatter(date), smsInfo.getImsId());
            creditCardMap.put(Constants.CREDIT, sanitizeBalance(credit));
            log.info("credit {} for userid {}", sanitizeBalance(credit), smsInfo.getImsId());
            return creditCardMap;
        }

        String minimumDue = fetchMinAmountDue(originalMessage);
        String totalAmountDue = fetchTotalAmountDue(originalMessage);

        Pattern billReminder = Pattern.compile(" (last day|due | minimum | total | min| reminder|scheduled|has been generated|Statement)", Pattern.CASE_INSENSITIVE);
        Matcher billReminderMatcher = billReminder.matcher(originalMessage);
        if (billReminderMatcher.find()) {
            creditCardMap.put(Constants.DATE, modelRunnerService.dateFormatter(date));
            if (creditCardMap.get(Constants.DATE).isEmpty()) {
                if (originalMessage.contains("today")) {
                    creditCardMap.put(Constants.DATE, new SimpleDateFormat("yyyy-MM-dd").format(smsInfo.getMsgTime()));
                } else if (originalMessage.contains("tomorrow")) {
                    creditCardMap.put(Constants.DATE, new SimpleDateFormat("yyyy-MM-dd").format(smsInfo.getMsgTime() + 86400000));
                }
            }
            log.info("date {} for userid {}", date, smsInfo.getImsId());
            creditCardMap.put(Constants.MINIMUM_AMOUNT_DUE, sanitizeBalance(minimumDue));
            log.info("MINIMUMAMOUNTDUE {} for userid {}", sanitizeBalance(minimumDue), smsInfo.getImsId());
            creditCardMap.put(Constants.TOTAL_AMOUNT_DUE,sanitizeBalance(totalAmountDue));
            log.info("TOTALAMOUNTDUE {} for userid {}", sanitizeBalance(totalAmountDue), smsInfo.getImsId());
            return creditCardMap;
        }

        return creditCardMap;
    }

    public List<String> getCategory(String originalMessage) {

        List<String> creditCardList = new ArrayList<>();

        Pattern dumpPattern = Pattern.compile("(renewal|declined|failed|Incorrect PIN|not approved| Gift Voucher|kyc|Card will be blocked|Gift your loved|has been delivered|dispatched)", Pattern.CASE_INSENSITIVE);
        Matcher dumpMatcher = dumpPattern.matcher(originalMessage);
        if (dumpMatcher.find()) {
            creditCardList.add(Constants.OTHER);
            creditCardList.add(Constants.OTHER);
        }

        Pattern credit = Pattern.compile("(credited|reversed|received|successful bill processesd|Thank you for payment|Thank you for the bill payment|refund|Avl Limit)", Pattern.CASE_INSENSITIVE);
        Matcher creditMatcher = credit.matcher(originalMessage);
        if (creditMatcher.find()) {
            creditCardList.add(Constants.PAYMENT_RECEIVED);
            creditCardList.add(Constants.PAYMENT_RECEIVED);
            log.info("credit card message for PAYMENT RECEIVED");

            return creditCardList;
        }

        Pattern expense = Pattern.compile("(spent|debit|transaction [A-z\\.\\,\\s0-9]{0,20} has)", Pattern.CASE_INSENSITIVE);
        Matcher expenseMatcher = expense.matcher(originalMessage);
        if (expenseMatcher.find()) {
            creditCardList.add(Constants.EXPENSE);
            creditCardList.add(Constants.EXPENSE);
            log.info("credit card message for EXPENSE");
            return creditCardList;
        }

        Pattern billReminder = Pattern.compile("(last day| minimum | total | min| reminder|scheduled|has been generated|due|pay your outstanding| overdue)", Pattern.CASE_INSENSITIVE);
        Matcher billReminderMatcher = billReminder.matcher(originalMessage);
        if (billReminderMatcher.find()) {
            creditCardList.add(Constants.BILL_REMINDER);
            creditCardList.add(Constants.BILL_REMINDER);
            log.info("credit card message for BILL REMINDER");
            return creditCardList;
        }

        Pattern withdraw = Pattern.compile("(withdraw)");
        Matcher withdrawMatcher = withdraw.matcher(originalMessage);
        if (withdrawMatcher.find())
        {
            creditCardList.add(Constants.EXPENSE);
            creditCardList.add(Constants.WITHDRAWAL);
        }

        creditCardList.add(Constants.OTHER);
        creditCardList.add(Constants.OTHER);
        return creditCardList;
    }

    public static String fetchCardNumber(String originalMessage) {
        Pattern fetchCardNumber = Pattern.compile("(?:Credit Card|Card ending|ending with|Card Account|card no|card number|card)\\s?(?:no)?\\(?\\:?\\.?\\s?(([\\dxX*N]*\\d+))", Pattern.CASE_INSENSITIVE);
        Matcher m = fetchCardNumber.matcher(originalMessage);
        if (m.find()) {
            return m.group(0);
        }
        return "";
    }

    public static String fetchAvailableCreditLimit(String originalMessage) {
        Pattern fetchAvailableCreditLimit = Pattern.compile("(?:Avbl limit|Avl Lmt|Avl limit|available limit|Avail Limit|Available credit limit|Avl credit limit|Avlb credit limit|Limit available)\\s?(?:now)?(?:is)?\\=?\\s?\\.?\\:?\\-?\\(?\\s?(?:Rs\\.?|INR|amount|Bal)?\\)?\\.?\\-?\\:?(?:[.,])?\\s*([\\d\\,\\.]+)", Pattern.CASE_INSENSITIVE);
        Matcher m = fetchAvailableCreditLimit.matcher(originalMessage);
        if (m.find()) {
            return m.group(0);
        }
        return "";
    }

    public static String fetchCurrentOutstanding(String originalMessage) {
        Pattern fetchCurrentOutstanding = Pattern.compile("(?:Current outstanding|Curr O\\/s|outstanding of)\\s?\\s?(?:is)?\\.?\\:?\\-?\\(?\\s?(?:Rs\\.?|INR|amount|Bal)?\\)?\\.?\\-?\\:?(?:[.,])?\\s*([\\d\\,\\.]+)", Pattern.CASE_INSENSITIVE);
        Matcher m = fetchCurrentOutstanding.matcher(originalMessage);
        if (m.find()) {
            return m.group(0);
        }
        return "";
    }

    public static String fetchMinAmountDue(String originalMessage) {
        Pattern fetchMinAmountDue = Pattern.compile("(?:Minimum Amount Due|minimum payment of|Minimum Amount Due[A-z0-9\\s]* is|Min\\.? amount due|minimum amount|Minimum due amount|Min\\.? due amt|Min\\.? Amt due|Min Due|Minimum Due|[^Total|Dear Customer, your] payment|Payment of Rs|minimum payment|Minimum Amt due|Min\\.? amt|To PAY)\\s?(?:of)?(?:is)?\\s?\\.?\\:?\\-?\\(?\\s?(?:Rs\\.?|INR|amount|Bal)?\\)?\\.?\\-?\\:?(?:[.,])?\\s*([\\d\\,\\.]+)", Pattern.CASE_INSENSITIVE);
        Matcher m = fetchMinAmountDue.matcher(originalMessage);
        if (m.find()) {
            return m.group(0);
        }
        Pattern fetchMinAmountDue2 = Pattern.compile("(?:Payment of)\\s?\\.?\\:?\\-?\\(?\\s?(?:Rs\\.?|INR|amount)?\\)?\\.?\\-?\\:?(?:[.,])?\\s*([\\d\\,\\.]+)\\s(?:is due)", Pattern.CASE_INSENSITIVE);
        Matcher m2 = fetchMinAmountDue2.matcher(originalMessage);
        if (m2.find()) {
            return m2.group(0);
        }
        return "";
    }

    public static String fetchTotalAmountDue(String originalMessage) {
        Pattern fetchTotalAmountDue = Pattern.compile("(?:[^Mmin\\.] amt|[^Mmin\\.] Due|, outstanding|\\,\\s|total Due|total amount due|Total Amount [over]*Due[A-z0-9\\s]* is |Current bill [A-z0-9\\s]*|Total amount|Total Amt Due|Total Outstanding|Total Due|total amount|Total amount|Total due amt|statement|total payment|total outstanding due|Total due|total outstanding)\\s?(?:of)?(?:is)?\\s?\\.?\\:?\\-?\\(?\\s?(?:Rs\\.?|INR|amount|Bal)?\\)?\\.?\\-?\\:?(?:[.,])?\\s*([\\d\\,\\.]+)", Pattern.CASE_INSENSITIVE);
        Matcher m = fetchTotalAmountDue.matcher(originalMessage);
        if (m.find()) {
            return m.group(0);
        }
        Pattern fetchTotalAmountDue2= Pattern.compile("(?:\\s?\\.?\\:?\\-?\\(?\\s?(?:Rs\\.?|INR|amount|Bal)?\\)?\\.?\\-?\\:?(?:[.,])?\\s*([\\d\\,\\.]+))\\s?(?:of)?(?:is)?\\s?\\.?(?:overdue)",Pattern.CASE_INSENSITIVE);
        Matcher m2 = fetchTotalAmountDue2.matcher(originalMessage);
        if(m2.find()){
            return m2.group(0);
        }
        return "";
    }

    private String fetchBalance(String originalMessage) {
        Pattern getBalance = Pattern.compile("(?:Avlb Bal|Avail bal|Avl bal|available bal|available balance)\\s?(?:now)?(?:is)?\\s?\\.?\\:?\\-?\\(?\\s?(?:Rs\\.?|INR|amount)?\\)?\\.?\\-?\\:?(?:[.,])?\\s*([\\d\\,\\.]+)", Pattern.CASE_INSENSITIVE);
        Matcher balanceMatcher = getBalance.matcher(originalMessage);
        if (balanceMatcher.find()) {
            return balanceMatcher.group();
        }
        return "";
    }

    private String fetchAmount(String originalMessage) {
        Pattern getAmount = Pattern.compile("(?:Rs\\.?|INR|amount|Bal):?([.,])?\\s*\\d+(?:[.,]\\d+)*", Pattern.CASE_INSENSITIVE);
        Matcher amountMatcher = getAmount.matcher(originalMessage);
        if (amountMatcher.find()) {
            return amountMatcher.group();
        }
        return "";
    }

    private String fetchDate(String originalMessage) {
        Pattern getDate = Pattern.compile("(?:[\\.\\,\\s]on|dated|by|due date|before|Date|Card no\\.? [xX0-9]+ [INR]+ [0-9]+|(?:Card|A\\/c) no\\.? [xX0-9]+ [^INR])\\s?\\:?\\s?([0-9]{1,4}(-|\\/)?\\s*[a-zA-Z0-9]{1,3}(-|\\/)?\\s*[A-z0-9]{0,4}\\s?[0-9]{0,4})", Pattern.CASE_INSENSITIVE);
        Matcher dateMatcher = getDate.matcher(originalMessage);
        if (dateMatcher.find()) {
            return dateMatcher.group();
        }
        return "";
    }

    private String fetchAccountNumber(String originalMessage) {
        Pattern fetchAccountNumber = Pattern.compile("(?:a\\/c|A\\/c|account|ac|acc|acct|HDFC Bank|debited from)\\s*[:-]?\\s*(?:number|no|no\\.)?\\s*(?:ending with|opening with|opening on)?\\s*(\\d+[xX.*N]*\\d+|[xX.*N]*\\d+)", Pattern.CASE_INSENSITIVE);
        Matcher m = fetchAccountNumber.matcher(originalMessage);
        if (m.find()) {
            return m.group();
        }
        return "";
    }

    private String sanitizeBalance(String balance)
    {
        String finalBal = balance.replaceAll(",","");
        Pattern sanitizer = Pattern.compile("([0-9,]+([.]?)([0-9,]?)+)",Pattern.CASE_INSENSITIVE);
        Matcher m = sanitizer.matcher(finalBal);
        if(m.find()){
            return m.group();
        }
        return "";
    }

}
