package com.salonplatform.service;

import com.salonplatform.config.LocaleProperties;
import com.salonplatform.domain.entity.User;
import com.salonplatform.domain.repository.UserRepository;
import com.salonplatform.dto.auth.AuthResponse;
import com.salonplatform.dto.user.UpdateLocaleRequest;
import com.salonplatform.exception.BadRequestException;
import com.salonplatform.exception.ResourceNotFoundException;
import com.salonplatform.security.SecurityUtils;
import com.salonplatform.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserProfileService {

    private final UserRepository userRepository;
    private final AuthService authService;
    private final LocaleProperties localeProperties;

    @Transactional
    public AuthResponse updateLocale(UpdateLocaleRequest request) {
        if (!localeProperties.isEnabled(request.getLocale())) {
            throw new BadRequestException("error.locale.unsupported");
        }
        UserPrincipal principal = SecurityUtils.currentUser();
        User user = userRepository.findById(principal.getId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        user.setPreferredLocale(request.getLocale());
        userRepository.save(user);
        return authService.me(new UserPrincipal(user));
    }
}
