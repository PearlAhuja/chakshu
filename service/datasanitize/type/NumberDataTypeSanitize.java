package com.freecharge.smsprofilerservice.service.datasanitize.type;

import com.freecharge.smsprofilerservice.constant.DataTypeEnum;
import com.freecharge.smsprofilerservice.service.datasanitize.ValueByDataTypeSanitize;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.utils.StringUtils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author parag.vyas
 */
@Service("NumberDataTypeSanitize")
public class NumberDataTypeSanitize implements ValueByDataTypeSanitize {
    private final Pattern pattern = Pattern.compile("([0-9]+([.]?)([0-9]?)+)");

    @Override
    public String sanitizeData(String valueToSanitize, String dataType) {
        if (StringUtils.isBlank(valueToSanitize)) {
            return "";
        }
        valueToSanitize = valueToSanitize.replace(",","");
        Matcher matcher = pattern.matcher(valueToSanitize);
        if (!matcher.find()) {
            return "";
        }
        if (dataType.equalsIgnoreCase(DataTypeEnum.INTEGER.name())) {
            return String.valueOf((int) Double.parseDouble(matcher.group()));
        }
        return matcher.group();
    }

    public static void main(String[] args) {
        String regex = "([0-9]+([.]?)([0-9]?)+)";
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher("291bcvdc/-");
        if (!matcher.find()) {
            return;
        }
        if ("integer".equalsIgnoreCase(DataTypeEnum.INTEGER.name())) {
            System.out.println((int) Long.parseLong(matcher.group()));
        }
        System.out.println(matcher.group());
    }
}