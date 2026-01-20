package uk.ac.ebi.biostudies.index_service.config;

import com.amazonaws.ClientConfiguration;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3Client;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.PropertySource;
import org.springframework.stereotype.Component;

/**
 * Fire configuration loaded from classpath:fire.properties.
 *
 * <p>This is a facade that aggregates various fire-related configuration properties from the
 * fire.properties file.
 */
@Getter
@Component
@PropertySource("classpath:fire.properties")
public class FireConfig {

  private final String credentialsAccessKey;
  private final String credentialsSecretKey;
  private final String region;
  private final String s3Endpoint;
  private final String s3Bucket;

  private final Integer s3ConnectionPool;
  private final Integer s3ConnectionMagetabPool;
  private final Integer s3ConnectionTimeout;
  private final Integer s3ConnectionSocketTimeout;
  private final Boolean s3FtpRedirectEnabled;

  private final Boolean localIsActive;
  private final String localPath;

  public FireConfig(
      @Value("${fire.credentials.access-key:}") String credentialsAccessKey,
      @Value("${fire.credentials.secret-key:}") String credentialsSecretKey,
      @Value("${fire.region:}") String region,
      @Value("${fire.s3.endpoint:}") String s3Endpoint,
      @Value("${fire.s3.bucket:}") String s3Bucket,
      @Value("${fire.s3.connection.pool:400}") Integer s3ConnectionPool,
      @Value("${fire.s3.connection.magetab.pool:50}") Integer s3ConnectionMagetabPool,
      @Value("${fire.s3.connection.timeout:3000}") Integer s3ConnectionTimeout,
      @Value("${fire.s3.connection.socket.timeout:3000}") Integer s3ConnectionSocketTimeout,
      @Value("${fire.s3.ftp.redirect.enabled:true}") Boolean s3FtpRedirectEnabled,
      @Value("${fire.local.isactive:false}") Boolean localIsActive,
      @Value("${fire.local.path:}") String localPath) {
    this.credentialsAccessKey = credentialsAccessKey;
    this.credentialsSecretKey = credentialsSecretKey;
    this.region = region;
    this.s3Endpoint = s3Endpoint;
    this.s3Bucket = s3Bucket;
    this.s3ConnectionPool = s3ConnectionPool;
    this.s3ConnectionMagetabPool = s3ConnectionMagetabPool;
    this.s3ConnectionTimeout = s3ConnectionTimeout;
    this.s3ConnectionSocketTimeout = s3ConnectionSocketTimeout;
    this.s3FtpRedirectEnabled = s3FtpRedirectEnabled;
    this.localIsActive = localIsActive;
    this.localPath = localPath;
  }

  public boolean isLocalActive() {
    return Boolean.TRUE.equals(localIsActive);
  }

  public boolean isS3FtpRedirectEnabled() {
    return Boolean.TRUE.equals(s3FtpRedirectEnabled);
  }

  private AmazonS3 amazonS3Client(int poolSize) {
    BasicAWSCredentials basicAWSCredentials = new BasicAWSCredentials(credentialsAccessKey, credentialsSecretKey);
    AwsClientBuilder.EndpointConfiguration endpointConfiguration =
        new AwsClientBuilder.EndpointConfiguration(s3Endpoint, region); // The region is not important
    return AmazonS3Client.builder()
        .withClientConfiguration(
            new ClientConfiguration()
                .withMaxConnections(poolSize)
                .withConnectionTimeout(s3ConnectionTimeout)
                .withSocketTimeout(s3ConnectionSocketTimeout))
        .withEndpointConfiguration(endpointConfiguration)
        .withPathStyleAccessEnabled(true)
        .withCredentials(new AWSStaticCredentialsProvider(basicAWSCredentials))
        .build();
  }

  @Bean("S3DownloadClient")
  public AmazonS3 amazonS3DownloadClient() {
    return amazonS3Client(s3ConnectionPool);
  }

//  @Bean("S3MageTabClient")
//  public AmazonS3 amazonS3mergeTabClient() {
//    return amazonS3Client(s3ConnectionMagetabPool);
//  }
}
