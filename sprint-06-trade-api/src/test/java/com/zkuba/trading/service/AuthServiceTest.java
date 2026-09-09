package com.zkuba.trading.service;

import com.zkuba.trading.domain.entity.User;
import com.zkuba.trading.domain.enums.UserRole;
import com.zkuba.trading.domain.enums.UserStatus;
import com.zkuba.trading.exception.UnauthorizedException;
import com.zkuba.trading.dto.request.LoginRequest;
import com.zkuba.trading.dto.response.LoginResponse;
import com.zkuba.trading.repository.UserRepository;
import com.zkuba.trading.security.JwtService;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    @InjectMocks
    private AuthServiceImpl authService;

    private User activeUser;
    private LoginRequest validLoginRequest;

    @BeforeEach
    void setUp() {
        activeUser = new User();
        activeUser.setId(10L);
        activeUser.setUsername("trader");
        activeUser.setPasswordHash("hashed_password");
        activeUser.setRole(UserRole.ROLE_USER);
        activeUser.setStatus(UserStatus.ACTIVE);

        validLoginRequest = new LoginRequest("trader", "password123");
    }

    @Test
    @DisplayName("login should succeed and return JWT token when credentials are valid")
    void login_Success() {
        when(userRepository.findByUsername("trader")).thenReturn(Optional.of(activeUser));
        when(passwordEncoder.matches("password123", "hashed_password")).thenReturn(true);
        when(jwtService.generate(10L, "trader", "ROLE_USER")).thenReturn("mocked.jwt.token");
        when(jwtService.expirationMs()).thenReturn(86400000L);

        LoginResponse response = authService.login(validLoginRequest);

        assertThat(response).isNotNull();
        assertThat(response.accessToken()).isEqualTo("mocked.jwt.token");
        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.expiresInMs()).isEqualTo(86400000L);
    }

    @Test
    @DisplayName("login should throw UnauthorizedException when username is not found")
    void login_UserNotFound_ThrowsUnauthorized() {
        when(userRepository.findByUsername("trader")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(validLoginRequest))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    @DisplayName("login should throw UnauthorizedException when password does not match")
    void login_InvalidPassword_ThrowsUnauthorized() {
        when(userRepository.findByUsername("trader")).thenReturn(Optional.of(activeUser));
        when(passwordEncoder.matches("password123", "hashed_password")).thenReturn(false);

        assertThatThrownBy(() -> authService.login(validLoginRequest))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    @DisplayName("login should throw UnauthorizedException when user status is not ACTIVE")
    void login_InactiveUser_ThrowsUnauthorized() {
        activeUser.setStatus(UserStatus.SUSPENDED);
        when(userRepository.findByUsername("trader")).thenReturn(Optional.of(activeUser));
        when(passwordEncoder.matches("password123", "hashed_password")).thenReturn(true);

        assertThatThrownBy(() -> authService.login(validLoginRequest))
                .isInstanceOf(UnauthorizedException.class);
    }
}
