package com.freecharge.smsprofilerservice.service.impl;

import com.freecharge.smsprofilerservice.constant.Constants;
import lombok.extern.slf4j.Slf4j;
import opennlp.tools.parser.Cons;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


@Slf4j
@Service
public class RegexTokenizerManager {

    @Autowired
    private UtilityRegexManager utilityRegexManager;
    @Autowired
    private CreditCardRegexManager creditCardRegexManager;

    public List<String> getDomainsApplicableForRegex() {
        List<String> domainsApplicableForRegex = new ArrayList<>();
        domainsApplicableForRegex.add(Constants.IMPS);
        domainsApplicableForRegex.add(Constants.UPI);
        domainsApplicableForRegex.add(Constants.DEPOSIT);
        domainsApplicableForRegex.add(Constants.WALLET);
        domainsApplicableForRegex.add(Constants.DEBIT_CARD);
        domainsApplicableForRegex.add(Constants.UTILITY);
        domainsApplicableForRegex.add(Constants.NOT_CATEGORIZED);
        domainsApplicableForRegex.add(Constants.CREDIT_CARD);
        domainsApplicableForRegex.add(Constants.EPFO);
        return domainsApplicableForRegex;
    }

    public  String getAccountNumber(String originalMessage) {
        Pattern accountNumber = Pattern.compile("(?:a\\/c|A\\/c|account|ac|acc|acct|HDFC Bank|debited from)\\s*[:-]?\\s*(?:number|no|no\\.)?\\s*(ending with|opening with|opening on)?\\s*(?:\\d+[xX.*N]*\\d+|[xX.*N]*\\d+)", Pattern.CASE_INSENSITIVE);
        Pattern accountNumber2 = Pattern.compile("([xX*N]+\\d+)", Pattern.CASE_INSENSITIVE);
        Matcher m = accountNumber.matcher(originalMessage);
        Matcher m2 = accountNumber2.matcher(originalMessage);
        if (m.find()) {
            return m.group();
        }
        if (m2.find()) {
            return m2.group();
        }
        return "";
    }

    public static String getPFContribution(String originalMessage) {
        Pattern contribution = Pattern.compile("(?:Contribution[A-z\\s.,:]{0,9})([0-9,.，]+)", Pattern.CASE_INSENSITIVE);
        Matcher m = contribution.matcher(originalMessage);
        if (m.find()) {
            return m.group(0);
        }
        return "";
    }

    public static String getPFMonth(String originalMessage) {
        Pattern pfmonth = Pattern.compile("(month[ |-|:]?\\s)([0-9]{6})", Pattern.CASE_INSENSITIVE);
        Matcher m = pfmonth.matcher(originalMessage);
        if (m.find()) {
            return m.group(2);
        }
        return "";
    }

    public static List<String> sanitizeAmount(List<String> getAmounts) {
        ArrayList<String> list = new ArrayList<>();
        for (String x : getAmounts) {
            Pattern amount = Pattern.compile("([0-9,]+([.]?)([0-9,]?)+)");
            Matcher m = amount.matcher(x);
            if (m.find()) {
                list.add(m.group());
            }
        }
        return list;
    }

    public  String sanitizeAccountNumber(String accountNumber) {

        Pattern accountNum = Pattern.compile("(?:\\d+[xX.*N]*\\d+|[xX.*N]*\\d+)");
        Matcher m = accountNum.matcher(accountNumber);
        if (m.find()) {
            return m.group();
        }
        return "";

    }

    public String balanceFormatter(String balance) {
        log.info("BALANCE : {}",balance);
        String finalBal= sanitizeBalance(getBalance(balance));
        finalBal= finalBal.replaceAll(",","");
        log.info("BALANCE  BEFORE SANITY  : {}",getBalance(balance));
        log.info("BALANCE  AFTER SANITY  : {}",finalBal);
        return finalBal;
    }


    public  static String sanitizeBalance(String balance) {
        Pattern bal = Pattern.compile("([0-9,]+([.]?)([0-9,]?)+)");
        Matcher m = bal.matcher(balance);
        if (m.find()) {
            return m.group();
        }
        return "";
    }

    public static String getTransactionCategory(String originalMessage) {
        Pattern expense = Pattern.compile("debit|spent|paid|debited", Pattern.CASE_INSENSITIVE);
        Matcher mExpense = expense.matcher(originalMessage);
        if (mExpense.find()) {
            return Constants.DEBIT;
        }

        Pattern income = Pattern.compile("credit|received|receive|settle|settled|deposited|deposit|return|credited|returned|remitted|casback|reversed", Pattern.CASE_INSENSITIVE);
        Matcher mIncome = income.matcher(originalMessage);
        if (mIncome.find()) {
            return Constants.CREDIT;
        }
        return Constants.DEBIT;

    }

