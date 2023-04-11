package com.freecharge.smsprofilerservice.aws.accessor;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.freecharge.smsprofilerservice.aws.config.AmazonS3ResourceConfig;
import com.freecharge.smsprofilerservice.utils.HttpRequestUtils;
import lombok.NonNull;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.tomcat.util.security.MD5Encoder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import javax.annotation.PostConstruct;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPInputStream;

@Slf4j
@Service
public class S3Accessor {

    private final S3Client s3Client;

    private final AmazonS3ResourceConfig amazonS3ResourceConfig;

    private AmazonS3 amazonS3;

    @PostConstruct
    private AmazonS3 getS3Client() {
        /*String serviceEndpoint = String.format("http://%s:%s", "127.0.0.1", "4572");
        s3client = AmazonS3Client.builder()
                .withEndpointConfiguration(new AwsClientBuilder.EndpointConfiguration(serviceEndpoint, s3RuleRegion))
                .withCredentials(new AWSStaticCredentialsProvider(new BasicAWSCredentials("dummyKey", "dummySecret")))
                .build();*/

        amazonS3 = AmazonS3ClientBuilder
                    .standard()
                    .withRegion(amazonS3ResourceConfig.getS3RuleRegion())
                    .build();
        return amazonS3;
    }

    @Autowired
    public S3Accessor(@NonNull final S3Client s3Client,
                      @NonNull final AmazonS3ResourceConfig amazonS3ResourceConfig) {
        this.s3Client = s3Client;
        this.amazonS3ResourceConfig = amazonS3ResourceConfig;
    }

    public void saveData(@NonNull final String key, @NonNull final byte[] data) {
        log.info("Uploading file to s3 to path : {} ", (amazonS3ResourceConfig.getS3DataRuleBucket()+"/"+key));
        final PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .key(key)
                .bucket(amazonS3ResourceConfig.getS3DataRuleBucket())
                .contentEncoding(StandardCharsets.UTF_8.name())
                .contentLength(Long.valueOf(data.length))
                .contentType( "plain/text")
                .contentMD5(MD5Encoder.encode(data))
                .build();
        try {
            s3Client.putObject(putObjectRequest, RequestBody.fromBytes(data));
        } catch (Exception exception) {
            log.error("Error in File Upload to path {} : with exception {} ",(amazonS3ResourceConfig.getS3DataRuleBucket()+key),exception.getMessage());
            throw new InternalError(exception.getMessage().substring(0, Math.min(50, exception.getMessage().length())));
        }
    }

    @SneakyThrows
    public String getData(@NonNull String key, String imsId) {
        log.info("Reading file from s3 on path : {} ", (amazonS3ResourceConfig.getS3DataRuleBucket()+"/"+key));

        GetObjectRequest request = GetObjectRequest.builder()
                .bucket(amazonS3ResourceConfig.getS3DataRuleBucket())
                .key(key+imsId+".json.gz")
                .build();

        ResponseInputStream<GetObjectResponse> object = s3Client.getObject(request);
        return getSmsData(object);
    }

    private static String getSmsData(InputStream input) throws IOException {
        // Read the text input stream one line at a time and display each line.
        GZIPInputStream gzis = new GZIPInputStream(input);
        BufferedReader br = new BufferedReader(new InputStreamReader(gzis));
        String line;
        StringBuilder sb = new StringBuilder();

        while ((line = br.readLine()) != null) {
            sb.append(line);
        }

        br.close();
        gzis.close();
        input.close();

        String content = sb.toString();
        return content;
    }
}
