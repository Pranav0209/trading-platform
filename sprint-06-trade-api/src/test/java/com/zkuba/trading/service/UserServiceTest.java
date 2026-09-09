package com.zkuba.trading.service;

import com.zkuba.trading.domain.entity.User;
import com.zkuba.trading.domain.enums.UserRole;
import com.zkuba.trading.domain.enums.UserStatus;
import com.zkuba.trading.domain.exception.DuplicateUserException;
import com.zkuba.trading.domain.exception.UserNotFoundException;
import com.zkuba.trading.dto.request.RegisterRequest;
import com.zkuba.trading.dto.response.RegisterResponse;
import com.zkuba.trading.repository.UserRepository;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserService userService;

    private RegisterRequest registerRequest;
    private User user;

    @BeforeEach
    void setUp() {
        registerRequest = new RegisterRequest("trader1", "trader1@example.com", "securePass123");

        user = new User();
        user.setId(1L);
        user.setUsername("trader1");
        user.setEmail("trader1@example.com");
        user.setPasswordHash("encoded_securePass123");
        user.setRole(UserRole.ROLE_USER);
        user.setStatus(UserStatus.ACTIVE);
    }

    @Test
    @DisplayName("register should create and save user successfully when username and email are unique")
    void register_Success() {
        when(userRepository.findByUsername(registerRequest.username())).thenReturn(Optional.empty());
        when(userRepository.findByEmail(registerRequest.email())).thenReturn(Optional.empty());
        when(passwordEncoder.encode(registerRequest.password())).thenReturn("encoded_securePass123");

        RegisterResponse response = userService.register(registerRequest);

        assertThat(response).isNotNull();
        assertThat(response.username()).isEqualTo("trader1");
        assertThat(response.email()).isEqualTo("trader1@example.com");
        assertThat(response.role()).isEqualTo("ROLE_USER");
        assertThat(response.status()).isEqualTo("ACTIVE");

        verify(userRepository).save(any(User.class));
    }

    @Test
    @DisplayName("register should throw DuplicateUserException when username already exists")
    void register_DuplicateUsername_ThrowsException() {
        when(userRepository.findByUsername(registerRequest.username())).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> userService.register(registerRequest))
                .isInstanceOf(DuplicateUserException.class);

        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("register should throw DuplicateUserException when email already exists")
    void register_DuplicateEmail_ThrowsException() {
        when(userRepository.findByUsername(registerRequest.username())).thenReturn(Optional.empty());
        when(userRepository.findByEmail(registerRequest.email())).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> userService.register(registerRequest))
                .isInstanceOf(DuplicateUserException.class);

        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("get should return user when found by ID")
    void get_Success() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        User result = userService.get(1L);

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getUsername()).isEqualTo("trader1");
    }

    @Test
    @DisplayName("get should throw UserNotFoundException when user ID does not exist")
    void get_NotFound_ThrowsException() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.get(99L))
                .isInstanceOf(UserNotFoundException.class);
    }
}
