package com.salonplatform.domain.repository;

import com.salonplatform.domain.entity.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {
    Optional<RefreshToken> findByTokenAndRevokedFalse(String token);

    /**
     * Single bulk DELETE rather than a derived delete: the derived form loads each row and removes it, so two
     * simultaneous logins for one account both try to remove the same rows and the loser fails with an
     * optimistic-locking error (HTTP 500). A bulk statement is safe to run twice.
     */
    @Modifying(flushAutomatically = true)
    @Query("delete from RefreshToken t where t.userId = :userId")
    void deleteByUserId(@Param("userId") UUID userId);
}
