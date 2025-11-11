package com.icc.qasker.util;

import com.icc.qasker.error.CustomException;
import com.icc.qasker.error.ExceptionMessage;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hashids.Hashids;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@AllArgsConstructor
public class HashUtil {

    private final Hashids hashids;

    public String encode(long id) {
        return hashids.encode(id);
    }

    public long decode(String hashId) {
        long[] decoded = hashids.decode(hashId);
        if (decoded.length == 0) {
            throw new CustomException(ExceptionMessage.PROBLEM_SET_NOT_FOUND);
        }
        if (decoded.length > 1) {
            log.error("중복된 ID가 발견되었습니다: {}", hashId);
        }
        return decoded[decoded.length - 1];
    }
}
