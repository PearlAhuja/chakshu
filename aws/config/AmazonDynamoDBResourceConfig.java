package com.freecharge.smsprofilerservice.aws.config;

import com.freecharge.vault.PropertiesConfig;
import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.Map;


@Data
@Component
public class AmazonDynamoDBResourceConfig {
    private String dynamoDbRegion;
    private String dynamoDbEndpoint;
    private boolean pickCredentialsFromPropertiesFile;
    private String awsArn;
    private String awsArnName;

    @Autowired
    public AmazonDynamoDBResourceConfig(@Qualifier("applicationProperties") PropertiesConfig propertiesConfig) {
        final Map<String, Object> awsProperties = propertiesConfig.getProperties();
        this.pickCredentialsFromPropertiesFile = (Boolean) awsProperties.get("aws.dynamo.db.pickCredentialsFromPropertiesFile");
        this.awsArn = (String) awsProperties.get("aws.arn");
        this.awsArnName = (String) awsProperties.get("aws.arn.name");
        this.dynamoDbEndpoint = (String) awsProperties.get("aws.dynamo.db.endpoint");
        this.dynamoDbRegion = (String) awsProperties.get("aws.dynamo.db.region");
    }
}