    static List<String> getAmounts(String originalMessage) {
        ArrayList<String>list=new ArrayList<>();
        list.add("CBINR"); list.add("SBINR");list.add("UBINR");
        for( String word: list) {
            originalMessage=originalMessage.replace(word, " ");
        }
        List<String> amountsList = new ArrayList<>();
        Pattern getAmount = Pattern.compile("(?:Rs\\.?|INR|amount|Bal):?([.,])?\\s*\\d+(?:[.,]\\d+)*", Pattern.CASE_INSENSITIVE);
        Matcher m2 = getAmount.matcher(originalMessage);
        while (m2.find()) {
            amountsList.add(m2.group());
        }
        return amountsList;
    }

    public static String getBalance(String originalMessage) {

        Pattern balance = Pattern.compile("((balance|BAL)(.)?(:)?(.)?(\\s)?(\\(incl. of chq in clg\\))?(is)?\\s?(-)?\\s*(Rs|INR)?[-.]?\\s*(.)?(,)?\\s?([0-9.,]+))", Pattern.CASE_INSENSITIVE);
        Pattern balance2=Pattern.compile("(Avlbl Amt)(\\s)?(:)?(\\s)?(is)?(\\s)?(-)?\\s*(Rs|INR)?[-.]?\\s*(.)?(,)?\\s?([0-9.,]+)",Pattern.CASE_INSENSITIVE);
        Pattern numeric = Pattern.compile("(Rs|INR)[-:.]?\\s*[,.-]?\\s?([0-9.,]+)", Pattern.CASE_INSENSITIVE);
        Matcher m = balance.matcher(originalMessage);
        if (m.find()) {
            return m.group();
        }
        Matcher newBal = balance2.matcher(originalMessage);
        if (newBal.find()) {
            return newBal.group();
        }

        String lowerTest = originalMessage.toLowerCase();
        int index = lowerTest.indexOf("bal");
        if (index == -1) {
            return "";
        }
        String str = originalMessage.substring(index, originalMessage.length() - 1);
        Matcher m1 = numeric.matcher(str);
        if (m1.find()) {
            return m1.group();
        }

        return "";


    }


    public String getDomain(String originalMessage, String sender) {

        Pattern failOrLoan = Pattern.compile("fail|failure|policy|DECLINED", Pattern.CASE_INSENSITIVE);
        Matcher m10 = failOrLoan.matcher(originalMessage);
        if (m10.find()) {
            return Constants.NOT_CATEGORIZED;
        }

        Pattern epfo = Pattern.compile("EPF", Pattern.CASE_INSENSITIVE);
        Matcher m2 = epfo.matcher(sender);
        if (m2.find()) {
            return Constants.EPFO;
        }

        Pattern utility = Pattern.compile("(fastag| water | gas |fees|.*mobile .* postpaid|.*mobile .* prepaid|[\\.\\,\\-\\s\\:]vi[\\.\\,\\-\\s\\:]|Airtel|jio|BSNL|dth|landline|broadband|wifi|rechargelpg|hydro|electricity|utility|bijli|TSSPDCL|MSEDCL|BESCOM|TNEB|JBVNL|AVVNL|BSES|JVVNL|HPCL|UHBVN|power|bpcl|UPPCL|nigam|energy|Vehicle No|JDVVNL|.*school .*fees|smarthub|.* college .* fees)", Pattern.CASE_INSENSITIVE);
        Matcher m11 = utility.matcher(originalMessage);
        if (m11.find()) {
            return Constants.UTILITY;
        }

        Pattern imps = Pattern.compile("IMPS|RTGS|NEFT|net banking|netbanking", Pattern.CASE_INSENSITIVE);
        Matcher m8 = imps.matcher(originalMessage);
        if (m8.find()) {
            return Constants.IMPS;
        }

        Pattern creditCard = Pattern.compile("(.* card.* minimum amount due.* )|(.* card.* Avl Lmt.*)|(.* card.* spent*)| credit card|(.* card.* available limit*)|(.* card.* min amt* )|(.* card.* outstanding* )|(.* card.* limit * )|(curr o/s)|(.* card.* avl limit*)", Pattern.CASE_INSENSITIVE);
        Matcher m3 = creditCard.matcher(originalMessage);
        if (m3.find()) {
            return Constants.CREDIT_CARD;
        }

        Pattern upi = Pattern.compile("UPI|@ybl|@icici|@ibl|@okbizaxis|@yesbankltd|@paytm|@axl|@fbpe|@indb|@KKBK|@yesbank|@kotak|@indus|@sbi|@airtel|@axisb|@AIRP|@yespay|@axisbank|@ibl|.ifsc.npci|@freecharge|@hdfc|idfcfirst", Pattern.CASE_INSENSITIVE);
        Matcher m7 = upi.matcher(originalMessage);
        if (m7.find()) {
            return Constants.UPI;
        }

        Pattern debitCard = Pattern.compile("(.* card.* credit.* )|(.* card.* debit.*)| debit card", Pattern.CASE_INSENSITIVE);
        Matcher m5 = debitCard.matcher(originalMessage);
        if (m5.find()) {
            return Constants.DEBIT_CARD;
        }

        Pattern wallet = Pattern.compile("wallet", Pattern.CASE_INSENSITIVE);
        Matcher m6 = wallet.matcher(originalMessage);
        if (m6.find()) {
            return Constants.WALLET;
        }

        Pattern deposit = Pattern.compile("credited|credit|debited|debit|crediting|debiting|transfer|tranfered|payment|received|refund|paid|bal", Pattern.CASE_INSENSITIVE);
        Matcher m9 = deposit.matcher(originalMessage);
        if (m9.find()) {
            return Constants.DEPOSIT;
        }

        return Constants.NOT_CATEGORIZED;
    }


