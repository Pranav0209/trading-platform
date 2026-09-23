package com.zkuba.trading.controller;

import com.zkuba.trading.dto.request.RegisterRequest;
import com.zkuba.trading.dto.response.RegisterResponse;
import com.zkuba.trading.service.UserService;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserControllerTest {

    @Mock
    private UserService userService;

    @InjectMocks
    private UserController userController;

    @Test
    void register_Success() {

        RegisterRequest request = new RegisterRequest(
                "john",
                "john@example.com",
                "Password123"
        );

        RegisterResponse expected = new RegisterResponse(
                1L,
                "john",
                "john@example.com",
                "USER",
                "ACTIVE"
        );

        when(userService.register(any(RegisterRequest.class)))
                .thenReturn(expected);

        RegisterResponse result =
                userController.register(request);

        assertThat(result).isNotNull();
        assertThat(result.userId()).isEqualTo(1L);
        assertThat(result.username()).isEqualTo("john");
        assertThat(result.email()).isEqualTo("john@example.com");
        assertThat(result.role()).isEqualTo("USER");
        assertThat(result.status()).isEqualTo("ACTIVE");

        verify(userService).register(request);
    }
}
