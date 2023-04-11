package com.freecharge.smsprofilerservice.aws.config;

import com.freecharge.smsprofilerservice.aws.service.AbstractAWSService;
import com.freecharge.vault.PropertiesConfig;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

import java.io.IOException;
import java.net.URI;
import java.util.Map;

@Slf4j
@Configuration
public class DynamodbConfig extends AbstractAWSService<DynamoDbClient> {

    final AmazonDynamoDBResourceConfig amazonDynamoDBResourceConfig;

    final BaseAwsResourceConfig baseAwsResourceConfig;

    final String dbTableName;

    @Value("${spring.profiles.active:Unknown}")
    private String activeProfile;


    @Autowired
    public DynamodbConfig(AmazonDynamoDBResourceConfig amazonDynamoDBResourceConfig, BaseAwsResourceConfig baseAwsResourceConfig,
                          @Qualifier("applicationProperties") PropertiesConfig applicationProperties) {
        final Map<String, Object> awsProperties = applicationProperties.getProperties();
        this.amazonDynamoDBResourceConfig = amazonDynamoDBResourceConfig;
        this.baseAwsResourceConfig = baseAwsResourceConfig;
        this.dbTableName = (String) awsProperties.get("aws.dynamo.db.tableName");
    }

    @Bean
    public DynamoDbEnhancedClient dynamoDbEnhancedClient(@Autowired @NonNull final DynamoDbClient dynamoDbClient) {
        return DynamoDbEnhancedClient.builder()
                .dynamoDbClient(dynamoDbClient)
                .build();
    }

    @Bean
    public DynamoDbClient geResourceClient() throws IOException {
        final URI uri = URI.create(amazonDynamoDBResourceConfig.getDynamoDbEndpoint());
        if (activeProfile.equalsIgnoreCase("dev")) {
            return DynamoDbClient.builder()
                //.region(Region.of(amazonDynamoDBResourceConfig.getDynamoDbRegion()))
                .endpointOverride(uri)
                .credentialsProvider(StaticCredentialsProvider.create(
                  AwsBasicCredentials.create("dummyKey", "dummySecret")))
                .build();
        }
        final DynamoDbClient dynamoDbClient = DynamoDbClient.builder()
                .endpointOverride(uri)
                .region(Region.of(amazonDynamoDBResourceConfig.getDynamoDbRegion()))
                /*.credentialsProvider(getAwsCredentials(amazonDynamoDBResourceConfig.getAwsArn(), amazonDynamoDBResourceConfig.getDynamoDbRegion(),
                        amazonDynamoDBResourceConfig.getAwsArnName()))*/
                .build();
        log.info("AwsV2ClientSms DynamoDbClient created");

        return dynamoDbClient;
    }
}
