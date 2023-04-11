package com.freecharge.smsprofilerservice.aws.config;

import com.freecharge.vault.PropertiesConfig;
import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.Map;

@Data
@Component
public class AmazonS3ResourceConfig {

    private String s3RuleRegion;
    private String s3ReportsRuleBucket;
    private Integer expirationTimeReportsBucket;
    private String s3DataRuleBucket;
    private Integer expirationTimeDataBucket;
    private String localTmpPathForUpload;
    private Integer downloadThreadCount;
    private Integer bucketPartitionNumber;

    @Autowired
    public AmazonS3ResourceConfig(@Qualifier("applicationProperties") PropertiesConfig propertiesConfig) {
        final Map<String, Object> awsProperties = propertiesConfig.getProperties();
        this.s3RuleRegion = (String) awsProperties.get("aws.rules.s3.region");
        this.s3ReportsRuleBucket = (String) awsProperties.get("aws.rules.s3.reports.bucket");
        this.expirationTimeReportsBucket = (Integer) awsProperties.get("aws.rule.s3.reports.expirationTime");
        this.s3DataRuleBucket = (String) awsProperties.get("aws.rules.s3.data.bucket");
        this.expirationTimeDataBucket = (Integer)  awsProperties.get("aws.rule.s3.data.expirationTime");
        this.localTmpPathForUpload = (String) awsProperties.get("aws.rule.s3.upload.tmp.folder");
        this.downloadThreadCount = (Integer) awsProperties.get("aws.s3.download.thread.count");
        this.bucketPartitionNumber = (Integer) awsProperties.get("aws.bucket.partition.number");
    }
}