    public  List<String> getCategory(String originalMessage,String domain)
    {

        String category = "";
        String subCategory = "";
        ArrayList<String> list = new ArrayList<>();

        Pattern expense = Pattern.compile("debit|spent|paid", Pattern.CASE_INSENSITIVE);
        Matcher mExpense = expense.matcher(originalMessage);

        Pattern income = Pattern.compile("credit|settled|received|receive|deposited|deposit|return|withdrawn|withdraw", Pattern.CASE_INSENSITIVE);
        Matcher mIncome = income.matcher(originalMessage);

        Pattern sal = Pattern.compile("(.* sal.* credit.* )|(.* credit.* sal.*)", Pattern.CASE_INSENSITIVE);
        Matcher m6 = sal.matcher(originalMessage);

//        String domain = getDomain(originalMessage, sender);
        switch(domain)

        {
            case Constants.EPFO:
            case Constants.WALLET:
            case Constants.DEBIT_CARD:
            case Constants.IMPS:
            case Constants.UPI:
            case Constants.NOT_CATEGORIZED:
                if (mExpense.find()) {
                    category = Constants.EXPENSE;
                    subCategory = Constants.OTHER;
                } else if (mIncome.find()) {
                    category = Constants.INCOME;
                    subCategory = Constants.INCOME;
                } else {
                    category = Constants.OTHER;
                    subCategory = Constants.OTHER;
                }
                break;
            case Constants.DEPOSIT:
                if (mExpense.find()) {
                    category = Constants.EXPENSE;
                    subCategory = Constants.OTHER;
                } else if (mIncome.find()) {
                    category = Constants.INCOME;
                    subCategory = Constants.INCOME;
                } else if (m6.find()) {
                    category = Constants.INCOME;
                    subCategory = Constants.INCOME;
                } else {
                    category = Constants.OTHER;
                    subCategory = Constants.OTHER;
                }
                break;

            case Constants.UTILITY:
                List<String> utilityList= utilityRegexManager.getCategory(originalMessage);
                category=utilityList.get(0);
                subCategory=utilityList.get(1);
                break;

            case Constants.CREDIT_CARD:
                List<String> creditCategory = creditCardRegexManager.getCategory(originalMessage);
                category = creditCategory.get(0);
                subCategory = creditCategory.get(1);
                break;


        }

        list.add(category);
        list.add(subCategory);
        log.info("OriginalMessage : {} transactionCategory :{} Subcategory :{} ",originalMessage,list.get(0),list.get(1));
        return list;
    }


    public String getDomainForPaidUtiliy(String originalMessage, String sender) {

        Pattern creditCard = Pattern.compile("(.* card.* minimum amount due.* )|(.* card.* Avl Lmt.*)|(.* card.* spent*)|credit card|(.* card.* available limit*)|(.* card.* min amt* )|(.* card.* outstanding* )|(.* card.* limit * )|(curr o/s)|(.* card.* avl limit*)", Pattern.CASE_INSENSITIVE);
        Matcher m3 = creditCard.matcher(originalMessage);
        if (m3.find()) {
            return "CREDIT CARD";
        }

        Pattern imps = Pattern.compile("IMPS|RTGS|NEFT|net banking|netbanking", Pattern.CASE_INSENSITIVE);
        Matcher m8 = imps.matcher(originalMessage);
        if (m8.find()) {
            return "IMPS";
        }


        Pattern upi = Pattern.compile("UPI|@ybl|@icici|@ibl|@okbizaxis|@yesbankltd|@paytm|@axl|@fbpe|@indb|@KKBK|@yesbank|@kotak|@indus|@sbi|@airtel|@axisb|@AIRP|@yespay|@axisbank|@ibl|.ifsc.npci|@freecharge|@hdfc|idfcfirst", Pattern.CASE_INSENSITIVE);
        Matcher m7 = upi.matcher(originalMessage);
        if (m7.find()) {
            return "UPI";
        }

        Pattern debitCard = Pattern.compile("(.* card.* credit.* )|(.* card.* debit.*)| debit card", Pattern.CASE_INSENSITIVE);
        Matcher m5 = debitCard.matcher(originalMessage);
        if (m5.find()) {
            return "DEBIT CARD";
        }


        Pattern wallet = Pattern.compile("wallet", Pattern.CASE_INSENSITIVE);
        Matcher m6 = wallet.matcher(originalMessage);
        if (m6.find()) {
            return "WALLET";
        }


        Pattern deposit = Pattern.compile("credited|credit|debited|debit|crediting|debiting|transfer|tranfered|payment|received|refund|paid|bal", Pattern.CASE_INSENSITIVE);
        Matcher m9 = deposit.matcher(originalMessage);
        if (m9.find()) {
            return "DEPOSIT";
        }

        return "NOT CATEGORIZED";
    }
}
