package com.zkuba.trading.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record CreateAccountRequest(
        @NotNull Long userId, @Pattern(regexp = "[A-Z]{3}") String currency) {}
