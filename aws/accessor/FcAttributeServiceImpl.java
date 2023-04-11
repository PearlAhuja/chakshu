package com.freecharge.smsprofilerservice.aws.accessor;


import com.fc.attribute.client.FCAttributeClientApi;
import com.fc.attribute.response.FCTransactionalData;
import com.fc.attribute.response.FCVariantResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Optional;


@Slf4j
@Service
@RequiredArgsConstructor
public class FcAttributeServiceImpl {

    @Autowired
    private final FCAttributeClientApi fcAttributeClientApi;


    public void saveIncomeInFCAttributeDB(String imsId, String income, String mobileNumber) {
        try {
            Optional.ofNullable(fcAttributeClientApi.saveIncomeInFCAttribute(imsId, income, mobileNumber))
                    .map(result -> result.equalsIgnoreCase("Success"))
                    .orElseThrow(() -> new RuntimeException("Failed to save income"));
        } catch (Exception e) {
            log.info("Failed to save Income for imsId : {} , income : {} and mobileNumber : {}", imsId, income, mobileNumber);
        }
    }

    public FCTransactionalData fetchDataFromFcAttributes(String mobileNumber) {
        try {
            return Optional.ofNullable(fcAttributeClientApi.getFCAttributes(mobileNumber))
                    .map(FCVariantResponse::getFcTransactionalData)
                    .orElseGet(() -> null);
        } catch (Exception e) {
            log.info("Failed to fetch data from fc-attributes for mobileNumber :: {}", mobileNumber);
        }
        return null;
    }
}
