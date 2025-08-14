package com.icc.qasker.auth.utils;

import java.time.Duration;

final class RtKeys {

    static String userSet(String userId) {
        return "rt:u:" + userId;
    }

    static final Duration TTL = Duration.ofSeconds(604800);
}