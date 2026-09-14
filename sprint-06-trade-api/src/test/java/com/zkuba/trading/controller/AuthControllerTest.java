package com.zkuba.trading.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zkuba.trading.dto.request.LoginRequest;
import com.zkuba.trading.dto.response.LoginResponse;
import com.zkuba.trading.security.JwtService;
import com.zkuba.trading.service.AuthService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AuthService authService;


    @MockBean
    private JwtService jwtService;


    @Test
    @DisplayName("POST /login should return 200 and login response")
    void login_shouldReturnLoginResponse() throws Exception {

        LoginRequest request =
                new LoginRequest("trader", "password123");

        LoginResponse response =
                new LoginResponse(
                        "mocked.jwt.token",
                        "Bearer",
                        86400000L
                );

        when(authService.login(any(LoginRequest.class)))
                .thenReturn(response);

        mockMvc.perform(
                        post("/api/v1/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken")
                        .value("mocked.jwt.token"))
                .andExpect(jsonPath("$.tokenType")
                        .value("Bearer"))
                .andExpect(jsonPath("$.expiresInMs")
                        .value(86400000L));
    }


    @Test
    @DisplayName("POST /login should return 422 when username is blank")
    void login_shouldRejectBlankUsername() throws Exception {

        LoginRequest request =
                new LoginRequest("", "password123");

        mockMvc.perform(
                        post("/api/v1/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request))
                )
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.status").value(422))
                .andExpect(jsonPath("$.error").value("VAL-422"))
                .andExpect(jsonPath("$.fieldErrors[0].field")
                        .value("username"));
    }


    @Test
    @DisplayName("POST /login should return 422 when password is blank")
    void login_shouldRejectBlankPassword() throws Exception {

        LoginRequest request =
                new LoginRequest("trader", "");

        mockMvc.perform(
                        post("/api/v1/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request))
                )
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.status").value(422))
                .andExpect(jsonPath("$.error").value("VAL-422"))
                .andExpect(jsonPath("$.fieldErrors[0].field")
                        .value("password"));
    }


    @Test
    @DisplayName("POST /login should return 422 when username and password are blank")
    void login_shouldRejectBlankUsernameAndPassword() throws Exception {

        LoginRequest request =
                new LoginRequest("", "");

        mockMvc.perform(
                        post("/api/v1/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request))
                )
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.status").value(422))
                .andExpect(jsonPath("$.error").value("VAL-422"))
                .andExpect(jsonPath("$.fieldErrors").isArray())
                .andExpect(jsonPath("$.fieldErrors.length()").value(2));
    }


    @Test
    @DisplayName("POST /login should return 422 when username is missing")
    void login_shouldRejectMissingUsername() throws Exception {

        String json = """
                {
                    "password": "password123"
                }
                """;

        mockMvc.perform(
                        post("/api/v1/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json)
                )
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.status").value(422))
                .andExpect(jsonPath("$.error").value("VAL-422"))
                .andExpect(jsonPath("$.fieldErrors[0].field")
                        .value("username"));
    }


    @Test
    @DisplayName("POST /login should return 422 when password is missing")
    void login_shouldRejectMissingPassword() throws Exception {

        String json = """
                {
                    "username": "trader"
                }
                """;

        mockMvc.perform(
                        post("/api/v1/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json)
                )
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.status").value(422))
                .andExpect(jsonPath("$.error").value("VAL-422"))
                .andExpect(jsonPath("$.fieldErrors[0].field")
                        .value("password"));
    }


    @Test
    @DisplayName("POST /login should return 500 for empty request body")
    void login_shouldRejectEmptyRequestBody() throws Exception {

        mockMvc.perform(
                        post("/api/v1/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("")
                )
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.error").value("SYS-500"));
    }


    @Test
    @DisplayName("POST /login should return 500 for invalid JSON")
    void login_shouldRejectInvalidJson() throws Exception {

        String invalidJson = """
                {
                    "username": "trader",
                    "password":
                }
                """;

        mockMvc.perform(
                        post("/api/v1/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(invalidJson)
                )
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.error").value("SYS-500"));
    }


    @Test
    @DisplayName("POST /login should return 500 when service throws exception")
    void login_shouldHandleServiceException() throws Exception {

        LoginRequest request =
                new LoginRequest("trader", "password123");

        when(authService.login(any(LoginRequest.class)))
                .thenThrow(new RuntimeException("Database down"));

        mockMvc.perform(
                        post("/api/v1/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request))
                )
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.error").value("SYS-500"))
                .andExpect(jsonPath("$.message")
                        .value("Internal server error. Please contact support if the problem persists."));
    }
}