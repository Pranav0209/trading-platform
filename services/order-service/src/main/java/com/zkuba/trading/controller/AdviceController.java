package com.zkuba.trading.controller;

import com.zkuba.trading.dto.response.AdviceResponse;
import com.zkuba.trading.security.JwtAuthenticationFilter.AuthenticatedUser;
import com.zkuba.trading.service.AdviceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/advice")
@Tag(name = "Advice", description = "Trade advice and signals")
public class AdviceController {

    private final AdviceService adviceService;

    public AdviceController(AdviceService adviceService) {
        this.adviceService = adviceService;
    }

    @GetMapping("/{accountId}")
    @Operation(
            summary = "Get trade advice and signals",
            description = "Returns BUY/SELL/HOLD signals for every stock the user currently owns or has added to their watchlist.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Advice generated",
                            content = @Content(schema = @Schema(implementation = AdviceResponse.class))),
                    @ApiResponse(responseCode = "401", description = "Unauthorised"),
                    @ApiResponse(responseCode = "403", description = "Account not active"),
                    @ApiResponse(responseCode = "404", description = "Account not found")
            }
    )
    public ResponseEntity<AdviceResponse> getAdvice(
            @Parameter(description = "The numeric account key, ACCOUNTS.id.", required = true)
            @PathVariable Long accountId,
            @AuthenticationPrincipal AuthenticatedUser userDetails) {

        AdviceResponse response = adviceService.getAdvice(accountId, userDetails.userId());
        return ResponseEntity.ok(response);
    }
}
