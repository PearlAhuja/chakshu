package com.freecharge.smsprofilerservice.service.datasanitize.type;

import com.freecharge.smsprofilerservice.service.datasanitize.ValueByDataTypeSanitize;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.utils.StringUtils;

/**
 * @author parag.vyas
 */
@Service("StringDataTypeSanitize")
public class StringDataTypeSanitize implements ValueByDataTypeSanitize {
    private final String pattern = "[^/a-zA-Z0-9\\s+]";

    @Override
    public String sanitizeData(String valueToSanitize, String dataType) {
        if (StringUtils.isBlank(valueToSanitize)) {
            return "";
        }
        return valueToSanitize.replaceAll(pattern, "");
    }

    public static void main(String[] args) {
        System.out.println("bill243437388".replaceAll("[^/a-zA-Z0-9\\s+]", ""));
    }
}