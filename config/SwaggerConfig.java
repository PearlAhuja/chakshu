package com.freecharge.smsprofilerservice.config;

import com.google.common.base.Predicate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import springfox.documentation.builders.ApiInfoBuilder;
import springfox.documentation.builders.PathSelectors;
import springfox.documentation.builders.RequestHandlerSelectors;
import springfox.documentation.service.ApiInfo;
import springfox.documentation.spi.DocumentationType;
import springfox.documentation.spring.web.plugins.Docket;
import springfox.documentation.swagger.web.*;
import springfox.documentation.swagger2.annotations.EnableSwagger2;

@Configuration
@EnableSwagger2
public class SwaggerConfig {


    @Value("${swagger.profiles.active}")
    private String profile;

    public Predicate<String> findPredicate() {
        Predicate<String> predicate = null;
        predicate = PathSelectors.any();
        return predicate;
    }

    @Bean
    public Docket api() {
        return new Docket(DocumentationType.SWAGGER_2)
                .apiInfo(apiInfo())
                .select()
                .apis(RequestHandlerSelectors.basePackage("com.freecharge.smsprofilerservice"))
                .paths(findPredicate())
                .build();

    }
    @Bean
    public UiConfiguration uiConfig() {
        return UiConfigurationBuilder.builder()
                .defaultModelRendering(ModelRendering.EXAMPLE)
                .displayRequestDuration(true)
                .docExpansion(DocExpansion.NONE)
                .operationsSorter(OperationsSorter.ALPHA)
                .tagsSorter(TagsSorter.ALPHA)
                .supportedSubmitMethods(UiConfiguration.Constants.DEFAULT_SUBMIT_METHODS)
                .validatorUrl(null).build();
    }

    public ApiInfo apiInfo() {
        return new ApiInfoBuilder().title("Sms Profiler Service")
                .license("1.0")
                .description("All api's for Sms Profiler Service")
                .build();
    }
}
