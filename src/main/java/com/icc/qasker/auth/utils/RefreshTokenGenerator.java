package com.icc.qasker.auth.utils;

import com.icc.qasker.auth.entity.RefreshToken;
import com.icc.qasker.auth.repository.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RefreshTokenGenerator {

    private final RefreshTokenRepository repo;
    private final StringRedisTemplate redis;

    @Transactional
    public String issue(String userId) {
        String rtPlain = TokenUtils.randomUrlSafe(64);
        String rtHash = TokenUtils.sha256Hex(rtPlain);

        repo.save(new RefreshToken(rtHash, userId));
        System.out.println("rtHash " + rtHash);
        System.out.println("userId " + userId);

        String setKey = RtKeys.userSet(userId);
        redis.opsForSet().add(setKey, rtHash);
        redis.expire(setKey, RtKeys.TTL);

        return rtPlain;
    }

    public record RotateResult(String userId, String newRtPlain) {

    }

    @Transactional
    public RotateResult validateAndRotate(String oldRtPlain) {
        String oldRtHash = TokenUtils.sha256Hex(oldRtPlain);

        RefreshToken current = repo.findById(oldRtHash)
            .orElseThrow(() -> new IllegalStateException("invalid/expired refresh token"));

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
