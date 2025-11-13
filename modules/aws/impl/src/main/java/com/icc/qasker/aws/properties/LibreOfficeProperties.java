package com.icc.qasker.aws.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.office")
public record LibreOfficeProperties(
    String officeHome,
    int port
) {

};
