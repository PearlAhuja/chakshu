package com.freecharge.smsprofilerservice.aws.config;

import com.freecharge.vault.PropertiesConfig;
import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.Map;

@Data
@Component
public class BaseAwsResourceConfig {

    private String awsArn;
    private String awsArnName;

    @Autowired
    public BaseAwsResourceConfig(@Qualifier("applicationProperties") PropertiesConfig propertiesConfig) {
        final Map<String, Object> awsProperties = propertiesConfig.getProperties();
        this.awsArn = (String) awsProperties.get("aws.arn");
        this.awsArnName = (String) awsProperties.get("aws.arn.name");
    }

}