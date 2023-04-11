package com.freecharge.smsprofilerservice.aws.accessor;

import com.axis.authorize.clients.config.HttpAuthClientConfig;
import com.fc.attribute.client.FCAttributeClientApi;
import com.fc.attribute.client.FCAttributeClientImpl;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;


@Configuration
public class ServerConfig
{
    @Value("${fc.attribute.client.id}")
    private String fcAttributeId;


    @Value("${fc.attribute.client.key}")
    private String fcAttributeKey;

    @Value("${fc.attribute.client.ip}")
    private String fcAttributeIp;


    @Bean
    public FCAttributeClientApi fcAttributeClientApi() {
        final HttpAuthClientConfig httpAuthClientConfig = HttpAuthClientConfig.builder()
                .host(fcAttributeIp)
                .clientId(fcAttributeId)
                .clientSecretKey(fcAttributeKey)
                .build();
        return new FCAttributeClientImpl(httpAuthClientConfig);
    }
}
