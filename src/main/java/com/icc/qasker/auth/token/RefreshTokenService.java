package com.icc.qasker.auth.token;

import java.security.SecureRandom;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private final RefreshTokenRepository repo;
    private final StringRedisTemplate redis;
    private static final SecureRandom RAND = new SecureRandom();


    // 발급
    @Transactional
    public String issue(Long userId) {
        String rtPlain = TokenUtils.randomUrlSafe(64);
        String rtHash = TokenUtils.sha256Hex(rtPlain);

        // 1. Hash 저장
        repo.save(new RefreshToken(rtHash, userId));
        // 2. Set 저장
        String setKey = RtKeys.userSet(userId);
        redis.opsForSet().add(setKey, rtHash);
        redis.expire(setKey, RtKeys.TTL);

        return rtPlain; // 쿠키에는 평문으로 저장
    }

    // 조회 + 새 RT 발급
    public record RotateResult(Long userId, String newRtPlain) {

    }

    @Transactional
    public RotateResult validateAndRotate(String oldRtPlain) {
        String oldRtHash = TokenUtils.sha256Hex(oldRtPlain);

        RefreshToken current = repo.findById(oldRtHash)
            .orElseThrow(() -> new IllegalStateException("invalid/expired refresh token"));

        Long userId = current.getUserId();

        // 1. 이전 토큰 삭제 + set 삭제
        repo.deleteById(oldRtHash); // 이전 rt 삭제
        redis.opsForSet().remove(RtKeys.userSet(userId), oldRtHash); // 이전 userID set에도 삭제

        // 2. 새 토큰 발급
        String newRtPlain = issue(userId);
        return new RotateResult(userId, newRtPlain);
    }

    // 로그아웃 (해당 Rt 폐기)
    @Transactional
    public void revokeOne(String presentedRtPlain) {
        String rtHash = TokenUtils.sha256Hex(presentedRtPlain);
        RefreshToken cur = repo.findById(rtHash).orElse(null);
        if (cur == null) {
            return; // 이미 폐기됨
        }
        Long userId = cur.getUserId();
        repo.deleteById(rtHash);
        redis.opsForSet().remove(RtKeys.userSet(userId), rtHash);
    }

    // 로그아웃 (전체 Rt 폐기)
    @Transactional
    public void revokeAll(Long userId) {
        String setKey = RtKeys.userSet(userId);
        Set<String> hashes = redis.opsForSet().members(setKey);
        if (hashes != null && !hashes.isEmpty()) {
            repo.deleteAllById(hashes); // hash 삭제
        }
        redis.delete(setKey);
    }
}
