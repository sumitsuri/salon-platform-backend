package com.salonplatform.domain.repository;

import com.salonplatform.domain.entity.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {
    Optional<RefreshToken> findByTokenAndRevokedFalse(String token);

    List<RefreshToken> findByUserIdAndRevokedFalse(UUID userId);

    long countByUserId(UUID userId);

    /**
     * Single bulk DELETE rather than a derived delete: the derived form loads each row and removes it, so two
     * simultaneous logins for one account both try to remove the same rows and the loser fails with an
     * optimistic-locking error (HTTP 500). A bulk statement is safe to run twice.
     */
    @Modifying(flushAutomatically = true)
    @Query("delete from RefreshToken t where t.userId = :userId")
    void deleteByUserId(@Param("userId") UUID userId);

    @Transactional
    @Modifying(flushAutomatically = true)
    @Query("delete from RefreshToken t where t.token = :token")
    int deleteByTokenValue(@Param("token") String token);

    @Transactional
    @Modifying(flushAutomatically = true)
    @Query("delete from RefreshToken t where t.id = :id and t.userId = :userId")
    int deleteByIdAndUserId(@Param("id") UUID id, @Param("userId") UUID userId);

    @Transactional
    @Modifying(flushAutomatically = true)
    @Query("delete from RefreshToken t where t.userId = :userId and t.id <> :keepId")
    int deleteByUserIdExcept(@Param("userId") UUID userId, @Param("keepId") UUID keepId);

    @Transactional
    @Modifying(flushAutomatically = true)
    @Query("delete from RefreshToken t where t.id in :ids")
    int deleteByIds(@Param("ids") List<UUID> ids);

    @Transactional
    @Modifying
    @Query("update RefreshToken t set t.loginLocation = :location where t.id = :id")
    int setLoginLocation(@Param("id") UUID id, @Param("location") String location);

    @Transactional
    @Modifying
    @Query("update RefreshToken t set t.lastLocation = :location where t.id = :id")
    int setLastLocation(@Param("id") UUID id, @Param("location") String location);
}
