package com.icc.qasker.auth.repository;

import com.icc.qasker.auth.entity.RefreshToken;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, String> {

    /**
 * Finds a RefreshToken by its hashed token value.
 *
 * @param rtHash the hashed refresh token string to look up
 * @return an Optional containing the matching RefreshToken if present, otherwise an empty Optional
 */
Optional<RefreshToken> findByRtHash(String rtHash);
}