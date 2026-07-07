package com.salonplatform.service;

import com.salonplatform.domain.entity.Branch;
import com.salonplatform.domain.entity.RefreshToken;
import com.salonplatform.domain.entity.Tenant;
import com.salonplatform.domain.entity.User;
import com.salonplatform.domain.repository.BranchRepository;
import com.salonplatform.domain.repository.RefreshTokenRepository;
import com.salonplatform.domain.repository.TenantRepository;
import com.salonplatform.domain.repository.UserRepository;
import com.salonplatform.dto.auth.AuthResponse;
import com.salonplatform.dto.auth.LoginRequest;
import com.salonplatform.exception.BadRequestException;
import com.salonplatform.exception.ResourceNotFoundException;
import com.salonplatform.security.JwtTokenProvider;
import com.salonplatform.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenRepository refreshTokenRepository;
    private final UserRepository userRepository;
    private final TenantRepository tenantRepository;
    private final BranchRepository branchRepository;

    @Transactional
    public AuthResponse login(LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword()));
        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        String accessToken = jwtTokenProvider.generateAccessToken(authentication);
        String refreshTokenValue = jwtTokenProvider.generateRefreshTokenValue();

        refreshTokenRepository.deleteByUserId(principal.getId());
        refreshTokenRepository.save(RefreshToken.builder()
                .userId(principal.getId())
                .token(refreshTokenValue)
                .expiresAt(Instant.now().plusMillis(jwtTokenProvider.getRefreshTokenExpiryMs()))
                .build());

        return buildAuthResponse(principal, accessToken, refreshTokenValue);
    }

    @Transactional
    public AuthResponse refresh(String refreshToken) {
        RefreshToken token = refreshTokenRepository.findByTokenAndRevokedFalse(refreshToken)
                .orElseThrow(() -> new BadRequestException("Invalid refresh token"));
        if (token.getExpiresAt().isBefore(Instant.now())) {
            throw new BadRequestException("Refresh token expired");
        }
        User user = userRepository.findById(token.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        UserPrincipal principal = new UserPrincipal(user);
        String accessToken = jwtTokenProvider.generateAccessToken(principal);
        return buildAuthResponse(principal, accessToken, refreshToken);
    }

    public AuthResponse me(UserPrincipal principal) {
        return buildAuthResponse(principal, null, null);
    }

    private AuthResponse buildAuthResponse(UserPrincipal principal, String accessToken, String refreshToken) {
        AuthResponse.AuthResponseBuilder builder = AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .userId(principal.getId())
                .name(principal.getName())
                .email(principal.getEmail())
                .role(principal.getRole())
                .tenantId(principal.getTenantId())
                .branchId(principal.getBranchId());

        if (principal.getTenantId() != null) {
            Tenant tenant = tenantRepository.findById(principal.getTenantId()).orElse(null);
            if (tenant != null) {
                builder.tenantName(tenant.getName())
                        .primaryColor(tenant.getPrimaryColor())
                        .logoUrl(tenant.getLogoUrl());
            }
        }
        if (principal.getBranchId() != null) {
            branchRepository.findById(principal.getBranchId())
                    .map(Branch::getName)
                    .ifPresent(builder::branchName);
        }
        return builder.build();
    }
}
