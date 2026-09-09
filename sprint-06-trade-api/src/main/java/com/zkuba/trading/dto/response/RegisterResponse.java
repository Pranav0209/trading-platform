package com.zkuba.trading.dto.response;

public record RegisterResponse(
        Long userId, String username, String email, String role, String status) {}
