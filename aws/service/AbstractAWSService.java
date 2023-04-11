package com.freecharge.smsprofilerservice.aws.service;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public abstract class AbstractAWSService<T> {


    /*@Value("${AWS_WEB_IDENTITY_TOKEN_FILE}")
    private String tokenFilePath;

    protected abstract T geResourceClient() throws IOException;

    protected AwsCredentialsProvider getAwsCredentials(@NonNull final String awsArn,
                                                       @NonNull final String clientRegion, @NonNull final String awsArnName) throws IOException {
        final Path tokenFile = Paths.get(tokenFilePath);
        final WebIdentityTokenFileCredentialsProvider webIdentityTokenFileCredentialsProvider =
                WebIdentityTokenFileCredentialsProvider.builder()
                .roleArn(awsArn)
                .roleSessionName(awsArnName)
                .webIdentityTokenFile(tokenFile)
                .build();
        return webIdentityTokenFileCredentialsProvider;
    }*/
}