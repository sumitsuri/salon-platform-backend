package com.salonplatform.security;

import com.salonplatform.domain.entity.User;
import com.salonplatform.domain.enums.UserRole;
import com.salonplatform.domain.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomUserDetailsServiceTest {

    @Mock UserRepository userRepository;
    @InjectMocks CustomUserDetailsService service;

    private User user(String email) {
        return User.builder().id(UUID.randomUUID()).email(email).password("x").role(UserRole.SALON_MANAGER).active(true).build();
    }

    @Test
    void exactMatchWinsWithoutFallback() {
        when(userRepository.findByEmail("manager@salon.com")).thenReturn(Optional.of(user("manager@salon.com")));
        assertEquals("manager@salon.com", service.loadUserByUsername("manager@salon.com").getUsername());
        verify(userRepository, never()).findFirstByEmailIgnoreCaseOrderByCreatedAtAsc("manager@salon.com");
    }

    @Test
    void accountStoredWithCapitalsStillSignsInWithLowerCasedEmail() {
        when(userRepository.findByEmail("manager.gp@salon.com")).thenReturn(Optional.empty());
        when(userRepository.findFirstByEmailIgnoreCaseOrderByCreatedAtAsc("manager.gp@salon.com"))
                .thenReturn(Optional.of(user("Manager.GP@salon.com")));
        assertEquals("Manager.GP@salon.com", service.loadUserByUsername("manager.gp@salon.com").getUsername());
    }

    @Test
    void unknownEmailIsRejected() {
        when(userRepository.findByEmail("nobody@salon.com")).thenReturn(Optional.empty());
        when(userRepository.findFirstByEmailIgnoreCaseOrderByCreatedAtAsc("nobody@salon.com")).thenReturn(Optional.empty());
        assertThrows(UsernameNotFoundException.class, () -> service.loadUserByUsername("nobody@salon.com"));
    }
}
