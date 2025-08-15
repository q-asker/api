package com.icc.qasker.auth.utils;

import java.time.Duration;

public class RtKeys {

    public static String userSet(String userId) {
        return "rt:u:" + userId;
    }

    public static final Duration TTL = Duration.ofSeconds(172800); // 2일
}