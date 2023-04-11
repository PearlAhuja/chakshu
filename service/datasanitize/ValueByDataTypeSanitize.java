package com.freecharge.smsprofilerservice.service.datasanitize;

/**
 * @author parag.vyas
 */
public interface ValueByDataTypeSanitize {
    String sanitizeData(String valueToSanitize, String dataType);
}
