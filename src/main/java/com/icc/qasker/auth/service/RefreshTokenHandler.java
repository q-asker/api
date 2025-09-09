package com.icc.qasker.auth.service;

import com.icc.qasker.auth.entity.RefreshToken;
import com.icc.qasker.auth.repository.RefreshTokenRepository;
import com.icc.qasker.auth.utils.RtKeys;
import com.icc.qasker.auth.utils.TokenUtils;
import com.icc.qasker.global.error.CustomException;
import com.icc.qasker.global.error.ExceptionMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RefreshTokenHandler {

    private final RefreshTokenRepository repo;
    private final StringRedisTemplate redis;

    @Transactional
    public String issue(String userId) {
        try {
            String rtPlain = TokenUtils.randomUrlSafe(64);
            String rtHash = TokenUtils.sha256Hex(rtPlain);

            repo.save(new RefreshToken(rtHash, userId));
            String setKey = RtKeys.userSet(userId);
            redis.opsForSet().add(setKey, rtHash);
            redis.expire(setKey, RtKeys.TTL);

            return rtPlain;
        } catch (Exception e) {
            throw new CustomException(ExceptionMessage.TOKEN_GENERATION_FAILED);
        }
    }

    public record RotateResult(String userId, String newRtPlain) {

    }

    @Transactional
    public RotateResult validateAndRotate(String oldRtPlain) {
        String oldRtHash = TokenUtils.sha256Hex(oldRtPlain);

        RefreshToken current = repo.findById(oldRtHash)
            .orElseThrow(() -> new CustomException(ExceptionMessage.UNAUTHORIZED));

        String userId = current.getUserId();
        repo.deleteById(oldRtHash);
        redis.opsForSet().remove(RtKeys.userSet(userId), oldRtHash);

        String newRtPlain = issue(userId);
        return new RotateResult(userId, newRtPlain);
    }

    @Transactional
    public void revoke(String presentedRtPlain) {
        String rtHash = TokenUtils.sha256Hex(presentedRtPlain);
        RefreshToken cur = repo.findById(rtHash).orElse(null);
        if (cur == null) {
            return;
        }
        String userId = cur.getUserId();
        repo.deleteById(rtHash);
        redis.opsForSet().remove(RtKeys.userSet(userId), rtHash);
    }

}
