package com.zkuba.trading.dto.response;

public record PendingUserResponse(
        Long id,
        String username,
        String email
) {}
