package com.zkuba.trading.dto.response;

public record LoginResponse(String accessToken, String tokenType, long expiresInMs) {}
