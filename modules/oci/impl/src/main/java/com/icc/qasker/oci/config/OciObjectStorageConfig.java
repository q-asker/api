package com.icc.qasker.oci.config;

import com.icc.qasker.oci.properties.OciObjectStorageProperties;
import com.oracle.bmc.ConfigFileReader;
import com.oracle.bmc.auth.ConfigFileAuthenticationDetailsProvider;
import com.oracle.bmc.objectstorage.ObjectStorageClient;
import com.oracle.bmc.objectstorage.transfer.UploadConfiguration;
import com.oracle.bmc.objectstorage.transfer.UploadManager;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@RequiredArgsConstructor
@Profile("!stress-test")
public class OciObjectStorageConfig {

  private final OciObjectStorageProperties properties;

  @Bean
  public ObjectStorageClient objectStorageClient() throws IOException {
    ConfigFileReader.ConfigFile configFile =
        ConfigFileReader.parse(properties.configFilePath(), properties.profile());

    ConfigFileAuthenticationDetailsProvider provider =
        new ConfigFileAuthenticationDetailsProvider(configFile);

    return ObjectStorageClient.builder()
        .region(com.oracle.bmc.Region.fromRegionCodeOrId(properties.region()))
        .build(provider);
  }

  @Bean
  public UploadManager uploadManager(ObjectStorageClient objectStorageClient) {
    UploadConfiguration uploadConfiguration =
        UploadConfiguration.builder()
            .allowMultipartUploads(true)
            .allowParallelUploads(true)
            .build();

    return new UploadManager(objectStorageClient, uploadConfiguration);
  }
}
