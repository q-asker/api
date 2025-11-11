package com.icc.qasker.properties;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.data.redis.connection.RedisConfiguration;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;

@ConfigurationProperties(prefix = "spring.data.redis")
@Getter
@AllArgsConstructor
public class RedisProperties {

    private String host;
    private int port;
    private String password;
    private int database;

    public RedisConfiguration getRedisConfiguration() {

        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration();
        config.setHostName(host);
        config.setPort(port);
        config.setPassword(password);
        config.setDatabase(database);

        return config;
    }
}