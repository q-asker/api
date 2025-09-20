package com.icc.qasker.auth.utils;

import com.icc.qasker.global.error.CustomException;
import com.icc.qasker.global.error.ExceptionMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class RefreshTokenHandler {

    private final StringRedisTemplate redis;
    private final RtKeys rtKeys;

    @Transactional
    public String issue(String userId) {
        try {
            String rtPlain = TokenUtils.randomUrlSafe(64);
            String rtHash = TokenUtils.sha256Hex(rtPlain);
            String setKey = rtKeys.userSet(userId);

            redis.opsForHash().put(rtHash, "userId", userId);
            redis.opsForSet().add(setKey, rtHash);
            redis.expire(rtHash, rtKeys.ttl());
            redis.expire(setKey, rtKeys.ttl());

            return rtPlain;
        } catch (Exception e) {
            System.out.println(e.getMessage());
            throw new CustomException(ExceptionMessage.TOKEN_GENERATION_FAILED);
        }
    }

    @Transactional
    public RotateResult validateAndRotate(String oldRtPlain) {
        String oldRtHash = TokenUtils.sha256Hex(oldRtPlain);

        String userId = (String) redis.opsForHash().get(oldRtHash, "userId");

        if (userId == null) {
            throw new CustomException(ExceptionMessage.UNAUTHORIZED);
        }
        redis.delete(oldRtHash);
        redis.opsForSet().remove(rtKeys.userSet(userId), oldRtHash);

        String newRtPlain = issue(userId);
        return new RotateResult(userId, newRtPlain);
    }

    @Transactional
    public void revoke(String presentedRtPlain) {
        String rtHash = TokenUtils.sha256Hex(presentedRtPlain);
        String userId = (String) redis.opsForHash().get(rtHash, "userId");
        if (userId == null) {
            return;
        }
        redis.delete(rtHash);
        redis.opsForSet().remove(rtKeys.userSet(userId), rtHash);
    }

    public record RotateResult(String userId, String newRtPlain) {

    }

}
