package com.icc.qasker.auth.utils;

import com.icc.qasker.auth.entity.RefreshToken;
import com.icc.qasker.auth.repository.RefreshTokenRepository;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RefreshTokenGenerator {

    private final RefreshTokenRepository repo;
    private final StringRedisTemplate redis;


    // 발급
    @Transactional
    public String issue(String userId) {
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
    public record RotateResult(String userId, String newRtPlain) {

    }

    @Transactional
    public RotateResult validateAndRotate(String oldRtPlain) {
        String oldRtHash = TokenUtils.sha256Hex(oldRtPlain);

        RefreshToken current = repo.findById(oldRtHash)
            .orElseThrow(() -> new IllegalStateException("invalid/expired refresh token"));

        String userId = current.getUserId();

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
        String userId = cur.getUserId();
        repo.deleteById(rtHash);
        redis.opsForSet().remove(RtKeys.userSet(userId), rtHash);
    }

    // 로그아웃 (전체 Rt 폐기)
    @Transactional
    public void revokeAll(String userId) {
        String setKey = RtKeys.userSet(userId);
        Set<String> hashes = redis.opsForSet().members(setKey);
        if (hashes != null && !hashes.isEmpty()) {
            repo.deleteAllById(hashes); // hash 삭제
        }
        redis.delete(setKey);
    }
}
