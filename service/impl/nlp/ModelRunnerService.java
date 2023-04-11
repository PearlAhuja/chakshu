package com.freecharge.smsprofilerservice.service.impl.nlp;

import com.freecharge.smsprofilerservice.dao.mysql.service.ModelServiceDao;
import lombok.extern.slf4j.Slf4j;
import opennlp.tools.namefind.TokenNameFinder;
import opennlp.tools.util.Span;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


@Service
@Slf4j
public class ModelRunnerService {

    @Autowired
    private CacheManager cacheManager;
    @Autowired
    private ModelServiceDao modelServiceDao;


    /**
     * Get NLP model by modelId
     * Pass stringToTokenize string to nlp
     * nlp return those token which were trained at time of training.
     *
     * @param stringToTokenize
     * @param modelId
     * @return
     */


    public Map<String, String> getTokens(String stringToTokenize, Integer modelId, String hashCode) {
        TokenNameFinder nameFinder = modelServiceDao.getTokenByModelId(modelId);
        String[] tokens = stringToTokenize.split(" ");
        Span[] names = nameFinder.find(tokens);
        Map<String, String> response = getTokensByModel(nameFinder, stringToTokenize);
        return response;
    }

    public Map<String, String> getTokensByModel(TokenNameFinder nameFinder, String stringToTokenize) {
        Map<String, String> resultMap = new HashMap<>();
        if (nameFinder == null) {
            return resultMap;
        }
        String[] tokens = stringToTokenize.split(" ");
        Span[] names = nameFinder.find(tokens);
        for (Span span : names)
        {
            StringBuilder stringBuilder = new StringBuilder();
            for (String str : Arrays.copyOfRange(tokens, span.getStart(), span.getEnd())) {
                stringBuilder.append(str).append(" ");
            }
                resultMap.put(span.getType().trim(), stringBuilder.toString().trim());
        }
        log.info("TOKEN MAP FINAL : {}", resultMap);
        return resultMap;

    }

    public  String sanitizeNumber(String accountNumber) {
        Pattern accountNum = Pattern.compile("(?:\\d+[xX.*N]*\\d+|[xX.*N]*\\d+)");
        Matcher m = accountNum.matcher(accountNumber);
        if (m.find()) {
            return m.group();
        }
        return "";

    }

    public static boolean isNumeric(String str) {
        return str != null && str.matches("[0-9.]+");
    }

   public static String yearFormatter(String unfilteredDate,String unfilteredYear)
     {
         HashMap<String, String> filterYear = new HashMap<>();
         filterYear.put("jan", "01");
         filterYear.put("feb", "02");
         filterYear.put("mar", "03");
         filterYear.put("apr", "04");
         filterYear.put("may", "05");
         filterYear.put("jun", "06");
         filterYear.put("jul", "07");
         filterYear.put("aug", "08");
         filterYear.put("sep", "09");
         filterYear.put("oct", "10");
         filterYear.put("nov", "11");
         filterYear.put("dec", "12");
         String consolidatedDate = "";
         log.info("unfiltered date in yearFormatter : {} ", unfilteredDate);
         log.info("unfiltered year in yearFormatter : {} ", unfilteredYear);
         Pattern datePattern = Pattern.compile("(\\d{1,4})\\s*(?:th)?(?:rd)?(?:st)?(?:nd)?(-|:|/)?\\s?(\\d{2}|\\w{3})(-|:|,|/)?\\s?(\\d{0,4})");
         Matcher m1 = datePattern.matcher(unfilteredDate);

         Pattern yearPattern = Pattern.compile("[0-9.-]+",Pattern.CASE_INSENSITIVE);
         Matcher m2 = yearPattern.matcher(unfilteredYear);
         if (m1.find() && m2.find()) {
             String day = m1.group(1);
             String month = m1.group(3);
             if (!isNumeric(month)) {
                 month = filterYear.get(month.toLowerCase());
                 log.info("unfiltered month in yearFormatter : {} ", month);
             }

             String year = m2.group();

             if (year.length() == 2) {
                 year = "20" + m2.group();
             }
             if(year.length() == 4) {
                 consolidatedDate = year + "-" + month + "-" + day;
             }
         }
         log.info("consolidated date  : {} ", consolidatedDate);
         return consolidatedDate;
      }

    public static String dateFormatter(String str) {

        HashMap<String, String> map = new HashMap<>();
        map.put("jan", "01");
        map.put("feb", "02");
        map.put("mar", "03");
        map.put("apr", "04");
        map.put("may", "05");
        map.put("jun", "06");
        map.put("jul", "07");
        map.put("aug", "08");
        map.put("sep", "09");
        map.put("oct", "10");
        map.put("nov", "11");
        map.put("dec", "12");

        String finalDate="";
        Pattern pattern = Pattern.compile("(\\d{1,4})\\s*(?:th)?(?:rd)?(?:st)?(?:nd)?(-|:|/)?\\s?(\\d{2}|\\w{3})(-|:|,|/)?\\s?(\\d{2,4})");
        Matcher m = pattern.matcher(str);

        if (m.find()) {
            String day = m.group(1);
            String month = m.group(3);
            String year = m.group(5);
            if (day.length() == 4) {
                String temp = day;
                day = year;
                year = temp;
            }
            if (year.length() == 2) {
                year = "20" + year;
            }
            if (isNumeric(month) && Integer.parseInt(month) > 12) {
                String temp1 = day;
                day = month;
                month = temp1;
            }
            if (!isNumeric(month)) {
                month = map.get(month.toLowerCase());
            }

            if(day.length()==1)
            {
               day="0"+day;
            }

            log.info("DAY :   {} +  MONTH :  {}  YEAR :  + {}", day, month, year);
            finalDate = year + "-" + month + "-" + day;
            log.info("finalDate : {}", finalDate);
        }
        return finalDate;
    }


    public String pfMonthFormatter(String str)
    {
        String pfMonth = str;
        if(StringUtils.isNotEmpty(str) && str.length() > 1 && Integer.valueOf(str.substring(0,2)) == 20){
            pfMonth = str.substring(str.length() - 2) + str.substring(0,4);
        }
        return pfMonth;
    }

}
